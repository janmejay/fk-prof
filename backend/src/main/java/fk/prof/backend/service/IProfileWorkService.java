package fk.prof.backend.service;

import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.aggregator.AggregationWindowStore;

//TODO: Liable for refactoring. For now, placeholder to enable functional completion of /profile apilati
public interface IProfileWorkService {
  void associateAggregationWindow(Long workId, AggregationWindow aggregationWindow);
  AggregationWindow getAssociatedAggregationWindow(Long workId);
}
