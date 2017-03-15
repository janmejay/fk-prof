package fk.prof.backend.model.assignment;

import recording.Recorder;

public interface ProcessGroupContextForPolling {
  Recorder.ProcessGroup getProcessGroup();
  Recorder.WorkAssignment getWorkAssignment(Recorder.PollReq pollReq);
}
