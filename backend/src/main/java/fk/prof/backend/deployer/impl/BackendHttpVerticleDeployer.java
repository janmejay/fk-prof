package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.http.BackendHttpVerticle;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.service.IProfileWorkService;
import io.vertx.core.*;

public class BackendHttpVerticleDeployer extends VerticleDeployer {

  private final LeaderReadContext leaderReadContext;
  private final IProfileWorkService profileWorkService;

  public BackendHttpVerticleDeployer(Vertx vertx,
                                     ConfigManager configManager,
                                     LeaderReadContext leaderReadContext,
                                     IProfileWorkService profileWorkService) {
    super(vertx, configManager);
    this.leaderReadContext = Preconditions.checkNotNull(leaderReadContext);
    this.profileWorkService = Preconditions.checkNotNull(profileWorkService);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getBackendHttpDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new BackendHttpVerticle(getConfigManager(), leaderReadContext, profileWorkService);
  }

}
