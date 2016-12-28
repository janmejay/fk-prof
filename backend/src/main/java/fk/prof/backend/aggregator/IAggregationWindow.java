package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import recording.Recorder;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public interface IAggregationWindow {
  /**
   * Gets status associated with workId
   * @param workId
   * @return aggregation status of workId
   */
   AggregationStatus getStatus(long workId);

  /**
   * Updates status of workId to indicate it is receiving profile data
   * @param workId
   * @return true if status of workId was successfully update to indicate start of profile, false otherwise
   */
  boolean startReceivingProfile(long workId);

  /**
   * Aborts all profiles which are in ongoing state at present. Usually will be called when aggregation window expires
   */
  void abortOngoingProfiles();

  /**
   * Aggregates wse in the associated work type bucket. Throws {@link AggregationFailure} if aggregation fails
   * Example: if work type = cpu_sample_work, then the profile data is aggregated in corresponding aggregation bucket
   * @param wse
   */
  void aggregate(Recorder.Wse wse) throws AggregationFailure;
}
