package fk.prof.backend.leader.election;

import com.google.common.base.Preconditions;
import fk.prof.backend.deployer.VerticleDeployer;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

/**
 * TODO: This class is partially complete and will undergo refactoring once all leader functions are implemented
 */
public class LeaderElectedTask implements Runnable {
  private static Logger logger = LoggerFactory.getLogger(LeaderElectedTask.class);

  private Runnable backendVerticlesUndeployer = null;
  private VerticleDeployer leaderHttpVerticlesDeployer;

  private LeaderElectedTask(Vertx vertx,
                            VerticleDeployer leaderHttpVerticleDeployer,
                            List<String> backendDeployments) {

    if (backendDeployments != null) {
      // NOTE: If backend deployments supplied, leader will not serve as backend and only do leader related operations
      this.backendVerticlesUndeployer = () -> backendDeployments.stream().forEach(deploymentId ->
          vertx.undeploy(deploymentId, result -> {
            if (result.succeeded()) {
              if(logger.isDebugEnabled()) {
                logger.debug(String.format("Successfully un-deployed backend verticle=%s", deploymentId));
              }
            } else {
              logger.error(String.format("Error when un-deploying backend verticle=%s", deploymentId), result.cause());
            }
          })
      );
    }

    this.leaderHttpVerticlesDeployer = Preconditions.checkNotNull(leaderHttpVerticleDeployer);
  }

  @Override
  public void run() {
    if (this.backendVerticlesUndeployer != null) {
      this.backendVerticlesUndeployer.run();
    }
    this.leaderHttpVerticlesDeployer.deploy();
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
