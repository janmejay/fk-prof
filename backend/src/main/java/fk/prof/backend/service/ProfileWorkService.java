package fk.prof.backend.service;

import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.aggregator.AggregationWindowStore;

//TODO: Liable for refactoring. For now, placeholder to enable functional completion of /profile api
public class ProfileWorkService implements IProfileWorkService {
  private AggregationWindowStore aggregationWindowStore = new AggregationWindowStore();

  public void associateAggregationWindow(Long workId, AggregationWindow aggregationWindow) {
    this.aggregationWindowStore.add(workId, aggregationWindow);
  }

  public AggregationWindow getAssociatedAggregationWindow(Long workId) {
    return this.aggregationWindowStore.get(workId);
  }
}
