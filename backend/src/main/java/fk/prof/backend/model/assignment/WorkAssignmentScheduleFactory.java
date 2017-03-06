package fk.prof.backend.model.assignment;

import recording.Recorder;

public class WorkAssignmentScheduleFactory {
  private final int windowDurationInMins;
  private final int windowEndToleranceInSecs;
  private final int schedulingBufferInSecs;
  private final int minAcceptableDelayForWorkAssignmentInSecs;
  private final int maxAcceptableDelayForWorkAssignmentInSecs;

  public WorkAssignmentScheduleFactory(int windowDurationInMins,
                                       int windowEndToleranceInSecs,
                                       int schedulingBufferInSecs,
                                       int maxAcceptableDelayForWorkAssignmentInSecs)
      throws IllegalArgumentException {
    this.windowDurationInMins = windowDurationInMins;
    this.windowEndToleranceInSecs = windowEndToleranceInSecs;
    this.schedulingBufferInSecs = schedulingBufferInSecs;
    this.maxAcceptableDelayForWorkAssignmentInSecs = maxAcceptableDelayForWorkAssignmentInSecs;
    this.minAcceptableDelayForWorkAssignmentInSecs = schedulingBufferInSecs / 2;

    if(this.maxAcceptableDelayForWorkAssignmentInSecs < (this.minAcceptableDelayForWorkAssignmentInSecs * 2)) {
      throw new IllegalArgumentException(String.format("Max acceptable delay for work assignment = %d" +
          "should be at least be twice of min acceptable delay for work assignment = %d", maxAcceptableDelayForWorkAssignmentInSecs, minAcceptableDelayForWorkAssignmentInSecs));
    }
  }

  public WorkAssignmentSchedule getNewWorkAssignmentSchedule(Recorder.WorkAssignment.Builder[] workAssignmentBuilders,
                                                             int coveragePct,
                                                             int maxConcurrentSchedulingAllowed,
                                                             int profileDurationInSecs) {
    return new WorkAssignmentSchedule(windowDurationInMins, windowEndToleranceInSecs, schedulingBufferInSecs,
        minAcceptableDelayForWorkAssignmentInSecs, maxAcceptableDelayForWorkAssignmentInSecs,
        workAssignmentBuilders, coveragePct, maxConcurrentSchedulingAllowed, profileDurationInSecs);
  }

}
