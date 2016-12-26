package fk.prof.backend.aggregator;

public enum AggregationStatus {
  SCHEDULED,
  ONGOING,
  ONGOING_PARTIAL,
  PARTIAL,
  COMPLETED,
  RETRIED,
  ABORTED
}
