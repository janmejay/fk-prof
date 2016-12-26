package fk.prof.backend.aggregator;

import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: Evaluate if this is the right DS. Temporary for now
 * Ideally, there should be separate in-memory store/persistent store impl for storing work-id related data model
 */
public class AggregationWindowStore {

  private ConcurrentHashMap<Long, AggregationWindow> windowLookup = new ConcurrentHashMap<>();

  public void add(long workId, AggregationWindow window) {
    this.windowLookup.putIfAbsent(workId, window);
  }

  public AggregationWindow get(long workId) {
    return this.windowLookup.get(workId);
  }

  public void removeWorkIds(long[] workIds) {
    for (int i = 0; i < workIds.length; i++) {
      this.windowLookup.remove(workIds[i]);
    }
  }
}
