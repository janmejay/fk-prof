package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.http.BackendHttpVerticle;
import fk.prof.backend.model.assignment.ProcessGroupDiscoveryContext;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.model.aggregation.AggregationWindowDiscoveryContext;
import io.vertx.core.*;

public class BackendHttpVerticleDeployer extends VerticleDeployer {

  private final LeaderReadContext leaderReadContext;
  private final AggregationWindowDiscoveryContext aggregationWindowDiscoveryContext;
  private final ProcessGroupDiscoveryContext processGroupDiscoveryContext;

  public BackendHttpVerticleDeployer(Vertx vertx,
                                     ConfigManager configManager,
                                     LeaderReadContext leaderReadContext,
                                     AggregationWindowDiscoveryContext aggregationWindowDiscoveryContext,
                                     ProcessGroupDiscoveryContext processGroupDiscoveryContext) {
    super(vertx, configManager);
    this.leaderReadContext = Preconditions.checkNotNull(leaderReadContext);
    this.aggregationWindowDiscoveryContext = Preconditions.checkNotNull(aggregationWindowDiscoveryContext);
    this.processGroupDiscoveryContext = Preconditions.checkNotNull(processGroupDiscoveryContext);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getBackendHttpDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new BackendHttpVerticle(getConfigManager(), leaderReadContext, aggregationWindowDiscoveryContext, processGroupDiscoveryContext);
  }

}
