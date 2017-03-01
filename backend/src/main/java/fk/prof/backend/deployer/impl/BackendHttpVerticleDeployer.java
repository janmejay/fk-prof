package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.http.BackendHttpVerticle;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.service.AggregationWindowReadContext;
import io.vertx.core.*;

public class BackendHttpVerticleDeployer extends VerticleDeployer {

  private final LeaderReadContext leaderReadContext;
  private final AggregationWindowReadContext aggregationWindowReadContext;

  public BackendHttpVerticleDeployer(Vertx vertx,
                                     ConfigManager configManager,
                                     LeaderReadContext leaderReadContext,
                                     AggregationWindowReadContext aggregationWindowReadContext) {
    super(vertx, configManager);
    this.leaderReadContext = Preconditions.checkNotNull(leaderReadContext);
    this.aggregationWindowReadContext = Preconditions.checkNotNull(aggregationWindowReadContext);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getBackendHttpDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new BackendHttpVerticle(getConfigManager(), leaderReadContext, aggregationWindowReadContext);
  }

}
