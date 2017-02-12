package fk.prof.backend;

import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ProcessGroupCountBasedBackendComparator;
import fk.prof.backend.model.association.impl.ZookeeperBasedBackendAssociationStore;
import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.service.ProfileWorkService;
import fk.prof.backend.verticles.http.AggregatorHttpVerticle;
import fk.prof.backend.verticles.http.LeaderHttpVerticle;
import fk.prof.backend.verticles.http.LeaderProxyVerticle;
import fk.prof.backend.verticles.leader.election.*;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;
import org.apache.curator.framework.CuratorFramework;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO: Deployment process is liable to changes later
 */
public class VertxManager {
  private static Logger logger = LoggerFactory.getLogger(VertxManager.class);

  public static Vertx initialize(JsonObject vertxConfig) throws IOException {
    VertxOptions vertxOptions = vertxConfig != null
        ? new VertxOptions(vertxConfig)
        : new VertxOptions();

    vertxOptions.setMetricsOptions(new DropwizardMetricsOptions()
        .setEnabled(true)
        .setJmxEnabled(true)
        .setRegistryName(ConfigManager.METRIC_REGISTRY)
        .addMonitoredHttpServerUri(new Match().setValue("/.*").setType(MatchType.REGEX)));
    return Vertx.vertx(vertxOptions);
  }

  public static Future<Void> close(Vertx vertx) {
    Future future = Future.future();
    vertx.close(closeResult -> {
      if (closeResult.succeeded()) {
        logger.info("Shutdown successful for vertx instance");
        future.complete();
      } else {
        logger.error("Error shutting down vertx instance");
        future.fail(closeResult.cause());
      }
    });

    return future;
  }

  public static CompositeFuture deployAggregatorHttpVerticles(
      Vertx vertx,
      JsonObject httpServerOptions,
      DeploymentOptions aggregatorDeploymentOptions,
      IProfileWorkService profileWorkService) {
    assert vertx != null;
    assert aggregatorDeploymentOptions != null;
    assert profileWorkService != null;

    int verticleCount = aggregatorDeploymentOptions.getConfig().getInteger("http.instances", 1);
    List<Future> deployFutures = new ArrayList<>();
    for (int i = 1; i <= verticleCount; i++) {
      Future<String> deployFuture = Future.future();
      deployFutures.add(deployFuture);

      Verticle aggregatorHttpVerticle = new AggregatorHttpVerticle(httpServerOptions, profileWorkService);
      vertx.deployVerticle(aggregatorHttpVerticle, aggregatorDeploymentOptions, deployResult -> {
        if (deployResult.succeeded()) {
          logger.info("Deployment of AggregatorHttpVerticle succeeded with deploymentId = " + deployResult.result());
          deployFuture.complete(deployResult.result());
        } else {
          logger.error("Deployment of AggregatorHttpVerticle failed", deployResult.cause());
          deployFuture.fail(deployResult.cause());
        }
      });
    }

    return CompositeFuture.all(deployFutures);
  }

  public static CompositeFuture deployLeaderProxyVerticles
      (Vertx vertx,
       JsonObject httpServerConfig,
       JsonObject httpClientConfig,
       DeploymentOptions leaderProxyDeploymentOptions,
       LeaderDiscoveryStore leaderDiscoveryStore) {
    assert vertx != null;
    assert leaderProxyDeploymentOptions != null;
    assert leaderDiscoveryStore != null;

    int verticleCount = leaderProxyDeploymentOptions.getConfig().getInteger("http.instances", 1);
    List<Future> deployFutures = new ArrayList<>();
    for (int i = 1; i <= verticleCount; i++) {
      Future<String> deployFuture = Future.future();
      deployFutures.add(deployFuture);

      Verticle leaderProxyVerticle = new LeaderProxyVerticle(httpServerConfig, httpClientConfig, leaderDiscoveryStore);
      vertx.deployVerticle(leaderProxyVerticle, leaderProxyDeploymentOptions, deployResult -> {
        if (deployResult.succeeded()) {
          logger.info("Deployment of LeaderProxyVerticle succeeded with deploymentId = " + deployResult.result());
          deployFuture.complete(deployResult.result());
        } else {
          logger.error("Deployment of LeaderProxyVerticle failed", deployResult.cause());
          deployFuture.fail(deployResult.cause());
        }
      });
    }

    return CompositeFuture.all(deployFutures);
  }

