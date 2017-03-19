package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.aggregation.model.AggregationWindowStorage;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.model.aggregation.ActiveAggregationWindows;
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
  private final ActiveAggregationWindows activeAggregationWindows;
  private final WorkSlotPool workSlotPool;
  private final AggregationWindowStorage aggregationWindowStorage;

  public BackendDaemonVerticleDeployer(Vertx vertx,
                                       ConfigManager configManager,
                                       LeaderReadContext leaderReadContext,
                                       AssociatedProcessGroups associatedProcessGroups,
                                       ActiveAggregationWindows activeAggregationWindows,
                                       WorkSlotPool workSlotPool,
                                       AggregationWindowStorage aggregationWindowStorage) {
    super(vertx, configManager);
    this.leaderReadContext = Preconditions.checkNotNull(leaderReadContext);
    this.associatedProcessGroups = Preconditions.checkNotNull(associatedProcessGroups);
    this.activeAggregationWindows = Preconditions.checkNotNull(activeAggregationWindows);
    this.workSlotPool = Preconditions.checkNotNull(workSlotPool);
    this.aggregationWindowStorage = aggregationWindowStorage;
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
    return new BackendDaemon(getConfigManager(), leaderReadContext, associatedProcessGroups, activeAggregationWindows, workSlotPool, aggregationWindowStorage);
  }
}
