package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.leader.election.LeaderElectionParticipator;
import fk.prof.backend.leader.election.LeaderElectionWatcher;
import fk.prof.backend.model.election.LeaderWriteContext;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import org.apache.curator.framework.CuratorFramework;

public class LeaderElectionWatcherVerticleDeployer extends VerticleDeployer {

  private final CuratorFramework curatorClient;
  private final LeaderWriteContext leaderWriteContext;

  public LeaderElectionWatcherVerticleDeployer(Vertx vertx,
                                               ConfigManager configManager,
                                               CuratorFramework curatorClient,
                                               LeaderWriteContext leaderWriteContext) {
    super(vertx, configManager);
    this.curatorClient = Preconditions.checkNotNull(curatorClient);
    this.leaderWriteContext = Preconditions.checkNotNull(leaderWriteContext);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getLeaderElectionDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new LeaderElectionWatcher(curatorClient, leaderWriteContext);
  }
}
