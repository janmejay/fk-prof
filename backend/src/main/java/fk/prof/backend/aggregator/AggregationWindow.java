package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import recording.Recorder;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationWindow {
  private final ConcurrentHashMap<Long, AggregationStatus> workStatusLookup = new ConcurrentHashMap<>();
  private LocalDateTime start = null, endWithTolerance = null;

  private final CpuSamplingAggregationBucket cpuSamplingAggregationBucket = new CpuSamplingAggregationBucket();

  public AggregationWindow(LocalDateTime start, int windowDurationInMinutes, int toleranceInSeconds, long[] workIds) {
    this.start = start;
    this.endWithTolerance = this.start.plusMinutes(windowDurationInMinutes).plusSeconds(toleranceInSeconds);
    for (int i = 0; i < workIds.length; i++) {
      this.workStatusLookup.put(workIds[i], AggregationStatus.SCHEDULED);
    }
  }

  /**
   * Gets status associated with workId
   * @param workId
   * @return aggregation status of workId
   */
  public AggregationStatus getStatus(long workId) {
    return workStatusLookup.get(workId);
  }

  /**
   * Updates status of workId to indicate it is receiving profile data
   * @param workId
   * @return true if status of workId was successfully update to indicate start of profile, false otherwise
   */
  public boolean startReceivingProfile(long workId) {
    boolean replaced = this.workStatusLookup.replace(workId, AggregationStatus.SCHEDULED, AggregationStatus.ONGOING);
    if(!replaced) {
      replaced = this.workStatusLookup.replace(workId, AggregationStatus.PARTIAL, AggregationStatus.ONGOING_PARTIAL);
    }
    return replaced;
  }

  /**
   * Aborts all profiles which are in ongoing state at present. Usually will be called when aggregation window expires
   */
  public void abortOngoingProfiles() {
    this.workStatusLookup.replaceAll((workId, status) -> {
      if (status.equals(AggregationStatus.ONGOING) || status.equals(AggregationStatus.ONGOING_PARTIAL)) {
        return AggregationStatus.ABORTED;
      } else {
        return status;
      }
    });
  }

  /**
   * Aggregates wse in the associated work type bucket. Throws {@link AggregationFailure} if aggregation fails
   * Example: if work type = cpu_sample_work, then the profile data is aggregated in {@link #cpuSamplingAggregationBucket}
   * @param wse
   */
  public void aggregate(Recorder.Wse wse) throws AggregationFailure {
    switch(wse.getWType()) {
      case cpu_sample_work:
        Recorder.StackSampleWse stackSampleWse = wse.getCpuSampleEntry();
        if(stackSampleWse == null) {
          throw new AggregationFailure(String.format("work type=%s did not have associated samples", wse.getWType()));
        }
        cpuSamplingAggregationBucket.aggregate(stackSampleWse);
        break;
      default:
        throw new AggregationFailure(String.format("Aggregation not supported for work type=%s", wse.getWType()));
    }
  }
}
