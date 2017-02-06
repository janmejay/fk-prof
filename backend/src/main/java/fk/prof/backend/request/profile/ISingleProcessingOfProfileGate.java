package fk.prof.backend.request.profile;

import fk.prof.backend.exception.AggregationFailure;

public interface ISingleProcessingOfProfileGate {
  void accept(long workId) throws AggregationFailure;
  void finish(long workId);
}
