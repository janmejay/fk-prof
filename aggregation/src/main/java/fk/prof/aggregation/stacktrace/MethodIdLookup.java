package fk.prof.aggregation.stacktrace;

import com.koloboke.collect.map.hash.HashLongObjMaps;

import java.util.Collections;
import java.util.Map;
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
  //0 is reserved. No method is assigned 0. Counter starts with 1 during assignment
  private AtomicLong counter = new AtomicLong(0);
  private ConcurrentHashMap<String, Long> lookup = new ConcurrentHashMap<>();

  public MethodIdLookup() {
    lookup.put(GLOBAL_ROOT_METHOD_SIGNATURE, GLOBAL_ROOT_METHOD_ID);
    lookup.put(UNCLASSIFIABLE_ROOT_METHOD_SIGNATURE, UNCLASSIFIABLE_ROOT_METHOD_ID);
  }

  public Long getOrAdd(String methodSignature) {
    return lookup.computeIfAbsent(methodSignature, (key -> counter.incrementAndGet()));
  }

  /**
   * Generates a reverse lookup map (unmodifiable view) from methodId to method name
   * Usually a reverse lookup is modelled as Map[V, List[K]] but because the put semantics in this map using atomic long counter,
   * we are assured of a 1:1 relationship between K and V. Therefore, reverse lookup is modelled as Map[V, K]
   *
   * @return lookup from methodId - method name
   */
  public Map<Long, String> generateReverseLookup() {
    Map<Long, String> reverseLookup = HashLongObjMaps.newUpdatableMap();
    for (Map.Entry<String, Long> entry: lookup.entrySet()) {
      if (entry.getValue() != null) {
        reverseLookup.put(entry.getValue(), entry.getKey());
      }
    }

    return Collections.unmodifiableMap(reverseLookup);
  }
}
