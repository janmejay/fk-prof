package fk.prof.aggregation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodIdLookup implements SerializableAggregationEntity {
  //-2 is reserved for placeholder global root method of all stack-trace
  public final static int GLOBAL_ROOT_METHOD_ID = -2;
  public final static String GLOBAL_ROOT_METHOD_SIGNATURE = "~ ROOT ~.()";
  //-1 is reserved for placeholder unclassifiable roor method of all incomplete stack-traces
  public final static int UNCLASSIFIABLE_ROOT_METHOD_ID = -1;
  public final static String UNCLASSIFIABLE_ROOT_METHOD_SIGNATURE = "~ UNCLASSIFIABLE ~.()";
  public final static int DEFAULT_LINE_NUMBER = 0;

  //Counter to generate method ids in auto increment fashion
  private final AtomicInteger counter = new AtomicInteger(0);
  private final ConcurrentHashMap<String, Integer> lookup = new ConcurrentHashMap<>();

  public MethodIdLookup() {
    lookup.put(GLOBAL_ROOT_METHOD_SIGNATURE, GLOBAL_ROOT_METHOD_ID);
    lookup.put(UNCLASSIFIABLE_ROOT_METHOD_SIGNATURE, UNCLASSIFIABLE_ROOT_METHOD_ID);
  }

  public Integer getOrAdd(String methodSignature) {
    return lookup.computeIfAbsent(methodSignature, (key -> counter.getAndIncrement()));
  }

  /**
   * Generates a reverse lookup array where array index corresponds to methodId + 2
   * We are assured of a 1:1 relationship between K and V because of an atomic counter being used to generate sequential lookup values.
   * Therefore, reverse lookup is modelled as an array
   *
   * @return 2-indexed array where arr[idx] = method signature and idx-2 = corresponding method id
   */
  public String[] generateReverseLookup() {
    String[] reverseLookup = new String[counter.get() + 2];
    lookup.entrySet().forEach(entry -> reverseLookup[entry.getValue() + 2] = entry.getKey());
    return reverseLookup;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MethodIdLookup)) {
      return false;
    }

    MethodIdLookup other = (MethodIdLookup) o;
    return this.counter.get() == other.counter.get()
        && this.lookup.equals(other.lookup);
  }

}
