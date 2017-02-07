package fk.prof.backend.verticles.leader.election;

public interface LeaderDiscoveryStore {
  /**
   * Accepts null argument as well, which is treated as removing leader mapping from the underlying store
   *
   * @param ipAddress
   */
  void setLeaderAddress(String ipAddress);

  /**
   * Returns null if leader has not been set in the store
   *
   * @return
   */
  String getLeaderAddress();

  /**
   * Returns true if self is leader, false otherwise
   *
   * @return
   */
  boolean isLeader();
}
