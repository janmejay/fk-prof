package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.leader.election.LeaderElectionParticipator;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import org.apache.curator.framework.CuratorFramework;

public class LeaderElectionParticipatorVerticleDeployer extends VerticleDeployer {

  private final CuratorFramework curatorClient;
  private final Runnable leaderElectedTask;

  public LeaderElectionParticipatorVerticleDeployer(Vertx vertx,
                                                    ConfigManager configManager,
                                                    CuratorFramework curatorClient,
                                                    Runnable leaderElectedTask) {
    super(vertx, configManager);
    this.curatorClient = Preconditions.checkNotNull(curatorClient);
    this.leaderElectedTask = Preconditions.checkNotNull(leaderElectedTask);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getLeaderElectionDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new LeaderElectionParticipator(getConfigManager(), curatorClient, leaderElectedTask);
  }
}
