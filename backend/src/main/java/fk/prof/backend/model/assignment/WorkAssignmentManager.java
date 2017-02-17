package fk.prof.backend.model.assignment;

import recording.Recorder;

public interface WorkAssignmentManager {
  Recorder.WorkAssignment receivePoll(Recorder.RecorderInfo recorderInfo, Recorder.WorkResponse lastIssuedWorkResponse);
  void startPollWindow();
}
