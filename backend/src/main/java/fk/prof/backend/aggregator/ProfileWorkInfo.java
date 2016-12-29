package fk.prof.backend.aggregator;

import com.koloboke.collect.map.hash.HashObjIntMap;
import com.koloboke.collect.map.hash.HashObjIntMaps;

/**
 * Non getter methods in this class are not thread-safe
 * But, this should not be a concern because instances of this class are maintained for every work id
 * Updates to state associated with a work id happens in context of a request (single thread)
 */
public class ProfileWorkInfo {
  private AggregationStatus status = AggregationStatus.SCHEDULED;
  private final HashObjIntMap<String> traceCoverages = HashObjIntMaps.newUpdatableMap();

  public void addTrace(String traceName, int coveragePct) {
    traceCoverages.put(traceName, coveragePct);
  }

  public void setStatus(AggregationStatus aggregationStatus) {
    status = aggregationStatus;
  }

  public AggregationStatus getStatus() {
    return status;
  }
}
