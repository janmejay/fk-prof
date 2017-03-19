package fk.prof.backend.model.assignment;

public class WorkAssignmentScheduleBootstrapConfig {
  private final int windowDurationInSecs;
  private final int windowEndToleranceInSecs;
  private final int schedulingBufferInSecs;
  private final int minAcceptableDelayForWorkAssignmentInSecs;
  private final int maxAcceptableDelayForWorkAssignmentInSecs;

  public WorkAssignmentScheduleBootstrapConfig(int windowDurationInSecs,
                                               int windowEndToleranceInSecs,
                                               int schedulingBufferInSecs,
                                               int maxAcceptableDelayForWorkAssignmentInSecs)
      throws IllegalArgumentException {
    this.windowDurationInSecs = windowDurationInSecs;
    this.windowEndToleranceInSecs = windowEndToleranceInSecs;
    this.schedulingBufferInSecs = schedulingBufferInSecs;
    this.maxAcceptableDelayForWorkAssignmentInSecs = maxAcceptableDelayForWorkAssignmentInSecs;
    this.minAcceptableDelayForWorkAssignmentInSecs = schedulingBufferInSecs / 2;

    if(this.maxAcceptableDelayForWorkAssignmentInSecs < (this.minAcceptableDelayForWorkAssignmentInSecs * 2)) {
      throw new IllegalArgumentException(String.format("Max acceptable delay for work assignment = %d" +
          "should be at least be twice of min acceptable delay for work assignment = %d", maxAcceptableDelayForWorkAssignmentInSecs, minAcceptableDelayForWorkAssignmentInSecs));
    }
  }

  public int getWindowDurationInSecs() {
    return windowDurationInSecs;
  }

  public int getWindowEndToleranceInSecs() {
    return windowEndToleranceInSecs;
  }

  public int getSchedulingBufferInSecs() {
    return schedulingBufferInSecs;
  }

  public int getMinAcceptableDelayForWorkAssignmentInSecs() {
    return minAcceptableDelayForWorkAssignmentInSecs;
  }

  public int getMaxAcceptableDelayForWorkAssignmentInSecs() {
    return maxAcceptableDelayForWorkAssignmentInSecs;
  }

}
