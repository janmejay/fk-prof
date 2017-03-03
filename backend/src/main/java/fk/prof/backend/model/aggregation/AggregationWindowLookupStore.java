package fk.prof.backend.model.aggregation;

import fk.prof.backend.aggregator.AggregationWindow;

public interface AggregationWindowLookupStore extends AggregationWindowDiscoveryContext {
  void associateAggregationWindow(long[] workIds, AggregationWindow aggregationWindow)
      throws IllegalStateException;

  void deAssociateAggregationWindow(long[] workIds);
}
