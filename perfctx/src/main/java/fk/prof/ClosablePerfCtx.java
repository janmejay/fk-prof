package fk.prof;

/**
 * @understands auto-close style fk.prof.{@link PerfCtx}
 */
public class ClosablePerfCtx implements AutoCloseable {
    private final PerfCtx perfCtx;

    public ClosablePerfCtx(PerfCtx perfCtx) {
        this.perfCtx = perfCtx;
        perfCtx.begin();
    }

    @Override
    public void close() {
        perfCtx.end();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClosablePerfCtx that = (ClosablePerfCtx) o;

        return perfCtx != null ? perfCtx.equals(that.perfCtx) : that.perfCtx == null;
    }

    @Override
    public int hashCode() {
        return perfCtx != null ? perfCtx.hashCode() : 0;
    }

    @Override
    public String toString() {
        return perfCtx.autoClosableStringRep;
    }
}
