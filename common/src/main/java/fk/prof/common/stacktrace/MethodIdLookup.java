package fk.prof.common.stacktrace;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MethodIdLookup {

    private AtomicLong counter = new AtomicLong(0);
    private ConcurrentHashMap<String, Long> lookup = new ConcurrentHashMap<>();

    public Long getOrAdd(String methodSignature) {
        return lookup.computeIfAbsent(methodSignature, (key -> counter.incrementAndGet()));
    }
}