  public static void deployLeaderElectionWorkerVerticles
      (Vertx vertx,
       DeploymentOptions leaderElectionDeploymentOptions,
       CuratorFramework curatorClient,
       Runnable leaderElectedTask,
       LeaderDiscoveryStore leaderDiscoveryStore) {
    assert vertx != null;
    assert leaderElectionDeploymentOptions != null;
    assert curatorClient != null;
    assert leaderElectedTask != null;
    assert leaderDiscoveryStore != null;

    Verticle leaderElectionParticipator = new LeaderElectionParticipator(curatorClient, leaderElectedTask);
    vertx.deployVerticle(leaderElectionParticipator, leaderElectionDeploymentOptions);

    Verticle leaderElectionWatcher = new LeaderElectionWatcher(
        curatorClient,
        leaderDiscoveryStore);
    vertx.deployVerticle(leaderElectionWatcher, leaderElectionDeploymentOptions);
  }

  public static void deployLeaderHttpVerticles
      (Vertx vertx,
       JsonObject httpServerConfig,
       DeploymentOptions leaderHttpDeploymentOptions,
       BackendAssociationStore backendAssociationStore) {
    assert vertx != null;
    assert leaderHttpDeploymentOptions != null;
    assert backendAssociationStore != null;

    Verticle leaderHttpVerticle = new LeaderHttpVerticle(httpServerConfig, backendAssociationStore);
    vertx.deployVerticle(leaderHttpVerticle, leaderHttpDeploymentOptions);
  }

  public static Runnable getDefaultLeaderElectedTask(Vertx vertx,
                                                     boolean aggregationEnabled,
                                                     List<String> backendDeployments,
                                                     boolean leaderHttpEnabled,
                                                     JsonObject httpServerConfig,
                                                     DeploymentOptions leaderHttpDeploymentOptions,
                                                     BackendAssociationStore backendAssociationStore) {
    LeaderElectedTask.Builder builder = LeaderElectedTask.newBuilder();
    if (!aggregationEnabled) {
      builder.disableBackend(backendDeployments);
    }
    if(leaderHttpEnabled) {
      builder.enableLeaderHttp(httpServerConfig, leaderHttpDeploymentOptions, backendAssociationStore);
    }
    return builder.build(vertx);
  }

  public static LeaderDiscoveryStore getDefaultLeaderDiscoveryStore(Vertx vertx) {
    return new SharedMapBasedLeaderDiscoveryStore(vertx);
  }

  public static BackendAssociationStore getDefaultBackendAssociationStore(Vertx vertx, CuratorFramework curatorClient, String backendAssociationZKNodePath, int reportLoadFrequencyInSeconds, int maxAllowedSkips)
      throws Exception {
    return ZookeeperBasedBackendAssociationStore.newBuilder()
        .setCuratorClient(curatorClient)
        .setBackendAssociationPath(backendAssociationZKNodePath)
        .setBackedPriorityComparator(new ProcessGroupCountBasedBackendComparator())
        .setReportingFrequencyInSeconds(reportLoadFrequencyInSeconds)
        .setMaxAllowedSkips(maxAllowedSkips)
        .build(vertx);
  }

