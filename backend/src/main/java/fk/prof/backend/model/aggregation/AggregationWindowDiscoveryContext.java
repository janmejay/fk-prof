package fk.prof.backend.model.aggregation;

import fk.prof.backend.aggregator.AggregationWindow;

public interface AggregationWindowDiscoveryContext {
  AggregationWindow getAssociatedAggregationWindow(long workId);
}
