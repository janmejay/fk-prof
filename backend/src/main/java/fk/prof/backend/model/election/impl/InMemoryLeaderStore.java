package fk.prof.backend.model.election.impl;

import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.model.election.LeaderWriteContext;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.proto.BackendProtoUtil;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class InMemoryLeaderStore implements LeaderReadContext, LeaderWriteContext {
  private static Logger logger = LoggerFactory.getLogger(InMemoryLeaderStore.class);

  private final BackendDTO.LeaderDetail selfLeaderDetail;
  private volatile BackendDTO.LeaderDetail currentLeaderDetail = null;
  private volatile boolean leader = false;

  public InMemoryLeaderStore(String ipAddress, int leaderPort) {
    this.selfLeaderDetail = BackendDTO.LeaderDetail.newBuilder().setHost(ipAddress).setPort(leaderPort).build();
  }

  @Override
  public synchronized void setLeader(BackendDTO.LeaderDetail leaderDetail) {
    if (leaderDetail == null) {
      logger.info(String.format("Removed backend node as leader. Node IP = %s",
          this.currentLeaderDetail == null ? "" : BackendProtoUtil.leaderDetailCompactRepr(this.currentLeaderDetail)));
    } else {
      logger.info(String.format("Set backend leader. Node IP = %s", BackendProtoUtil.leaderDetailCompactRepr(this.currentLeaderDetail)));
    }
    this.currentLeaderDetail = leaderDetail;
    this.leader = this.currentLeaderDetail != null && this.currentLeaderDetail.equals(selfLeaderDetail);
  }

  @Override
  public BackendDTO.LeaderDetail getLeader() {
    return this.currentLeaderDetail;
  }

  @Override
  public boolean isLeader() {
    return this.leader;
  }
}
