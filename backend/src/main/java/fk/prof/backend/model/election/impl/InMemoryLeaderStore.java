package fk.prof.backend.model.election.impl;

import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.model.election.LeaderWriteContext;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class InMemoryLeaderStore implements LeaderReadContext, LeaderWriteContext {
  private static Logger logger = LoggerFactory.getLogger(InMemoryLeaderStore.class);

  private final String ipAddress;
  private volatile String leaderIPAddress = null;
  private volatile boolean leader = false;

  public InMemoryLeaderStore(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  @Override
  public synchronized void setLeaderIPAddress(String ipAddress) {
    if (ipAddress == null) {
      logger.info(String.format("Removed backend node as leader. Node IP = %s",
          this.leaderIPAddress == null ? "" : this.leaderIPAddress));
    } else {
      logger.info(String.format("Set backend leader. Node IP = %s", ipAddress));
    }
    this.leaderIPAddress = ipAddress;
    this.leader = this.leaderIPAddress != null && this.leaderIPAddress.equals(ipAddress);
  }

  @Override
  public String getLeaderIPAddress() {
    return this.leaderIPAddress;
  }

  @Override
  public boolean isLeader() {
    return this.leader;
  }
}
