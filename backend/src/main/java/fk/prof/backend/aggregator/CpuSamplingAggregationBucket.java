package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import fk.prof.common.stacktrace.MethodIdLookup;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingContextDetail;
import recording.Recorder;

import java.util.concurrent.ConcurrentHashMap;

public class CpuSamplingAggregationBucket {
  private final MethodIdLookup methodIdLookup = new MethodIdLookup();
  private final ConcurrentHashMap<String, CpuSamplingContextDetail> contextLookup = new ConcurrentHashMap<>();

  /**
   * Aggregates stack samples in the bucket. Throws {@link AggregationFailure} if aggregation fails
   * @param stackSampleWse
   */
  public void aggregate(Recorder.StackSampleWse stackSampleWse) throws AggregationFailure {

  }
}
