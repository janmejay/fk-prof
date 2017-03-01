package fk.prof.backend.service;

import com.google.common.base.Preconditions;
import fk.prof.backend.aggregator.AggregationWindow;

import java.util.concurrent.ConcurrentHashMap;

public class AggregationWindowLookupStore implements AggregationWindowReadContext, AggregationWindowWriteContext {
  private ConcurrentHashMap<Long, AggregationWindow> windowLookup = new ConcurrentHashMap<>();

  public AggregationWindow getAssociatedAggregationWindow(long workId) {
    return this.windowLookup.get(workId);
  }

  public void associateAggregationWindow(long workId, AggregationWindow aggregationWindow)
      throws IllegalStateException {
    Preconditions.checkNotNull(aggregationWindow);
    AggregationWindow existingWindow = this.windowLookup.putIfAbsent(workId, aggregationWindow);
    if (existingWindow != null) {
      throw new IllegalStateException(String.format("Aggregation window already associated with work_id=%d", workId));
    }
  }

  public void deAssociateAggregationWindow(long[] workIds) {
    for (int i = 0; i < workIds.length; i++) {
      this.windowLookup.remove(workIds[i]);
    }
  }
}
