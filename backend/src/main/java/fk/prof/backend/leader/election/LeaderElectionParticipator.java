package fk.prof.backend.leader.election;

import fk.prof.backend.ConfigManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;

public class LeaderElectionParticipator extends AbstractVerticle {
  private static Logger logger = LoggerFactory.getLogger(LeaderElectionParticipator.class);
  private final CuratorFramework curatorClient;
  private final Runnable leaderElectedTask;
  private final ConfigManager configManager;

  private LeaderSelector leaderSelector;

  public LeaderElectionParticipator(ConfigManager configManager, CuratorFramework curatorClient, Runnable leaderElectedTask) {
    this.curatorClient = curatorClient;
    this.leaderElectedTask = leaderElectedTask;
    this.configManager = configManager;
  }

  @Override
  public void start() {
    LeaderSelectorListener leaderSelectorListener = createLeaderSelectorListener();
    leaderSelector = createLeaderSelector(curatorClient, leaderSelectorListener);
    if (logger.isDebugEnabled()) {
      logger.debug("Starting leader selector");
    }
    leaderSelector.start();
  }

  @Override
  public void stop() {
    if(logger.isDebugEnabled()) {
      logger.debug("Closing leader selector");
    }
    leaderSelector.close();
  }

  private LeaderSelectorListener createLeaderSelectorListener() {
    return new LeaderSelectorListenerImpl(
        configManager.getIPAddress(),
        config().getString("leader.watching.path", "/backends"),
        KillBehavior.valueOf(config().getString("kill.behavior", "DO_NOTHING")),
        leaderElectedTask);
  }

  private LeaderSelector createLeaderSelector(CuratorFramework curatorClient, LeaderSelectorListener leaderSelectorListener) {
    return new LeaderSelector(curatorClient, config().getString("leader.mutex.path"), leaderSelectorListener);
  }

}
