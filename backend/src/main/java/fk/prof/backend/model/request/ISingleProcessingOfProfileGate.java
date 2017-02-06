package fk.prof.backend.model.request;

import fk.prof.backend.exception.AggregationFailure;

public interface ISingleProcessingOfProfileGate {
  void accept(long workId) throws AggregationFailure;
  void finish(long workId);
}
