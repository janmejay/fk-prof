package fk.prof.backend.aggregator;

import com.koloboke.collect.map.hash.HashObjIntMap;
import com.koloboke.collect.map.hash.HashObjIntMaps;

import java.time.LocalDateTime;
import java.util.Map;

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

  public void addTrace(String traceName, int coveragePct) {
    traceCoverages.put(traceName, coveragePct);
  }

  /**
   * Returns the underlying map used to store trace name -> trace coverage data associated with a work id
   * The returned map implementation is not guaranteed to be thread-safe
   * Ideally, should be called when the aggregation window with which this instance is associated, expires.
   * @return lookup map for trace coverage using trace name
   */
  public Map<String, Integer> getTraceCoverages() {
    return this.traceCoverages;
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
