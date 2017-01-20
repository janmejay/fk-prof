package fk.prof;

import java.util.function.Function;

/**
 * @understands a named "logical" context under which all performance data is aggregated
 */
public class PerfCtx {
    private final int ctxId;
    private final String stringRep;
    final String autoClosableStringRep;

    public PerfCtx(final String name, final int coveragePct) {
        if (coveragePct < 0 || coveragePct > 100) {
            throw new IllegalArgumentException(String.format("Coverage-percentage value %s is not valid, a valid value must be in the range [0, 100].", coveragePct));
        }
        if (name.contains("%") || name.contains("~")) {
            throw new IllegalArgumentException(String.format("Name '%s' has invalid character(s), chars '%%' and '~' are not allowed.", name));
        }
        char c = name.charAt(0);
        if (! (Character.isAlphabetic(c) || Character.isDigit(c))) {
            throw new IllegalArgumentException(String.format("Name '%s' has an invalid starting character, first-char must be alpha-numeric.", name));
        }
        ctxId = registerCtx(name, coveragePct);
        stringRep = String.format("PerfCtx(%s) {name: '%s', coverage: %s%%}", ctxId, name, coveragePct);
        autoClosableStringRep = "Closable" + stringRep; 
    }

    private native int registerCtx(String name, int coveragePct);

    public void end() {
        end(ctxId);
    }

    public void start() {
        start(ctxId);
    }
    
    public ClosablePerfCtx open() {
        return new ClosablePerfCtx(this);
    }

    private native void end(int ctxId);
    
    private native void start(int ctxId);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PerfCtx perfCtx = (PerfCtx) o;

        return ctxId == perfCtx.ctxId;
    }

    @Override
    public int hashCode() {
        return ctxId;
    }

    @Override
    public String toString() {
        return stringRep;
    }
}
