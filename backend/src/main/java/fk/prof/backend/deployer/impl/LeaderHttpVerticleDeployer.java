package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.http.LeaderHttpVerticle;
import fk.prof.backend.model.association.BackendAssociationStore;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class LeaderHttpVerticleDeployer extends VerticleDeployer {

  private final BackendAssociationStore backendAssociationStore;

  public LeaderHttpVerticleDeployer(Vertx vertx,
                                    ConfigManager configManager,
                                    BackendAssociationStore backendAssociationStore) {
    super(vertx, configManager);
    this.backendAssociationStore = Preconditions.checkNotNull(backendAssociationStore);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getLeaderHttpDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new LeaderHttpVerticle(getConfigManager(), backendAssociationStore);
  }
}
