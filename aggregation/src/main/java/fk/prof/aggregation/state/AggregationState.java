package fk.prof.aggregation.state;


//TODO: Add ascii-art here explaining the state machine with transitions
public enum AggregationState {
  SCHEDULED {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      switch (stateEvent) {
        case START_PROFILE:
          return ONGOING;
        default:
          return this;
      }
    }
  },
  PARTIAL {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      switch (stateEvent) {
        case START_PROFILE:
          return ONGOING_PARTIAL;
        default:
          return this;
      }
    }
  },
  ONGOING {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      switch (stateEvent) {
        case COMPLETE_PROFILE:
          return COMPLETED;
        case ABANDON_PROFILE:
          return PARTIAL;
        case ABORT_PROFILE:
          return ABORTED;
        default:
          return this;
      }
    }
  },
  ONGOING_PARTIAL {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      switch (stateEvent) {
        case COMPLETE_PROFILE:
          return RETRIED;
        case ABANDON_PROFILE:
          return PARTIAL;
        case ABORT_PROFILE:
          return ABORTED;
        default:
          return this;
      }
    }
  },
  //Terminal state
  COMPLETED {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      return this;
    }
  },
  //Terminal state
  RETRIED {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      return this;
    }
  },
  //Terminal state
  ABORTED {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      return this;
    }
  };

  public abstract AggregationState process(AggregationStateEvent stateEvent);
}
