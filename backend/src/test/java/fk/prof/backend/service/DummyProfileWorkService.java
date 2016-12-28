package fk.prof.backend.service;

import fk.prof.backend.aggregator.AggregationWindow;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

//TODO: Liable for refactoring. For now, placeholder to enable functional completion of /profile api
public class DummyProfileWorkService implements IProfileWorkService<AggregationWindow> {
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

    public AggregationWindowStore() {
      AggregationWindow w1 = new AggregationWindow(LocalDateTime.now(Clock.systemUTC()), 30, 60, new long[]{1, 2});
      AggregationWindow w2 = new AggregationWindow(LocalDateTime.now(Clock.systemUTC()), 30, 60, new long[]{3, 4});

      this.add(1l, w1);
      this.add(2l, w1);
      this.add(3l, w2);
      this.add(4l, w2);
    }

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
}
