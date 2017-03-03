package fk.prof.backend.model.assignment;

import fk.prof.backend.proto.BackendDTO;
import recording.Recorder;

public interface SimultaneousWorkAssignmentCounter {
  int acquireSlots(Recorder.ProcessGroup processGroup, BackendDTO.WorkProfile workProfile);
  void releaseSlots(int slotsToRelease);
}
