package fk.prof.backend.aggregator;

import com.koloboke.collect.map.hash.HashObjIntMap;
import com.koloboke.collect.map.hash.HashObjIntMaps;
import recording.Recorder;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Non getter methods in this class are not thread-safe
 * But, this should not be a concern because instances of this class are maintained for every work id
 * Updates to state associated with a work id happens in context of a request (single thread)
 */
public class ProfileWorkInfo {
  private AggregationStatus status = AggregationStatus.SCHEDULED;
  private LocalDateTime startedAt = null, endedAt = null;
  private int samples = 0;

  private final HashObjIntMap<String> traceCoverages = HashObjIntMaps.newUpdatableMap();
  private final Map<String, Integer> traceCoveragesUnmodifiableView = Collections.unmodifiableMap(traceCoverages);

  private final Set<Recorder.WorkType> associatedWorkTypes = new HashSet<>();
  private final Set<Recorder.WorkType> associatedWorkTypesUnmodifiableView = Collections.unmodifiableSet(associatedWorkTypes);

  public void addTrace(String traceName, int coveragePct) {
    traceCoverages.put(traceName, coveragePct);
  }

  public void associateWorkType(Recorder.WorkType workType) {
    this.associatedWorkTypes.add(workType);
  }

  /**
   * Returns unmodifiable view of trace name -> trace coverage data associated with a work id
   * @return lookup map for trace coverage using trace name
   */
  public Map<String, Integer> getTraceCoverages() {
    return this.traceCoveragesUnmodifiableView;
  }

  /**
   * Returns unmodifiable view of all work types associated with the workId
   * @return set of associated work types
   */
  public Set<Recorder.WorkType> getAssociatedWorkTypes() {
    return this.associatedWorkTypesUnmodifiableView;
  }

  public void setStatus(AggregationStatus aggregationStatus) {
    status = aggregationStatus;
  }

  public AggregationStatus getStatus() {
    return status;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public LocalDateTime getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(LocalDateTime endedAt) {
    this.endedAt = endedAt;
  }

  public int getSamples() {
    return samples;
  }

  public void incrementSamplesBy(int samples) {
    this.samples += samples;
  }
}
