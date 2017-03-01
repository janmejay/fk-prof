package fk.prof.backend.service;

import fk.prof.backend.aggregator.AggregationWindow;

public interface AggregationWindowWriteContext {
  void associateAggregationWindow(long workId, AggregationWindow aggregationWindow);
  void deAssociateAggregationWindow(long[] workIds);
}
