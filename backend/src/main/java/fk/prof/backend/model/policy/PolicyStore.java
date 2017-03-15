package fk.prof.backend.model.policy;

import fk.prof.backend.proto.BackendDTO;
import recording.Recorder;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO: Liable for refactoring. Dummy impl for now
 */
public class PolicyStore {
  private final Map<Recorder.ProcessGroup, BackendDTO.RecordingPolicy> store = new HashMap<>();

  public void put(Recorder.ProcessGroup processGroup, BackendDTO.RecordingPolicy recordingPolicy) {
    this.store.put(processGroup, recordingPolicy);
  }

  public BackendDTO.RecordingPolicy get(Recorder.ProcessGroup processGroup) {
    return this.store.get(processGroup);
  }
}
