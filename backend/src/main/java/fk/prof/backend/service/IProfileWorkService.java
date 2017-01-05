package fk.prof.backend.service;

import fk.prof.backend.aggregator.AggregationWindow;

//TODO: Liable for refactoring. For now, placeholder to enable functional completion of /profile api
public interface IProfileWorkService {

  /**
   * Associates a workId with given aggregation window
   *
   * @param workId
   * @param aggregationWindow
   */
  void associateAggregationWindow(Long workId, AggregationWindow aggregationWindow);

  /**
   * Returns the aggregation window associated with given work id, null if no such workId exists
   *
   * @param workId
   * @return associated aggregation window
   */
  AggregationWindow getAssociatedAggregationWindow(Long workId);

}
