package fk.prof.backend.model.election;

public interface LeaderWriteContext {
  /**
   * Accepts null argument as well, which is treated as removing leader mapping from the underlying store
   *
   * @param ipAddress
   */
  void setLeaderIPAddress(String ipAddress);
}
