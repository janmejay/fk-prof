package fk.prof.common.stacktrace;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MethodIdLookup {
    //-2 is reserved for placeholder global root method of all stack-trace
    public final static long GLOBAL_ROOT_METHOD_ID = -2l;
    public final static String GLOBAL_ROOT_METHOD_SIGNATURE = "~ ROOT ~.()";
    //-1 is reserved for placeholder unclassifiable roor method of all incomplete stack-traces
    public final static long UNCLASSIFIABLE_ROOT_METHOD_ID = -1l;
    public final static String UNCLASSIFIABLE_ROOT_METHOD_SIGNATURE = "~ UNCLASSIFIABLE ~.()";

    //Counter to generate method ids in auto increment fashion
    //Starts with 0
    private AtomicLong counter = new AtomicLong(0);
    private ConcurrentHashMap<String, Long> lookup = new ConcurrentHashMap<>();

    public MethodIdLookup() {
        lookup.put(GLOBAL_ROOT_METHOD_SIGNATURE, GLOBAL_ROOT_METHOD_ID);
        lookup.put(UNCLASSIFIABLE_ROOT_METHOD_SIGNATURE, UNCLASSIFIABLE_ROOT_METHOD_ID);
    }

    public Long getOrAdd(String methodSignature) {
        return lookup.computeIfAbsent(methodSignature, (key -> counter.incrementAndGet()));
    }
}
