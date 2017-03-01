package fk.prof.backend.model.assignment;

import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.Future;
import recording.Recorder;

import java.util.*;
import java.util.function.Function;

public class WorkAssignmentManagerImpl implements WorkAssignmentManager {
  private final Map<Recorder.ProcessGroup, ProcessGroupDetail> processGroupLookup = new HashMap<>();
  private final int thresholdForDefunctRecorderInSecs;
  private Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor = null;

  public WorkAssignmentManagerImpl(int thresholdForDefunctRecorderInSecs) {
    this.thresholdForDefunctRecorderInSecs = thresholdForDefunctRecorderInSecs;
  }

  @Override
  public void initialize(Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor) {
    this.workForBackendRequestor = workForBackendRequestor;
  }

  @Override
  public Recorder.WorkAssignment receivePoll(Recorder.RecorderInfo recorderInfo, Recorder.WorkResponse lastIssuedWorkResponse) {
    Recorder.ProcessGroup processGroup = RecorderProtoUtil.mapRecorderInfoToProcessGroup(recorderInfo);
    ProcessGroupDetail processGroupDetail = this.processGroupLookup.get(processGroup);
    if(processGroupDetail == null) {
      throw new IllegalArgumentException("Process group " + ProtoUtil.processGroupCompactRepr(processGroup) + " not associated with the backend");
    }
    RecorderIdentifier recorderIdentifier = RecorderIdentifier.from(recorderInfo);
    processGroupDetail.receivePoll(recorderIdentifier, lastIssuedWorkResponse);
    //TODO: Generate work assignment
    return null;
  }

//  @Override
//  public ProcessGroupDetail updateAndGetProcessGroupDetail(Recorder.ProcessGroup assignedProcessGroup)

  @Override
  public void receiveProcessGroupAssignments(Recorder.ProcessGroups processGroups) {
    this.processGroupLookup.entrySet().removeIf(entry -> processGroups.getProcessGroupList().contains(entry));
    processGroups.getProcessGroupList().forEach(
        processGroup -> this.processGroupLookup.computeIfAbsent(processGroup, key -> {
          ProcessGroupDetail value = new ProcessGroupDetail(key, thresholdForDefunctRecorderInSecs);
          return value;
        }));
  }
}
