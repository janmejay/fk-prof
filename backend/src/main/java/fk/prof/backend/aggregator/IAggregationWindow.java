package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.request.RecordedProfileIndexes;
import recording.Recorder;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public interface IAggregationWindow {
  /**
   * Gets profile work info associated with workId
   * @param workId
   * @return ProfileWorkInfo instance
   */
   ProfileWorkInfo getWorkInfo(long workId);

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
   * Aggregates wse in the associated work type bucket. Uses provided indexes to resolve id values.
   * Throws {@link AggregationFailure} if aggregation fails
   * Example: if work type = cpu_sample_work, then the profile data is aggregated in corresponding aggregation bucket
   * @param wse
   * @param indexes
   */
  void aggregate(Recorder.Wse wse, RecordedProfileIndexes indexes) throws AggregationFailure;

  /**
   * Updates work info associated with a workId using the provided wse
   * @param workId
   * @param wse
   */
  void updateWorkInfo(long workId, Recorder.Wse wse);
}
