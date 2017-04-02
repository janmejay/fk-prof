package fk.prof.userapi.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.userapi.UserapiConfigManager;
import fk.prof.userapi.api.ProfileStoreAPI;
import fk.prof.userapi.deployer.VerticleDeployer;
import fk.prof.userapi.verticles.HttpVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class UserapiHttpVerticleDeployer extends VerticleDeployer {

  private final ProfileStoreAPI profileStoreAPI;

  public UserapiHttpVerticleDeployer(Vertx vertx, UserapiConfigManager userapiConfigManager, ProfileStoreAPI profileStoreAPI) {
    super(vertx, userapiConfigManager);
    this.profileStoreAPI = Preconditions.checkNotNull(profileStoreAPI);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getUserapiHttpDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new HttpVerticle(getConfigManager(), profileStoreAPI);
  }

}
