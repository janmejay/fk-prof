package fk.prof.backend.model.policy;

import fk.prof.backend.proto.BackendDTO;
import recording.Recorder;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO: Liable for refactoring. Dummy impl for now
 */
public class PolicyStore {
  private final Map<Recorder.ProcessGroup, BackendDTO.WorkProfile> store = new HashMap<>();

  public void put(Recorder.ProcessGroup processGroup, BackendDTO.WorkProfile workProfile) {
    this.store.put(processGroup, workProfile);
  }

  public BackendDTO.WorkProfile get(Recorder.ProcessGroup processGroup) {
    return this.store.get(processGroup);
  }
}
