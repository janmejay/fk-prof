package fk.prof.backend.service;

import fk.prof.backend.aggregator.IAggregationWindow;

//TODO: Liable for refactoring. For now, placeholder to enable functional completion of /profile apilati
public interface IProfileWorkService<T extends IAggregationWindow> {

  /**
   * Associates a workId with given aggregation window
   * @param workId
   * @param aggregationWindow
   */
  void associateAggregationWindow(Long workId, T aggregationWindow);

  /**
   * Returns the aggregation window associated with given work id, null if no such workId exists
   * @param workId
   * @return
   */
  T getAssociatedAggregationWindow(Long workId);

}
