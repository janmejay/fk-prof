package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;
import fk.prof.backend.model.assignment.AssociatedProcessGroups;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.model.slot.WorkSlotPool;
import fk.prof.backend.worker.BackendDaemon;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class BackendDaemonVerticleDeployer extends VerticleDeployer {

  private final LeaderReadContext leaderReadContext;
  private final AssociatedProcessGroups associatedProcessGroups;
  private final AggregationWindowLookupStore aggregationWindowLookupStore;
  private final WorkSlotPool workSlotPool;

  public BackendDaemonVerticleDeployer(Vertx vertx,
                                       ConfigManager configManager,
                                       LeaderReadContext leaderReadContext,
                                       AssociatedProcessGroups associatedProcessGroups,
                                       AggregationWindowLookupStore aggregationWindowLookupStore,
                                       WorkSlotPool workSlotPool) {
    super(vertx, configManager);
    this.leaderReadContext = Preconditions.checkNotNull(leaderReadContext);
    this.associatedProcessGroups = Preconditions.checkNotNull(associatedProcessGroups);
    this.aggregationWindowLookupStore = Preconditions.checkNotNull(aggregationWindowLookupStore);
    this.workSlotPool = Preconditions.checkNotNull(workSlotPool);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    DeploymentOptions deploymentOptions = new DeploymentOptions(getConfigManager().getBackendDaemonDeploymentConfig());
    //Backend daemon should never be deployed more than once, so hardcoding verticle count to 1, to protect from illegal configuration
    deploymentOptions.getConfig().put("verticle.count", 1);
    return deploymentOptions;
  }

  @Override
  protected Verticle buildVerticle() {
    return new BackendDaemon(getConfigManager(), leaderReadContext, associatedProcessGroups, aggregationWindowLookupStore, workSlotPool);
  }
}
