package fk.prof.backend.service;

import fk.prof.backend.aggregator.AggregationWindow;

public interface AggregationWindowReadContext {
  AggregationWindow getAssociatedAggregationWindow(long workId);
}
