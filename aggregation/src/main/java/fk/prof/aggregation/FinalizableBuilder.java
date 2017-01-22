package fk.prof.aggregation;

/**
 * Finalizable builder class provides semantics of a finalized flag and helper method which throws exception if this flag is true
 * It also provides impl of an instance method which turns this flag on and allows user to return a new entity which this builder was supposedly building
 * @param <T>
 */
public abstract class FinalizableBuilder<T> {
  private boolean finalized = false;

  public T finalizeEntity() {
    ensureEntityIsWriteable();
    finalized = true;
    return buildFinalizedEntity();
  }

  protected void ensureEntityIsWriteable() {
    if(finalized) {
      throw new IllegalStateException("Entity has already been finalized and cannot be updated");
    }
  }

  protected abstract T buildFinalizedEntity();
}
