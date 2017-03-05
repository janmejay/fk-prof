package fk.prof.backend.model.aggregation.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationWindowLookupStoreImpl implements AggregationWindowLookupStore {
  private Map<Long, AggregationWindow> windowLookup = new ConcurrentHashMap<>();

  //NOTE: Called on http event loop
  @Override
  public AggregationWindow getAssociatedAggregationWindow(long workId) {
    return this.windowLookup.get(workId);
  }

  //NOTE: Called always by backend daemon thread and not on http event loop
  @Override
  public void associateAggregationWindow(long[] workIds, AggregationWindow aggregationWindow)
      throws IllegalStateException {
    Preconditions.checkNotNull(aggregationWindow);
    Preconditions.checkNotNull(workIds);
    for(int i = 0;i < workIds.length;i++) {
      if(this.windowLookup.containsKey(workIds[i])) {
        throw new IllegalStateException(String.format("Aggregation window already associated with work_id=%d", workIds[i]));
      }
    }
    for(int i = 0;i < workIds.length;i++) {
      this.windowLookup.put(workIds[i], aggregationWindow);
    }
  }

  //NOTE: Called always by backend daemon thread and not on http event loop
  @Override
  public void deAssociateAggregationWindow(long[] workIds) {
    for (int i = 0; i < workIds.length; i++) {
      this.windowLookup.remove(workIds[i]);
    }
  }
}
