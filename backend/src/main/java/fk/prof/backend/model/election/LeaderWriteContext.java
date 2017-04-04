package fk.prof.backend.model.election;

import fk.prof.backend.proto.BackendDTO;

public interface LeaderWriteContext {
  /**
   * Accepts null argument as well, which is treated as removing leader mapping from the underlying store
   *
   * @param leader
   */
  void setLeader(BackendDTO.LeaderDetail leader);
}
