package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import recording.Recorder;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ProcessGroupDetail {
  private final Recorder.ProcessGroup processGroup;
  private final int thresholdForDefunctRecorderInSecs;
  private final Map<RecorderIdentifier, RecorderDetail> recorderLookup = new HashMap<>();

  public ProcessGroupDetail(Recorder.ProcessGroup processGroup, int thresholdForDefunctRecorderInSecs) {
    this.processGroup = Preconditions.checkNotNull(processGroup);
    this.thresholdForDefunctRecorderInSecs = thresholdForDefunctRecorderInSecs;
  }

  public Recorder.ProcessGroup getProcessGroup() {
    return processGroup;
  }

  public void receivePoll(RecorderIdentifier recorderIdentifier, Recorder.WorkResponse lastIssuedWorkResponse) {
    RecorderDetail recorderDetail = this.recorderLookup.computeIfAbsent(recorderIdentifier, key -> {
      RecorderDetail value = new RecorderDetail(key, thresholdForDefunctRecorderInSecs);
      return value;
    });
    recorderDetail.receivePoll(lastIssuedWorkResponse);
  }

  public RecorderSupplier getRecorderSupplier(int coveragePct) {
    final List<RecorderDetail> availableRecordersAtStart = this.recorderLookup.values().stream()
        .filter(recorderDetail -> !recorderDetail.isDefunct())
        .collect(Collectors.toList());
    final int target = Math.round((coveragePct * availableRecordersAtStart.size()) / 100.0f);

    return new RecorderSupplier() {
      private final Set<RecorderDetail> supplied = new HashSet<>();
      private int poolIndex = 0;
      private List<RecorderDetail> recorderPool = availableRecordersAtStart;

      @Override
      public int getTargetRecordersCount() {
        return target;
      }

      @Override
      public RecorderDetail getNextRecorder() {
        if(supplied.size() == target) {
          return null;
        }

        RecorderDetail found = null;
        while(found == null && poolIndex < recorderPool.size()) {
          RecorderDetail recorderDetail = recorderPool.get(poolIndex++);
          if(!recorderDetail.isDefunct()) {
            found = recorderDetail;
          }
        }

        if(found == null) {
          recorderPool = recorderLookup.values().stream()
              .filter(recorderDetail -> !recorderDetail.isDefunct() && !supplied.contains(recorderDetail))
              .collect(Collectors.toList());
          poolIndex = 0;
          if(recorderPool.size() > 0) {
            return getNextRecorder();
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
