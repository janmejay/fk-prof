package fk.prof.aggregation.finalized;

import fk.prof.aggregation.SerializableAggregationEntity;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.aggregation.state.AggregationState;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public class FinalizedProfileWorkInfo implements SerializableAggregationEntity {
  private final int recorderVersion;
  private final AggregationState state;
  private final LocalDateTime startedAt;
  private final LocalDateTime endedAt;
  private final Map<String, Integer> traceCoverages;
  private final Map<AggregatedProfileModel.WorkType, Integer> associatedWorkTypes;

  public FinalizedProfileWorkInfo(int recorderVersion,
                                  AggregationState state,
                                  LocalDateTime startedAt,
                                  LocalDateTime endedAt,
                                  Map<String, Integer> traceCoverages,
                                  Map<AggregatedProfileModel.WorkType, Integer> associatedWorkTypes) {
    this.recorderVersion = recorderVersion;
    this.state = state;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.traceCoverages = traceCoverages;
    this.associatedWorkTypes = associatedWorkTypes;
  }

  //NOTE: Exposing this to make the class more testable since startedAt has generated value
  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  //NOTE: Exposing this to make the class more testable since endedAt has generated value
  public LocalDateTime getEndedAt() {
    return endedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FinalizedProfileWorkInfo)) {
      return false;
    }

    FinalizedProfileWorkInfo other = (FinalizedProfileWorkInfo) o;
    return this.recorderVersion == other.recorderVersion
        && this.state.equals(other.state)
        && this.startedAt.equals(other.startedAt)
        && this.endedAt.equals(other.endedAt)
        && this.traceCoverages.equals(other.traceCoverages)
        && this.associatedWorkTypes.equals(other.associatedWorkTypes);
  }
}