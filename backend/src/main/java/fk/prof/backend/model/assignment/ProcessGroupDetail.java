package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import recording.Recorder;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ProcessGroupDetail {
  private final Recorder.ProcessGroup processGroup;
  private final int thresholdForDefunctRecorderInSecs;
  private final Map<RecorderIdentifier, RecordingMachineDetail> recordingMachineLookup = new HashMap<>();

  public ProcessGroupDetail(Recorder.ProcessGroup processGroup, int thresholdForDefunctRecorderInSecs) {
    this.processGroup = Preconditions.checkNotNull(processGroup);
    this.thresholdForDefunctRecorderInSecs = thresholdForDefunctRecorderInSecs;
  }

  public Recorder.ProcessGroup getProcessGroup() {
    return processGroup;
  }

  public void receivePoll(RecorderIdentifier recorderIdentifier, Recorder.WorkResponse lastIssuedWorkResponse) {
    RecordingMachineDetail recordingMachineDetail = this.recordingMachineLookup.computeIfAbsent(recorderIdentifier, key -> {
      RecordingMachineDetail value = new RecordingMachineDetail(key, thresholdForDefunctRecorderInSecs);
      return value;
    });
    recordingMachineDetail.receivePoll(lastIssuedWorkResponse);
  }

  public Supplier<RecordingMachineDetail> getRecorderSupplier(int coveragePct) {
    final List<RecordingMachineDetail> availableRecordersAtStart = this.recordingMachineLookup.values().stream()
        .filter(recordingMachineDetail -> !recordingMachineDetail.isDefunct())
        .collect(Collectors.toList());
    final long target = Math.round((coveragePct * availableRecordersAtStart.size()) / 100.0f);

    return new Supplier<RecordingMachineDetail>() {
      private final Set<RecordingMachineDetail> supplied = new HashSet<>();
      private int poolIndex = 0;
      private List<RecordingMachineDetail> recorderPool = availableRecordersAtStart;

      @Override
      public RecordingMachineDetail get() {
        if(supplied.size() == target) {
          return null;
        }

        RecordingMachineDetail found = null;
        while(found == null && poolIndex < recorderPool.size()) {
          RecordingMachineDetail recordingMachineDetail = recorderPool.get(poolIndex++);
          if(!recordingMachineDetail.isDefunct()) {
            found = recordingMachineDetail;
          }
        }

        if(found == null) {
          recorderPool = recordingMachineLookup.values().stream()
              .filter(recordingMachineDetail -> !recordingMachineDetail.isDefunct() && !supplied.contains(recordingMachineDetail))
              .collect(Collectors.toList());
          poolIndex = 0;
          if(recorderPool.size() > 0) {
            return get();
          }
        }

        if(found != null) {
          supplied.add(found);
        }
        return found;
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessGroupDetail)) {
      return false;
    }

    ProcessGroupDetail other = (ProcessGroupDetail) o;
    return this.processGroup.equals(other.processGroup);
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + this.processGroup.hashCode();
    return result;
  }
}
