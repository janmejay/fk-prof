package fk.prof.backend.model.assignment.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.model.assignment.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ProcessGroupDetail implements ProcessGroupContextForScheduling, ProcessGroupContextForPolling {
  private static final Logger logger = LoggerFactory.getLogger(ProcessGroupDetail.class);

  private final Recorder.ProcessGroup processGroup;
  private final int thresholdForDefunctRecorderInSecs;
  private final Map<RecorderIdentifier, RecorderDetail> recorderLookup = new ConcurrentHashMap<>();
  private volatile WorkAssignmentSchedule workAssignmentSchedule = null;

  public ProcessGroupDetail(Recorder.ProcessGroup processGroup, int thresholdForDefunctRecorderInSecs) {
    this.processGroup = Preconditions.checkNotNull(processGroup);
    this.thresholdForDefunctRecorderInSecs = thresholdForDefunctRecorderInSecs;
  }

  @Override
  public Recorder.ProcessGroup getProcessGroup() {
    return processGroup;
  }

  @Override
  public boolean receivePoll(Recorder.PollReq pollReq) {
    RecorderIdentifier recorderIdentifier = RecorderIdentifier.from(pollReq.getRecorderInfo());
    RecorderDetail recorderDetail = this.recorderLookup.computeIfAbsent(recorderIdentifier,
        key -> new RecorderDetail(key, thresholdForDefunctRecorderInSecs));
    return recorderDetail.receivePoll(pollReq);
  }

  /**
   * Returns null if no work assignment schedule is present
   * Returns null if no work assignments are left or none is ready yet to be handed out
   * Returns work assignment otherwise
   * @return
   */
  @Override
  public Recorder.WorkAssignment getNextWorkAssignment() {
    if (workAssignmentSchedule != null) {
      try {
        return workAssignmentSchedule.getNextWorkAssignment();
      } catch (NullPointerException ex) {
        //Do nothing in this scenario. This exception can happen when:
        //t0=if conditional is evaluated successfully
        //t1=aggregation window start timer called but by that time work prototype was not fetched from leader
        //t2=schedule set to null because no work fetched from leader so nothing to schedule
        //t3=above try block executed resulting in null pointer exception
      }
    }
    return null;
  }

  @Override
  public void updateWorkAssignmentSchedule(WorkAssignmentSchedule workAssignmentSchedule) {
    this.workAssignmentSchedule = workAssignmentSchedule;
  }

  @Override
  public int getRecorderTargetCountToMeetCoverage(int coveragePct) {
    final List<RecorderDetail> availableRecorders = this.recorderLookup.values().stream()
        .filter(recorderDetail -> !recorderDetail.isDefunct())
        .collect(Collectors.toList());
    return Math.round((coveragePct * availableRecorders.size()) / 100.0f);
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
