package fk.prof.backend.verticles.leader.election;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

/**
 * TODO: This class is partially complete and will undergo refactoring once all leader functions are implemented
 */
public class LeaderElectedTask implements Runnable {
  private static Logger logger = LoggerFactory.getLogger(LeaderElectedTask.class);

  private Vertx vertx;
  private Runnable aggregatorVerticlesUndeployer = null;

  private LeaderElectedTask(Vertx vertx, List<String> aggregatorDeployments) {
    this.vertx = vertx;

    if(aggregatorDeployments != null) {
      // NOTE: If aggregator deployments supplied, leader will not serve as aggregator and only do leader related operations
      this.aggregatorVerticlesUndeployer = () -> aggregatorDeployments.stream().forEach(deploymentId ->
          vertx.undeploy(deploymentId, result -> {
            if (result.succeeded()) {
              logger.debug(String.format("Successfully undeployed aggregator verticle=%s", deploymentId));
            } else {
              logger.error(String.format("Error when undeploying aggregator verticle=%s", deploymentId), result.cause());
            }
          })
      );
    }
  }

  @Override
  public void run() {
    if(this.aggregatorVerticlesUndeployer != null) {
      this.aggregatorVerticlesUndeployer.run();
    }
  }

  public static class Builder {
    private List<String> aggregatorDeployments = null;

    public Builder disableAggregation(List<String> aggregatorDeployments) {
      if(aggregatorDeployments == null) {
        throw new IllegalArgumentException("Aggregator deployments are required to disable aggregation");
      }
      this.aggregatorDeployments = aggregatorDeployments;
      return this;
    }

    public LeaderElectedTask build(Vertx vertx) {
      return new LeaderElectedTask(vertx, aggregatorDeployments);
    }
  }
}
