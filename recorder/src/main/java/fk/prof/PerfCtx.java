package fk.prof;

/**
 * @understands a named "logical" context under which all performance data is aggregated
 * <p>
 * Note about constructors:
 * Creates a perf-data-recording-context with desired merge semantics. This internally checks if a conflicting duplicate context (same name, but different properties) are created with conflicting coverage-pct and/or merge-semantics) creation is being attempted and fails the call.
 * <p>
 * The best way to use this is to create a static+final instance and use it in the relevant places. If that is not possible, separate instances may be used, but user must take care to not provide conflicting values for these parameters. Also, constructor here has a minor performance-cost, so its best used as static+final instance.
 * <p>
 * Constraints / Limits:
 * 1. An application (single JVM instance) can have only upto 200 unique PerfCtx instances (uniqueness is implied by name)
 * 2. There exists an upper-limit to number of context to be tracked. This may change between releases. Consult the package-maintainers to learn the actual enforced limit.
 * 3. No more than 5 perf-ctx instances should be nested in general. Nesting with the same ctx is the only exception (except for scoped-strict) because no additional context tracking is necessary in this case.
   */
public class PerfCtx {
    private static boolean noDepMode = false; 
    private final long ctxId;
    private final String stringRep;
    final String autoClosableStringRep;

    public PerfCtx(final String name) throws IllegalArgumentException, PerfCtxInitException {
        this(name, 10);
    }

    public PerfCtx(final String name, final int coveragePct) throws IllegalArgumentException, PerfCtxInitException {
        this(name, coveragePct, MergeSemantics.MERGE_TO_PARENT);
    }

    public PerfCtx(final String name, final int coveragePct, final MergeSemantics mrgSem) throws IllegalArgumentException, PerfCtxInitException {
        if (coveragePct < 0 || coveragePct > 100) {//coverage == 0 is actually not invalid, because one can write scoped CTXs, which don't make sense in isolation
            throw new IllegalArgumentException(String.format("Coverage-percentage value %s is not valid, a valid value must be in the range [0, 100].", coveragePct));
        }
        if (name.contains("%") || name.contains("~") || name.contains("<") || name.contains(">")) {
            throw new IllegalArgumentException(String.format("Name '%s' has invalid character(s), chars '%%' and '~' are not allowed.", name));
        }
        char c = name.charAt(0);
        if (!(Character.isAlphabetic(c) || Character.isDigit(c))) {
            throw new IllegalArgumentException(String.format("Name '%s' has an invalid starting character, first-char must be alpha-numeric.", name));
        }

        long ctxIdTmp = 0;
        if (! noDepMode) {
            try {
                ctxIdTmp = registerCtx(name, coveragePct, mrgSem.getTypeId());
            } catch (UnsatisfiedLinkError e) {
                noDepMode = true;
            }
        } 
        ctxId = ctxIdTmp;
        stringRep = String.format("PerfCtx(%s) {name: '%s', coverage: %s%%, merge_semantics: '%s'}", ctxId, name, coveragePct, mrgSem);
        autoClosableStringRep = "Closable" + stringRep;
    }

    private native long registerCtx(String name, int coveragePct, int typeId) throws PerfCtxInitException;

    public void end() throws IncorrectContextException {
        if (! noDepMode) end(ctxId);
    }

    public void begin() {
        if (! noDepMode) begin(ctxId);
    }

    public ClosablePerfCtx open() {
        return new ClosablePerfCtx(this);
    }

    private native void end(long ctxId) throws IncorrectContextException;

    private native void begin(long ctxId);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PerfCtx perfCtx = (PerfCtx) o;

        return ctxId == perfCtx.ctxId;
    }

    @Override
    public int hashCode() {
        return (int) (ctxId ^ (ctxId >>> 32));
    }

    @Override
    public String toString() {
        return stringRep;
    }
}
