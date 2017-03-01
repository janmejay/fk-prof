package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.PriorityBlockingQueue;

public class WorkAssignmentSchedule {
  private final static Logger logger = LoggerFactory.getLogger(WorkAssignmentSchedule.class);
  private final static long NANOS_IN_SEC = (long)Math.pow(10, 9);

  private final long referenceTimeInNanos;
  private final int minAcceptableDelayForWorkAssignmentInSecs;
  private final PriorityBlockingQueue<ScheduleEntry> entries = new PriorityBlockingQueue<>();

  public WorkAssignmentSchedule(Recorder.WorkAssignment.Builder[] workAssignments,
                                int coveragePct,
                                int windowDurationInMins,
                                int windowToleranceInSeconds,
                                int schedulingBufferInSeconds,
                                int concurrentSchedulingLimit,
                                int profileDurationInSeconds,
                                int profileIntervalInSeconds)
      throws IllegalStateException {

    int effectiveWindowDurationInSeconds = (windowDurationInMins * 60) - windowToleranceInSeconds - schedulingBufferInSeconds - profileIntervalInSeconds;
    int effectiveProfileDurationInSeconds = profileDurationInSeconds + schedulingBufferInSeconds;
    int maxScheduleEntriesWithNoOverlap = effectiveWindowDurationInSeconds / effectiveProfileDurationInSeconds;
    int maxScheduleEntriesWithOverlap = maxScheduleEntriesWithNoOverlap * concurrentSchedulingLimit;
    int targetScheduleEntries = workAssignments.length;
    if (maxScheduleEntriesWithOverlap < targetScheduleEntries) {
      throw new IllegalStateException(String.format("Not possible to setup schedule for " +
          "vm_coverage=%d, target_profiles=%d, profile_duration_secs=%d, profile_interval_secs=%d",
          coveragePct, targetScheduleEntries, profileDurationInSeconds, profileIntervalInSeconds));
    }

    this.referenceTimeInNanos = System.nanoTime();
    this.minAcceptableDelayForWorkAssignmentInSecs = schedulingBufferInSeconds / 2;

    long initialOffsetInNanos = (long)(schedulingBufferInSeconds * NANOS_IN_SEC);
    int minOverlap = targetScheduleEntries / maxScheduleEntriesWithNoOverlap;
    int currentWorkAssignmentIndex = 0;
    for (int i = 0; i < maxScheduleEntriesWithNoOverlap; i++) {
      long offsetInNanos = initialOffsetInNanos + (effectiveProfileDurationInSeconds * i * NANOS_IN_SEC);
      for(int j = 0; j < minOverlap; j++) {
        this.entries.add(new ScheduleEntry(workAssignments[currentWorkAssignmentIndex++], offsetInNanos));
      }
    }
    int remainingAssignments = targetScheduleEntries % maxScheduleEntriesWithNoOverlap;
    assert targetScheduleEntries == (currentWorkAssignmentIndex + remainingAssignments);
    for (int i = 0; i < remainingAssignments; i++) {
      long offsetInNanos = initialOffsetInNanos + (effectiveProfileDurationInSeconds * i * NANOS_IN_SEC);
      this.entries.add(new ScheduleEntry(workAssignments[currentWorkAssignmentIndex++], offsetInNanos));
    }
  }

  /**
   * Supplies with a work assignment
   * If returns null, then no work assignments are pending
   * @return WorkAssignment or null if all work assignments are exhausted
   */
  public Recorder.WorkAssignment getNextWorkAssignment() {
    ScheduleEntry scheduleEntry;
    while((scheduleEntry = this.entries.poll()) != null) {
      Recorder.WorkAssignment workAssignment = scheduleEntry.getWorkAssignment((System.nanoTime() - referenceTimeInNanos), minAcceptableDelayForWorkAssignmentInSecs);
      if(workAssignment != null) {
        return workAssignment;
      }
    }

    return null;
  }

  public static class ScheduleEntry implements Comparable<ScheduleEntry> {
    private final static Logger logger = LoggerFactory.getLogger(ScheduleEntry.class);

    private final Recorder.WorkAssignment.Builder workAssignmentBuilder;
    private final long offsetFromReferenceInNanos;

    public ScheduleEntry(Recorder.WorkAssignment.Builder workAssignmentBuilder, long offsetFromReferenceInNanos) {
      this.workAssignmentBuilder = Preconditions.checkNotNull(workAssignmentBuilder);
      this.offsetFromReferenceInNanos = offsetFromReferenceInNanos;
    }

    /**
     * Builds work assignment by setting delay and issue time fields
     * @param elapsedTimeSinceSchedulingInNanos
     * @param minAcceptableDelayInSecs
     * @return workassignment if remaining delay before work has to start is greater than min acceptable delay, null otherwise
     */
    public Recorder.WorkAssignment getWorkAssignment(long elapsedTimeSinceSchedulingInNanos, int minAcceptableDelayInSecs) {
      int remainingDelayInSecs = (int)((offsetFromReferenceInNanos - elapsedTimeSinceSchedulingInNanos) / NANOS_IN_SEC);
      if (remainingDelayInSecs < minAcceptableDelayInSecs) {
        logger.warn(String.format("Scheduling miss for work_id=%d, remaining delay=%d", workAssignmentBuilder.getWorkId(), remainingDelayInSecs));
        return null;
      }
      return workAssignmentBuilder
          .setDelay(remainingDelayInSecs)
          .setIssueTime(LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
          .build();
    }

    @Override
    public int compareTo(ScheduleEntry other) {
      return (int)(this.offsetFromReferenceInNanos - other.offsetFromReferenceInNanos);
    }

  }

}
