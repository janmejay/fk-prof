package fk.prof.aggregation.state;

public enum AggregationStateEvent {
  START_PROFILE,
  COMPLETE_PROFILE,
  ABANDON_PROFILE_AS_CORRUPT,
  ABANDON_PROFILE_AS_INCOMPLETE,
  ABORT_PROFILE
}
