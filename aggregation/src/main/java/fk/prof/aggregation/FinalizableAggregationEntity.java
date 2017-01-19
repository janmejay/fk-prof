package fk.prof.aggregation;

public abstract class FinalizableAggregationEntity<T> {
  private boolean finalized = false;

  public T finalizeEntity() {
    ensureEntityIsWriteable();
    finalized = true;
    return buildFinalizedEntity();
  }

  protected void ensureEntityIsWriteable() {
    if(finalized) {
      throw new IllegalStateException("Aggregation window has already been finalized and cannot be updated");
    }
  }

  protected abstract T buildFinalizedEntity();
}
