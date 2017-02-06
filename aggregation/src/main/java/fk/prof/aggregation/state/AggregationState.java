package fk.prof.aggregation.state;


//TODO: Add ascii-art here explaining the state machine with transitions
public enum AggregationState {
  SCHEDULED(false, false) {
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
  PARTIAL(false, false) {
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
  ONGOING(true, false) {
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
  ONGOING_PARTIAL(true, false) {
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
  COMPLETED(false, true) {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      return this;
    }
  },
  RETRIED(false, true) {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      return this;
    }
  },
  ABORTED(false, true) {
    @Override
    public AggregationState process(AggregationStateEvent stateEvent) {
      return this;
    }
  };

  public abstract AggregationState process(AggregationStateEvent stateEvent);

  private boolean ongoing = false;
  private boolean terminal = false;

  AggregationState(boolean ongoing, boolean terminal) {
    if(ongoing == true && terminal == true) {
      throw new IllegalArgumentException("Aggregation state cannot be ongoing and terminal simultaneously");
    }
    this.ongoing = ongoing;
    this.terminal = terminal;
  }

  public boolean isOngoing() {
    return this.ongoing;
  }
}
