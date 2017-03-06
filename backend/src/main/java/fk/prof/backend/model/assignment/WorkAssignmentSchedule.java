package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class WorkAssignmentSchedule {
  private final static Logger logger = LoggerFactory.getLogger(WorkAssignmentSchedule.class);
  private final static long NANOS_IN_SEC = (long)Math.pow(10, 9);

  private final long referenceTimeInNanos;
  private final int minAcceptableDelayForWorkAssignmentInSecs;
  private final int maxAcceptableDelayForWorkAssignmentInSecs;
  private final int maxConcurrentlyScheduledEntries;

  private final PriorityQueue<ScheduleEntry> entries = new PriorityQueue<>();
  private Set<RecorderIdentifier> assignedRecorders = new HashSet<>();
  private final ReentrantLock entriesLock = new ReentrantLock();

  public WorkAssignmentSchedule(int windowDurationInMins,
                                int windowEndToleranceInSecs,
                                int schedulingBufferInSecs,
                                int minAcceptableDelayForWorkAssignmentInSecs,
                                int maxAcceptableDelayForWorkAssignmentInSecs,
                                Recorder.WorkAssignment.Builder[] workAssignmentBuilders,
                                int coveragePct,
                                int maxConcurrentSlotsAllowed,
                                int profileDurationInSecs)
      throws IllegalArgumentException {

    // breathing space at the start, this will usually be a lower value that window tolerance
    int windowStartToleranceInSecs = schedulingBufferInSecs * 2;
    //actual time span for which schedule is calculated
    int effectiveWindowDurationInSecs = (windowDurationInMins * 60) - windowEndToleranceInSecs - windowStartToleranceInSecs;
    int effectiveProfileDurationInSecs = profileDurationInSecs + schedulingBufferInSecs;
    int maxScheduleEntriesWithNoOverlap = effectiveWindowDurationInSecs / effectiveProfileDurationInSecs;
    int maxScheduleEntriesWithOverlap = maxScheduleEntriesWithNoOverlap * maxConcurrentSlotsAllowed;
    int targetScheduleEntries = workAssignmentBuilders.length;
    if (maxScheduleEntriesWithOverlap < targetScheduleEntries) {
      throw new IllegalArgumentException(String.format("Not possible to setup schedule for " +
          "vm_coverage=%d, target_profiles=%d, profile_duration_secs=%d, max concurrent slots=%d",
          coveragePct, targetScheduleEntries, profileDurationInSecs, maxConcurrentSlotsAllowed));
    }

    this.minAcceptableDelayForWorkAssignmentInSecs = minAcceptableDelayForWorkAssignmentInSecs;
    this.maxAcceptableDelayForWorkAssignmentInSecs = maxAcceptableDelayForWorkAssignmentInSecs;
    if(this.maxAcceptableDelayForWorkAssignmentInSecs < (this.minAcceptableDelayForWorkAssignmentInSecs * 2)) {
      throw new IllegalArgumentException(String.format("Max acceptable delay for work assignment = %d" +
          "should be at least be twice of min acceptable delay for work assignment = %d", maxAcceptableDelayForWorkAssignmentInSecs, minAcceptableDelayForWorkAssignmentInSecs));
    }

    this.referenceTimeInNanos = System.nanoTime();
    long initialOffsetInNanos = windowStartToleranceInSecs * NANOS_IN_SEC;
    int minOverlap = targetScheduleEntries / maxScheduleEntriesWithNoOverlap;

    int currentWorkAssignmentIndex = 0;
    for (int i = 0; (minOverlap > 0) && (i < maxScheduleEntriesWithNoOverlap); i++) {
      long offsetInNanos = initialOffsetInNanos + (effectiveProfileDurationInSecs * i * NANOS_IN_SEC);
      for(int j = 0; j < minOverlap; j++) {
        this.entries.add(new ScheduleEntry(workAssignmentBuilders[currentWorkAssignmentIndex++], offsetInNanos));
      }
    }

    int remainingAssignments = targetScheduleEntries % maxScheduleEntriesWithNoOverlap;
    assert targetScheduleEntries == (currentWorkAssignmentIndex + remainingAssignments);
    for (int i = 0; i < remainingAssignments; i++) {
      long offsetInNanos = initialOffsetInNanos + (effectiveProfileDurationInSecs * i * NANOS_IN_SEC);
      this.entries.add(new ScheduleEntry(workAssignmentBuilders[currentWorkAssignmentIndex++], offsetInNanos));
    }

    maxConcurrentlyScheduledEntries = minOverlap + (remainingAssignments > 0 ? 1 : 0);
  }

  /**
   * Returns the maximum number of concurrently scheduled entries in this schedule
   * @return
   */
  public int getMaxConcurrentlyScheduledEntries() {
    return maxConcurrentlyScheduledEntries;
  }

  /**
   * Supplies with a work assignment
   * Returns null if:
   * > no work assignments are pending
   * > no work assignment ready to be handed out
   * > recorder already assigned work (tied to aggregation window)
   * > timeout while acquiring lock over queue
   * > interrupted while waiting to acquire lock over queue
   * > exception occurred while processing queue entries
   * @return WorkAssignment or null
   */
  public Recorder.WorkAssignment getNextWorkAssignment(RecorderIdentifier recorderIdentifier) {
    try {
      boolean acquired = entriesLock.tryLock(100, TimeUnit.MILLISECONDS);
      if(acquired) {
        try {
          if(this.assignedRecorders.contains(recorderIdentifier)) {
            return null;
          }

          ScheduleEntry scheduleEntry;
          while((scheduleEntry = this.entries.peek()) != null) {
            ScheduleEntry.ScheduleEntryValue value = scheduleEntry.getValue((System.nanoTime() - referenceTimeInNanos), minAcceptableDelayForWorkAssignmentInSecs, maxAcceptableDelayForWorkAssignmentInSecs);
            if(value.tooEarly) {
              return null; //Since this is a priority queue, no point checking subsequent entries if current entry indicates its too early
            } else {
              this.entries.poll(); //dequeue the entry. no point in keeping the entry around whether fetch was done on right time or it was a scheduling miss
              if(value.workAssignment != null) {
                this.assignedRecorders.add(recorderIdentifier);
                return value.workAssignment;
              }
            }
          }
        } catch (Exception ex) {
          //TODO: increment some metric somewhere
          logger.error("Unexpected error when getting fetching work assignment from schedule", ex);
        } finally {
          entriesLock.unlock();
        }
      } else {
        //TODO: increment some metric somewhere
        logger.warn("Timeout while acquiring lock for fetching work assignment from schedule");
      }
    } catch (InterruptedException ex) {
      //TODO: increment some metric somewhere
      logger.warn("Interrupted while acquiring lock for fetching work assignment from schedule");
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
     * @param elapsedTimeSinceSchedulingInNanos
     * @param minAcceptableDelayInSecs
     * @param maxAcceptableDelayInSecs
     * @return value of schedule entry with appropriate flags set to indicate if its too early or too late to receive work assignment
     */
    ScheduleEntryValue getValue(long elapsedTimeSinceSchedulingInNanos, int minAcceptableDelayInSecs, int maxAcceptableDelayInSecs) {
      int remainingDelayInSecs = (int)((offsetFromReferenceInNanos - elapsedTimeSinceSchedulingInNanos) / NANOS_IN_SEC);
      if (remainingDelayInSecs < minAcceptableDelayInSecs) {
        logger.error(String.format("Scheduling miss for work_id=%d, remaining delay=%d", workAssignmentBuilder.getWorkId(), remainingDelayInSecs));
        return new ScheduleEntryValue(null, false, true);
      }
      if (remainingDelayInSecs > maxAcceptableDelayInSecs) {
        logger.debug(String.format("Too early to hand over work assignment for work_id=%d, remaining delay=%d", workAssignmentBuilder.getWorkId(), remainingDelayInSecs));
        return new ScheduleEntryValue(null, true, false);
      }
      return new ScheduleEntryValue(
          workAssignmentBuilder
              .clone()
              .setDelay(remainingDelayInSecs)
              .setIssueTime(LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
              .build(),
          false, false);
    }

    @Override
    public int compareTo(ScheduleEntry other) {
      long diff = this.offsetFromReferenceInNanos - other.offsetFromReferenceInNanos;
      return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
    }

    static class ScheduleEntryValue {
      private final Recorder.WorkAssignment workAssignment;
      private final boolean tooEarly;
      private final boolean tooLate;

      ScheduleEntryValue(Recorder.WorkAssignment workAssignment, boolean tooEarly, boolean tooLate) {
        if(tooEarly && tooLate) {
          throw new IllegalArgumentException("Fetch of scheduling entry cannot be too early and too late simultaneously. Make up your mind!");
        } else if (tooEarly || tooLate) {
          if(workAssignment != null) {
            throw new IllegalArgumentException("Work assignment should be set as null if the fetch is too early or too late");
          }
        } else {
          if(workAssignment == null) {
            throw new IllegalArgumentException("Valid work assignment should be returned if the fetch is done at correct time");
          }
        }

        this.workAssignment = workAssignment;
        this.tooEarly = tooEarly;
        this.tooLate = tooLate;
      }
    }

  }

}
