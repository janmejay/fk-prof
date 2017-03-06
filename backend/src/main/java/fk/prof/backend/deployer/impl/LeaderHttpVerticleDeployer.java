package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.http.LeaderHttpVerticle;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.policy.PolicyStore;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class LeaderHttpVerticleDeployer extends VerticleDeployer {

  private final BackendAssociationStore backendAssociationStore;
  private final PolicyStore policyStore;

  public LeaderHttpVerticleDeployer(Vertx vertx,
                                    ConfigManager configManager,
                                    BackendAssociationStore backendAssociationStore,
                                    PolicyStore policyStore) {
    super(vertx, configManager);
    this.backendAssociationStore = Preconditions.checkNotNull(backendAssociationStore);
    this.policyStore = Preconditions.checkNotNull(policyStore);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getLeaderHttpDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new LeaderHttpVerticle(getConfigManager(), backendAssociationStore, policyStore);
  }
}
