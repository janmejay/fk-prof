package fk.prof.backend.model.assignment;

import recording.Recorder;

public interface ProcessGroupContextForPolling {
  Recorder.ProcessGroup getProcessGroup();
  boolean receivePoll(Recorder.PollReq pollReq);
  Recorder.WorkAssignment getNextWorkAssignment();
}
