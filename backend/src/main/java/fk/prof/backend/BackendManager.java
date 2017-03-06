package fk.prof.backend;

import com.google.common.base.Preconditions;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.*;
import fk.prof.backend.leader.election.LeaderElectedTask;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;
import fk.prof.backend.model.assignment.ProcessGroupAssociationStore;
import fk.prof.backend.model.assignment.SimultaneousWorkAssignmentCounter;
import fk.prof.backend.model.assignment.impl.ProcessGroupAssociationStoreImpl;
import fk.prof.backend.model.assignment.impl.SimultaneousWorkAssignmentCounterImpl;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ProcessGroupCountBasedBackendComparator;
import fk.prof.backend.model.association.impl.ZookeeperBasedBackendAssociationStore;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;
import fk.prof.backend.model.aggregation.impl.AggregationWindowLookupStoreImpl;
import fk.prof.backend.model.policy.PolicyStore;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * TODO: Deployment process is liable to changes later
 */
public class BackendManager {
  private static Logger logger = LoggerFactory.getLogger(BackendManager.class);

  private final Vertx vertx;
  private final ConfigManager configManager;
  private final CuratorFramework curatorClient;

  public BackendManager(String configFilePath) throws Exception {
    this(new ConfigManager(configFilePath));
  }

  public BackendManager(ConfigManager configManager) throws Exception {
    ConfigManager.setDefaultSystemProperties();
    this.configManager = Preconditions.checkNotNull(configManager);

    VertxOptions vertxOptions = new VertxOptions(configManager.getVertxConfig());
    vertxOptions.setMetricsOptions(new DropwizardMetricsOptions()
        .setEnabled(true)
        .setJmxEnabled(true)
        .setRegistryName(ConfigManager.METRIC_REGISTRY)
        .addMonitoredHttpServerUri(new Match().setValue("/.*").setType(MatchType.REGEX)));
    this.vertx = Vertx.vertx(vertxOptions);

    this.curatorClient = createCuratorClient();
    curatorClient.start();
    curatorClient.blockUntilConnected(configManager.getCuratorConfig().getInteger("connection.timeout.ms", 10000), TimeUnit.MILLISECONDS);
  }

  public Future<Void> close() {
    Future future = Future.future();
    vertx.close(closeResult -> {
      if (closeResult.succeeded()) {
        logger.info("Shutdown successful for vertx instance");
        curatorClient.close();
        future.complete();
      } else {
        logger.error("Error shutting down vertx instance");
        future.fail(closeResult.cause());
      }
    });

    return future;
  }

  public Future<Void> launch() {
    Future result = Future.future();
    InMemoryLeaderStore leaderStore = new InMemoryLeaderStore(configManager.getIPAddress());
    AggregationWindowLookupStore aggregationWindowLookupStore = new AggregationWindowLookupStoreImpl();
    ProcessGroupAssociationStore processGroupAssociationStore = new ProcessGroupAssociationStoreImpl(configManager.getRecorderDefunctThresholdInSeconds());
    SimultaneousWorkAssignmentCounter simultaneousWorkAssignmentCounter = new SimultaneousWorkAssignmentCounterImpl(configManager.getMaxSimultaneousProfiles());

    VerticleDeployer backendHttpVerticleDeployer = new BackendHttpVerticleDeployer(vertx, configManager, leaderStore, aggregationWindowLookupStore, processGroupAssociationStore);
    VerticleDeployer backendDaemonVerticleDeployer = new BackendDaemonVerticleDeployer(vertx, configManager, leaderStore, processGroupAssociationStore, aggregationWindowLookupStore, simultaneousWorkAssignmentCounter);
    CompositeFuture backendDeploymentFuture = CompositeFuture.all(backendHttpVerticleDeployer.deploy(), backendDaemonVerticleDeployer.deploy());
    backendDeploymentFuture.setHandler(backendDeployResult -> {
      if (backendDeployResult.succeeded()) {
        try {
          List<String> backendDeployments = backendDeployResult.result().list().stream()
              .flatMap(fut -> ((CompositeFuture)fut).list().stream())
              .map(deployment -> (String)deployment)
              .collect(Collectors.toList());

          BackendAssociationStore backendAssociationStore = createBackendAssociationStore(vertx, curatorClient);
          PolicyStore policyStore = new PolicyStore();
          VerticleDeployer leaderHttpVerticleDeployer = new LeaderHttpVerticleDeployer(vertx, configManager, backendAssociationStore, policyStore);
          Runnable leaderElectedTask = createLeaderElectedTask(vertx, leaderHttpVerticleDeployer, backendDeployments);

          VerticleDeployer leaderElectionParticipatorVerticleDeployer = new LeaderElectionParticipatorVerticleDeployer(
              vertx, configManager, curatorClient, leaderElectedTask);
          VerticleDeployer leaderElectionWatcherVerticleDeployer = new LeaderElectionWatcherVerticleDeployer(vertx, configManager, curatorClient, leaderStore);

          CompositeFuture leaderDeployFuture = CompositeFuture.all(
              leaderElectionParticipatorVerticleDeployer.deploy(), leaderElectionWatcherVerticleDeployer.deploy());
          leaderDeployFuture.setHandler(leaderDeployResult -> {
            if(leaderDeployResult.succeeded()) {
              result.complete();
            } else {
              result.fail(leaderDeployResult.cause());
            }
          });
        } catch (Exception ex) {
          result.fail(ex);
        }
      } else {
        result.fail(backendDeployResult.cause());
      }
    });

    return result;
  }


  private CuratorFramework createCuratorClient() {
    JsonObject curatorConfig = configManager.getCuratorConfig();
    return CuratorFrameworkFactory.builder()
        .connectString(curatorConfig.getString("connection.url"))
        .retryPolicy(new ExponentialBackoffRetry(1000, curatorConfig.getInteger("max.retries", 3)))
        .connectionTimeoutMs(curatorConfig.getInteger("connection.timeout.ms", 10000))
        .sessionTimeoutMs(curatorConfig.getInteger("session.timeout.ms", 60000))
        .namespace(curatorConfig.getString("namespace", "fkprof"))
        .build();
  }

  private BackendAssociationStore createBackendAssociationStore(
      Vertx vertx, CuratorFramework curatorClient)
      throws Exception {
    int loadReportIntervalInSeconds = configManager.getLoadReportIntervalInSeconds();
    JsonObject leaderHttpDeploymentConfig = configManager.getLeaderHttpDeploymentConfig();
    String backendAssociationPath = leaderHttpDeploymentConfig.getString("backend.association.path", "/association");
    int loadMissTolerance = leaderHttpDeploymentConfig.getInteger("load.miss.tolerance", 2);
    return new ZookeeperBasedBackendAssociationStore(vertx, curatorClient, backendAssociationPath,
        loadReportIntervalInSeconds, loadMissTolerance, configManager.getBackendHttpPort(), new ProcessGroupCountBasedBackendComparator());
  }

  private Runnable createLeaderElectedTask(Vertx vertx, VerticleDeployer leaderHttpVerticleDeployer, List<String> backendDeployments) {
    LeaderElectedTask.Builder builder = LeaderElectedTask.newBuilder();
    builder.disableBackend(backendDeployments);
    return builder.build(vertx, leaderHttpVerticleDeployer);
  }
}