  public static Future<Void> launch(Vertx vertx, CuratorFramework curatorClient, JsonObject configOptions) {
    JsonObject httpServerConfig = ConfigManager.getHttpServerConfig(configOptions);
    if (httpServerConfig == null) {
      throw new RuntimeException("Http server options are required");
    }

    JsonObject aggregatorDeploymentConfig = ConfigManager.getAggregatorDeploymentConfig(configOptions);
    if (aggregatorDeploymentConfig == null) {
      throw new RuntimeException("Aggregator deployment options are required");
    }

    JsonObject leaderProxyDeploymentConfig = ConfigManager.getLeaderProxyDeploymentConfig(configOptions);
    if (leaderProxyDeploymentConfig == null) {
      throw new RuntimeException("Leader proxy deployment options are required");
    }

    JsonObject httpClientConfig = ConfigManager.getHttpClientConfig(configOptions);
    if (httpClientConfig == null) {
      throw new RuntimeException("Http client options are required");
    }

    JsonObject leaderElectionDeploymentConfig = ConfigManager.getLeaderElectionDeploymentConfig(configOptions);
    if (leaderElectionDeploymentConfig == null) {
      throw new RuntimeException("Leader election deployment options are required");
    }

    JsonObject leaderHttpDeploymentConfig = ConfigManager.getLeaderHttpDeploymentConfig(configOptions);
    if (leaderHttpDeploymentConfig == null) {
      throw new RuntimeException("Leader http deployment options are required");
    }

    Future future = Future.future();
    DeploymentOptions aggregatorDeploymentOptions = new DeploymentOptions(aggregatorDeploymentConfig);
    DeploymentOptions leaderProxyDeploymentOptions = new DeploymentOptions(leaderProxyDeploymentConfig);
    ProfileWorkService profileWorkService = new ProfileWorkService();
    LeaderDiscoveryStore leaderDiscoveryStore = getDefaultLeaderDiscoveryStore(vertx);

    CompositeFuture aggregatorDeploymentFuture = deployAggregatorHttpVerticles(vertx, httpServerConfig, aggregatorDeploymentOptions, profileWorkService);
    CompositeFuture leaderProxyDeploymentFuture = deployLeaderProxyVerticles(vertx, httpServerConfig, httpClientConfig, leaderProxyDeploymentOptions, leaderDiscoveryStore);
    CompositeFuture backendDeployFuture = CompositeFuture.all(aggregatorDeploymentFuture, leaderProxyDeploymentFuture);

    backendDeployFuture.setHandler(result -> {
      if (result.succeeded()) {
        //Deploy leader related verticles
        List<String> backendDeployments = result.result().list().stream()
            .flatMap(deployments -> ((List<String>)deployments).stream())
            .collect(Collectors.toList());
        DeploymentOptions leaderElectionDeploymentOptions = new DeploymentOptions(leaderElectionDeploymentConfig);
        DeploymentOptions leaderHttpDeploymentOptions = new DeploymentOptions(leaderHttpDeploymentConfig);

        try {
          BackendAssociationStore backendAssociationStore = getDefaultBackendAssociationStore(
              vertx, curatorClient,
              leaderHttpDeploymentConfig.getString("backend.association.path"),
              ConfigManager.getLoadReportIntervalInSeconds(configOptions),
              leaderHttpDeploymentConfig.getInteger("allowed.report.skips"));

          Runnable leaderElectedTask = getDefaultLeaderElectedTask(
              vertx,
              leaderElectionDeploymentOptions.getConfig().getBoolean("aggregation.enabled", true),
              backendDeployments,
              leaderElectionDeploymentOptions.getConfig().getBoolean("http.enabled", true),
              httpServerConfig,
              leaderHttpDeploymentOptions,
              backendAssociationStore
          );

          deployLeaderElectionWorkerVerticles(vertx, leaderElectionDeploymentOptions, curatorClient, leaderElectedTask, leaderDiscoveryStore);
        } catch (Exception ex) {
          future.fail(ex);
        }
      } else {
        future.fail(result.cause());
      }
    });

    return future;
  }

}
