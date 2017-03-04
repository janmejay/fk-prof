package fk.prof.backend.deployer.impl;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;
import fk.prof.backend.model.assignment.ProcessGroupAssociationStore;
import fk.prof.backend.model.assignment.SimultaneousWorkAssignmentCounter;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.worker.BackendDaemon;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class BackendDaemonVerticleDeployer extends VerticleDeployer {

  private LeaderReadContext leaderReadContext;
  private ProcessGroupAssociationStore processGroupAssociationStore;
  private AggregationWindowLookupStore aggregationWindowLookupStore;
  private SimultaneousWorkAssignmentCounter simultaneousWorkAssignmentCounter;

  public BackendDaemonVerticleDeployer(Vertx vertx,
                                       ConfigManager configManager,
                                       LeaderReadContext leaderReadContext,
                                       ProcessGroupAssociationStore processGroupAssociationStore,
                                       AggregationWindowLookupStore aggregationWindowLookupStore,
                                       SimultaneousWorkAssignmentCounter simultaneousWorkAssignmentCounter) {
    super(vertx, configManager);
    this.leaderReadContext = leaderReadContext;
    this.processGroupAssociationStore = processGroupAssociationStore;
    this.aggregationWindowLookupStore = aggregationWindowLookupStore;
    this.simultaneousWorkAssignmentCounter = simultaneousWorkAssignmentCounter;
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getBackendDaemonDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new BackendDaemon(getConfigManager(), leaderReadContext, processGroupAssociationStore, aggregationWindowLookupStore, simultaneousWorkAssignmentCounter);
  }
}
