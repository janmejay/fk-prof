package fk.prof.backend.model.aggregation;

import fk.prof.backend.aggregator.AggregationWindow;

public interface ActiveAggregationWindows extends AggregationWindowDiscoveryContext {
  void associateAggregationWindow(final long[] workIds, AggregationWindow aggregationWindow)
      throws IllegalStateException;

  void deAssociateAggregationWindow(long[] workIds);
}
