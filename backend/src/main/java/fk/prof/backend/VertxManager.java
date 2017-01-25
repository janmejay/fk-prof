package fk.prof.backend;

import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.verticles.http.AggregatorHttpVerticle;
import fk.prof.backend.service.ProfileWorkService;
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
      if(closeResult.succeeded()) {
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
      DeploymentOptions aggregatorDeploymentOptions,
      IProfileWorkService profileWorkService) {
    assert vertx != null;
    assert aggregatorDeploymentOptions != null;
    assert profileWorkService != null;

    int verticleCount = aggregatorDeploymentOptions.getConfig().getInteger("http.instances", 1);
    List<Future> deployFutures = new ArrayList<>();
    for(int i = 1;i <= verticleCount;i++) {
      Future<String> deployFuture = Future.future();
      deployFutures.add(deployFuture);

      Verticle httpVerticle = new AggregatorHttpVerticle(profileWorkService);
      vertx.deployVerticle(httpVerticle, aggregatorDeploymentOptions, deployResult -> {
        if(deployResult.succeeded()) {
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

  public static void deployLeaderElectionWorkerVerticles
      (Vertx vertx,
       DeploymentOptions leaderDeploymentOptions,
       CuratorFramework curatorClient,
       Runnable leaderElectedTask,
       LeaderDiscoveryStore leaderDiscoveryStore) {
    assert vertx != null;
    assert leaderDeploymentOptions != null;
    assert curatorClient != null;
    assert leaderElectedTask != null;
    assert leaderDiscoveryStore != null;

    Verticle leaderElectionParticipator = new LeaderElectionParticipator(curatorClient, leaderElectedTask);
    vertx.deployVerticle(leaderElectionParticipator, leaderDeploymentOptions);

    Verticle leaderElectionWatcher = new LeaderElectionWatcher(
        curatorClient,
        leaderDiscoveryStore);
    vertx.deployVerticle(leaderElectionWatcher, leaderDeploymentOptions);
  }

  public static Runnable getDefaultLeaderElectedTask(Vertx vertx, boolean aggregationEnabled, List<String> aggregatorDeployments) {
    LeaderElectedTask.Builder builder = new LeaderElectedTask.Builder();
    if(!aggregationEnabled) {
      builder.disableAggregation(aggregatorDeployments);
    }
    return builder.build(vertx);
  }

  public static LeaderDiscoveryStore getDefaultLeaderDiscoveryStore(Vertx vertx) {
    return new SharedMapBasedLeaderDiscoveryStore(vertx);
  }

  public static Future<Void> launch(Vertx vertx, CuratorFramework curatorClient, JsonObject configOptions) {
    JsonObject aggregatorDeploymentConfig = ConfigManager.getAggregatorDeploymentConfig(configOptions);
    if(aggregatorDeploymentConfig == null) {
      throw new RuntimeException("Aggregator deployment options are required to be present");
    }

    JsonObject leaderDeploymentConfig = ConfigManager.getLeaderDeploymentConfig(configOptions);
    if(leaderDeploymentConfig == null) {
      throw new RuntimeException("Leader deployment options are required to be present");
    }

    Future future = Future.future();
    DeploymentOptions aggregatorDeploymentOptions = new DeploymentOptions(aggregatorDeploymentConfig);
    ProfileWorkService profileWorkService = new ProfileWorkService();

    CompositeFuture aggregatorDeploymentFuture = deployAggregatorHttpVerticles(vertx, aggregatorDeploymentOptions, profileWorkService);
    aggregatorDeploymentFuture.setHandler(result -> {
      if(result.succeeded()) {
        //Deploy leader related verticles
        List<String> aggregatorDeployments = result.result().list();
        DeploymentOptions leaderDeploymentOptions = new DeploymentOptions(leaderDeploymentConfig);
        Runnable leaderElectedTask = getDefaultLeaderElectedTask(
            vertx,
            leaderDeploymentOptions.getConfig().getBoolean("aggregation.enabled", true),
            aggregatorDeployments);
        LeaderDiscoveryStore leaderDiscoveryStore = getDefaultLeaderDiscoveryStore(vertx);
        deployLeaderElectionWorkerVerticles(vertx, leaderDeploymentOptions, curatorClient, leaderElectedTask, leaderDiscoveryStore);

      } else {
        future.fail(result.cause());
      }
    });

    return future;
  }

}
