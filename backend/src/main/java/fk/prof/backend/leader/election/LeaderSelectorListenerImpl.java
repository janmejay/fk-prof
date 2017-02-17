package fk.prof.backend.leader.election;

import fk.prof.backend.util.IPAddressUtil;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.CancelLeadershipException;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;

public class LeaderSelectorListenerImpl extends LeaderSelectorListenerAdapter {
  private static Logger logger = LoggerFactory.getLogger(LeaderSelectorListenerImpl.class);

  private final String leaderWatchingPath;
  private KillBehavior killBehavior;
  private final Runnable leaderElectedTask;

  public LeaderSelectorListenerImpl(String leaderWatchingPath, KillBehavior killBehavior, Runnable leaderElectedTask) {
    if (leaderWatchingPath == null) {
      throw new IllegalArgumentException("Leader Watching path in zookeeper cannot be null");
    }
    if (killBehavior == null) {
      throw new IllegalArgumentException("Kill behavior cannot be null");
    }

    this.leaderWatchingPath = leaderWatchingPath;
    this.killBehavior = killBehavior;
    this.leaderElectedTask = leaderElectedTask;
  }

  @Override
  public void takeLeadership(CuratorFramework curatorClient) throws Exception {
    logger.info("Elected as leader");
    curatorClient
        .create()
        .creatingParentsIfNeeded()
        .withMode(CreateMode.EPHEMERAL)
        .forPath(leaderWatchingPath + "/" + IPAddressUtil.getIPAddressAsString(), IPAddressUtil.getIPAddressAsBytes());

    // NOTE: There is a race here. Other backend nodes can be communicated about the new leader before leaderElectedTask has run
    // If backend nodes talk to the new leader before leader has been primed and setup, its possible for leader to not respond.
    // We are OK with this race issue as backend nodes can simply retry
    if (leaderElectedTask != null) {
      leaderElectedTask.run();
    }

    while (true) {
      try {
        Thread.sleep(Long.MAX_VALUE);
      } catch (InterruptedException ex) {
        logger.warn("Thread interrupted, sleeping again");
      }
    }
  }

  @Override
  public void stateChanged(CuratorFramework curatorClient, ConnectionState newState) {
    logger.debug("Connection state changed to {}", newState.toString());
    try {
      super.stateChanged(curatorClient, newState);
    } catch (CancelLeadershipException ex) {
      logger.debug("Relinquishing leadership, suicide!");
      cleanup();
    }
  }

  /**
   * Kill own process
   */
  private void cleanup() {
    try {
      killBehavior.process();
    } catch (Exception ex) {
      logger.error(ex);
    }
  }

}
