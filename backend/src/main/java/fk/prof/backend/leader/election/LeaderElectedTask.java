package fk.prof.backend.leader.election;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * TODO: This class should be supplier[future<void]] instead of runnable
 * If run fails because of backend undeployment failure or leader deployment failure, failed future should be returned
 * Post that, leader should relinquish leadership
 */
public class LeaderElectedTask implements Runnable {
  private static Logger logger = LoggerFactory.getLogger(LeaderElectedTask.class);

  private MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(ConfigManager.METRIC_REGISTRY);
  private Counter ctrFailure = metricRegistry.counter(MetricRegistry.name(LeaderElectedTask.class, "fail"));

  private Supplier<CompositeFuture> backendVerticlesUndeployer = null;
  private VerticleDeployer leaderHttpVerticlesDeployer;

  private LeaderElectedTask(Vertx vertx,
                            VerticleDeployer leaderHttpVerticleDeployer,
                            List<String> backendDeployments) {

    if (backendDeployments != null) {
      // NOTE: If backend deployments supplied, leader will not serve as backend and only do leader related operations
      this.backendVerticlesUndeployer = () -> {
        logger.info("Unloading backend verticles");
        List<Future> undeployFutures =  new ArrayList<>();
        for(String deploymentId: backendDeployments) {
          Future<Void> future = Future.future();
          undeployFutures.add(future);

          vertx.undeploy(deploymentId, result -> {
            if (result.succeeded()) {
              logger.info(String.format("Successfully un-deployed backend verticle=%s", deploymentId));
              future.complete();
            } else {
              logger.error(String.format("Error when un-deploying backend verticle=%s", deploymentId), result.cause());
              future.fail(result.cause());
            }
          });
        }
        return CompositeFuture.all(undeployFutures);
      };
    }

    this.leaderHttpVerticlesDeployer = Preconditions.checkNotNull(leaderHttpVerticleDeployer);
  }

  @Override
  public void run() {
    logger.info("Beginning leader elected task");
    runTask().setHandler(ar -> {
      if(ar.failed()) {
        ctrFailure.inc();
      } else {
        logger.info("Successfully completed leader elected task");
      }
    });
  }

  private Future<Void> runTask() {
    if (this.backendVerticlesUndeployer != null) {
      Future<Void> result = Future.future();
      this.backendVerticlesUndeployer.get().setHandler(ar -> {
        if(ar.succeeded()) {
          deployLeaderHttpVerticles().setHandler(result.completer());
        } else {
          logger.error("Aborting deployment of leader http verticles because error while un-deploying backend verticles", ar.cause());
          result.fail(ar.cause());
        }
      });
      return result;
    } else {
      return deployLeaderHttpVerticles();
    }
  }

  private Future<Void> deployLeaderHttpVerticles() {
    Future<Void> future = Future.future();
    logger.info("Leader http verticle is being deployed");
    this.leaderHttpVerticlesDeployer.deploy().setHandler(ar -> {
      if(ar.failed()) {
        future.fail(ar.cause());
      } else {
        future.complete();
      }
    });
    return future;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private List<String> backendDeployments = null;

    public Builder disableBackend(List<String> backendDeployments) {
      this.backendDeployments = Preconditions.checkNotNull(backendDeployments);
      return this;
    }

    public LeaderElectedTask build(Vertx vertx, VerticleDeployer leaderHttpVerticleDeployer) {
      Preconditions.checkNotNull(vertx);
      Preconditions.checkNotNull(leaderHttpVerticleDeployer);
      return new LeaderElectedTask(vertx, leaderHttpVerticleDeployer, backendDeployments);
    }
  }
}
