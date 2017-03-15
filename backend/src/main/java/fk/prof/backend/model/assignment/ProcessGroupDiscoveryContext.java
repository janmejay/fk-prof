package fk.prof.backend.model.assignment;

import recording.Recorder;

public interface ProcessGroupDiscoveryContext {
  ProcessGroupContextForPolling getProcessGroupContextForPolling(Recorder.ProcessGroup processGroup);
}
