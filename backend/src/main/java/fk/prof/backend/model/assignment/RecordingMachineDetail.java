package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import recording.Recorder;

public class RecordingMachineDetail {
  private static final double NANOSECONDS_IN_SECOND = Math.pow(10, 9);

  private final RecorderIdentifier recorderIdentifier;
  private final long thresholdForDefunctRecorderInNanos;
  private Long lastReportedTime = null;

  private long currentWorkId = 0;
  private Recorder.WorkResponse.WorkState currentWorkState;

  public RecordingMachineDetail(RecorderIdentifier recorderIdentifier, int thresholdForDefunctRecorderInSecs) {
    this.recorderIdentifier = Preconditions.checkNotNull(recorderIdentifier);
    this.thresholdForDefunctRecorderInNanos = (long)(thresholdForDefunctRecorderInSecs * NANOSECONDS_IN_SECOND);
  }

  public void receivePoll(Recorder.WorkResponse lastIssuedWorkResponse) {
    this.currentWorkId = lastIssuedWorkResponse.getWorkId();
    this.currentWorkState = lastIssuedWorkResponse.getWorkState();
    this.lastReportedTime = System.nanoTime();
  }

  public boolean isDefunct() {
    return lastReportedTime == null ||
        ((System.nanoTime() - lastReportedTime) > thresholdForDefunctRecorderInNanos);
  }

  public boolean canAcceptWork() {
    if (!isDefunct() &&
        (this.currentWorkId == 0 || Recorder.WorkResponse.WorkState.complete.equals(currentWorkState))) {
      return true;
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof RecordingMachineDetail)) {
      return false;
    }

    RecordingMachineDetail other = (RecordingMachineDetail) o;
    return this.recorderIdentifier.equals(other.recorderIdentifier);
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + this.recorderIdentifier.hashCode();
    return result;
  }
}