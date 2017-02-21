package fk.prof.backend.model.election;

public interface LeaderReadContext {
  /**
   * Returns null if leader has not been set in the store
   *
   * @return
   */
  String getLeaderIPAddress();

  /**
   * Returns true if self is leader, false otherwise
   *
   * @return
   */
  boolean isLeader();
}
