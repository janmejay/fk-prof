package fk.prof.backend.model.assignment;

import fk.prof.backend.http.ProfHttpClient;
import fk.prof.backend.proto.BackendDTO;
import io.vertx.core.Future;
import recording.Recorder;

import java.util.function.Function;

public interface WorkAssignmentManager {
  void initialize(Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor);
  Recorder.WorkAssignment receivePoll(Recorder.RecorderInfo recorderInfo, Recorder.WorkResponse lastIssuedWorkResponse);
//  ProcessGroupDetail updateAssignmentAndGetProcessGroupDetail(Recorder.ProcessGroup assignedProcessGroup);

  void receiveProcessGroupAssignments(Recorder.ProcessGroups processGroups);
}
