package fk.prof.backend.model.request;

import fk.prof.backend.exception.AggregationFailure;

public interface ISingleProcessingOfProfileGate {
  void accept(Long workId) throws AggregationFailure;
  void finish(Long workId);
}
