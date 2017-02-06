package fk.prof.backend.service;

import fk.prof.backend.aggregator.AggregationWindow;

import java.util.concurrent.ConcurrentHashMap;

//TODO: Liable for refactoring. For now, placeholder to enable functional completion of /profile api
public class ProfileWorkService implements IProfileWorkService {
  private AggregationWindowStore aggregationWindowStore = new AggregationWindowStore();

  public void associateAggregationWindow(Long workId, AggregationWindow aggregationWindow) {
    this.aggregationWindowStore.add(workId, aggregationWindow);
  }

  public AggregationWindow getAssociatedAggregationWindow(Long workId) {
    return this.aggregationWindowStore.get(workId);
  }

  /**
   * TODO: Evaluate if this is the right DS. Temporary for now
   * Ideally, there should be separate in-memory store/persistent store impl for storing work-id related data model
   */
  public static class AggregationWindowStore {

    private ConcurrentHashMap<Long, AggregationWindow> windowLookup = new ConcurrentHashMap<>();

    public void add(long workId, AggregationWindow window) {
      AggregationWindow existingWindow = this.windowLookup.putIfAbsent(workId, window);
      if(existingWindow != null) {
        throw new IllegalStateException(String.format("Aggregation window already associated with work_id=%d", workId));
      }
    }

    public AggregationWindow get(long workId) {
      return this.windowLookup.get(workId);
    }

    /**
     * TODO: Untested
     * @param workIds
     */
    public void removeWorkIds(long[] workIds) {
      for (int i = 0; i < workIds.length; i++) {
        this.windowLookup.remove(workIds[i]);
      }
    }
  }
}
