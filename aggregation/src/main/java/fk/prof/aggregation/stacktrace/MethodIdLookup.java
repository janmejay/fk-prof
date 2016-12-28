package fk.prof.aggregation.stacktrace;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lookup table to de-dup method fqdn.
 */
public class MethodIdLookup {

    private AtomicInteger counter = new AtomicInteger(0);
    private ConcurrentHashMap<String, Integer> lookup = new ConcurrentHashMap<>();

    /**
     * @param methodSignature
     * @return Returns a unique id mapping for a given method signature.
     */
    public Integer getOrAdd(String methodSignature) {
        return lookup.computeIfAbsent(methodSignature, (key -> counter.incrementAndGet()));
    }

    public int size() {
        return lookup.size();
    }

    public Set<Map.Entry<String, Integer>> entrySet() {
        return lookup.entrySet();
    }
}
