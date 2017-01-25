package fk.prof.backend.verticles.leader.election;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class LeaderSelectorListenerImpl extends LeaderSelectorListenerAdapter {
  private static Logger logger = LoggerFactory.getLogger(LeaderSelectorListenerImpl.class);

  private final int sleepDurationInMs;
  private final String leaderWatchingPath;
  private KillBehavior killBehavior;
  private final Runnable leaderElectedTask;

  public LeaderSelectorListenerImpl(int sleepDurationInMs, String leaderWatchingPath, KillBehavior killBehavior, Runnable leaderElectedTask) {
    if(leaderWatchingPath == null) {
      throw new IllegalArgumentException("Leader Watching path in zookeeper cannot be null");
    }
    if(killBehavior == null) {
      throw new IllegalArgumentException("Kill behavior cannot be null");
    }

    this.sleepDurationInMs = sleepDurationInMs;
    this.leaderWatchingPath = leaderWatchingPath;
    this.killBehavior = killBehavior;
    this.leaderElectedTask = leaderElectedTask;
  }

  @Override
  public void takeLeadership(CuratorFramework curatorClient) throws Exception {
    logger.info("Elected as leader");
    try {
      curatorClient
          .create()
          .creatingParentsIfNeeded()
          .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
          .forPath(leaderWatchingPath + "/child_", getIPAddress());

      if(leaderElectedTask != null) {
        leaderElectedTask.run();
      }

      while(true) {
        Thread.sleep(sleepDurationInMs);
      }
    } catch (InterruptedException ex) {
      logger.warn("Thread interrupted");
      Thread.currentThread().interrupt();
    } finally {
      cleanup();
    }
  }

  @Override
  public void stateChanged(CuratorFramework curatorClient, ConnectionState newState) {
    logger.debug("Connection state changed to {}", newState.toString());
    super.stateChanged(curatorClient, newState);
  }

  /**
   * Kill own process
   */
  private void cleanup() {
    try {
      killBehavior.process();
    } catch(Exception ex) {
      logger.error(ex);
    }
  }

  private static byte[] getIPAddress() {
    InetAddress ip;
    try {
      ip = InetAddress.getLocalHost();
      return ip.getAddress();
    } catch (UnknownHostException ex) {
      logger.error("Cannot determine ip address", ex);
      return null;
    }
  }

}
