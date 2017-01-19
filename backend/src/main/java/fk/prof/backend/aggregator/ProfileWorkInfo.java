package fk.prof.backend.aggregator;

import com.koloboke.collect.map.hash.HashObjIntMap;
import com.koloboke.collect.map.hash.HashObjIntMaps;
import fk.prof.aggregation.state.AggregationState;
import fk.prof.aggregation.state.AggregationStateTransition;
import fk.prof.aggregation.FinalizableAggregationEntity;
import fk.prof.aggregation.finalized.FinalizedProfileWorkInfo;
import recording.Recorder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Non getter methods in this class are not thread-safe
 * But, this should not be a concern because instances of this class are maintained for every work id
 * Updates to state associated with a work id happens in context of a request (single thread)
 */
public class ProfileWorkInfo extends FinalizableAggregationEntity<FinalizedProfileWorkInfo> {
  //TODO: Provide recorder version to this instance
  private int recorderVersion;
  private AggregationState state = AggregationState.SCHEDULED;
  private LocalDateTime startedAt = null, endedAt = null;
  private final HashObjIntMap<String> traceCoverages = HashObjIntMaps.newUpdatableMap();
  private final Set<Recorder.WorkType> associatedWorkTypes = new HashSet<>();

  public void updateWSESpecificDetails(Recorder.Wse wse) {
    for (Recorder.TraceContext trace : wse.getIndexedData().getTraceCtxList()) {
      traceCoverages.put(trace.getTraceName(), trace.getCoveragePct());
    }
    associatedWorkTypes.add(wse.getWType());
  }

  public AggregationState startProfile(int recorderVersion, LocalDateTime startedAt) {
    if(!processStateTransition(AggregationStateTransition.START_PROFILE)) {
      throw new IllegalStateException(String.format("Invalid transition %s for current state %s",
          AggregationStateTransition.START_PROFILE, state));
    }
    this.recorderVersion = recorderVersion;
    this.startedAt = startedAt;
    return state;
  }

  public AggregationState completeProfile() {
    if(!processStateTransition(AggregationStateTransition.COMPLETE_PROFILE)) {
      throw new IllegalStateException(String.format("Invalid transition %s for current state %s",
          AggregationStateTransition.COMPLETE_PROFILE, state));
    }
    this.endedAt = LocalDateTime.now(Clock.systemUTC());
    return state;
  }

  public AggregationState abandonProfile() {
    if(!processStateTransition(AggregationStateTransition.ABANDON_PROFILE)) {
      throw new IllegalStateException(String.format("Invalid transition %s for current state %s",
          AggregationStateTransition.ABANDON_PROFILE, state));
    }
    this.endedAt = LocalDateTime.now(Clock.systemUTC());
    return state;
  }

  public AggregationState abortProfile() {
    if(!processStateTransition(AggregationStateTransition.ABORT_PROFILE)) {
      throw new IllegalStateException(String.format("Invalid transition %s for current state %s",
          AggregationStateTransition.ABORT_PROFILE, state));
    }
    return state;
  }

  private boolean processStateTransition(AggregationStateTransition stateTransition) {
    AggregationState newState = state.process(stateTransition);
    if(newState.equals(state)) {
      return false;
    } else {
      state = newState;
      return true;
    }
  }

  @Override
  protected FinalizedProfileWorkInfo buildFinalizedEntity() {
    //TODO: Implement translation layer to finalized entity
    return null;
  }
}
