package fk.prof.backend.model.request;

import fk.prof.backend.exception.AggregationFailure;
import io.vertx.core.shareddata.LocalMap;

public class SharedMapBasedSingleProcessingOfProfileGate implements ISingleProcessingOfProfileGate {
  private final LocalMap<Long, Boolean> workIdsInPipeline;

  public SharedMapBasedSingleProcessingOfProfileGate(LocalMap<Long, Boolean> workIdsInPipeline) {
    this.workIdsInPipeline = workIdsInPipeline;
  }

  public void accept(Long workId) throws AggregationFailure {
    Boolean existing = workIdsInPipeline.putIfAbsent(workId, true);
    if(existing != null) {
      throw new AggregationFailure(String.format("Profile is already being aggregated for work_id=%d in a different request", workId));
    }
  }

  public void finish(Long workId) {
    workIdsInPipeline.remove(workId);
  }

}
