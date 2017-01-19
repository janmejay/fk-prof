package fk.prof.aggregation.state;

public enum AggregationState {
  SCHEDULED {
    @Override
    public AggregationState process(AggregationStateTransition stateTransition) {
      switch (stateTransition) {
        case START_PROFILE:
          return ONGOING;
        default:
          return this;
      }
    }
  },
  PARTIAL {
    @Override
    public AggregationState process(AggregationStateTransition stateTransition) {
      switch (stateTransition) {
        case START_PROFILE:
          return ONGOING_PARTIAL;
        default:
          return this;
      }
    }
  },
  ONGOING {
    @Override
    public AggregationState process(AggregationStateTransition stateTransition) {
      switch (stateTransition) {
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
    public AggregationState process(AggregationStateTransition stateTransition) {
      switch (stateTransition) {
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
    public AggregationState process(AggregationStateTransition stateTransition) {
      return this;
    }
  },
  //Terminal state
  RETRIED {
    @Override
    public AggregationState process(AggregationStateTransition stateTransition) {
      return this;
    }
  },
  //Terminal state
  ABORTED {
    @Override
    public AggregationState process(AggregationStateTransition stateTransition) {
      return this;
    }
  };

  public abstract AggregationState process(AggregationStateTransition stateTransition);
}
