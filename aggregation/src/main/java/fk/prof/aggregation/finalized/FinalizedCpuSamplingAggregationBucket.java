package fk.prof.aggregation.finalized;

import fk.prof.aggregation.MethodIdLookup;
import fk.prof.aggregation.SerializableAggregationEntity;
import fk.prof.aggregation.cpusampling.CpuSamplingTraceDetail;

import java.util.Map;

public class FinalizedCpuSamplingAggregationBucket implements SerializableAggregationEntity {
  private final MethodIdLookup methodIdLookup;
  private final Map<String, CpuSamplingTraceDetail> traceDetailLookup;

  public FinalizedCpuSamplingAggregationBucket(MethodIdLookup methodIdLookup, Map<String, CpuSamplingTraceDetail> traceDetailLookup) {
    this.methodIdLookup = methodIdLookup;
    this.traceDetailLookup = traceDetailLookup;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FinalizedCpuSamplingAggregationBucket)) {
      return false;
    }

    FinalizedCpuSamplingAggregationBucket other = (FinalizedCpuSamplingAggregationBucket) o;
    return this.methodIdLookup.equals(other.methodIdLookup)
        && this.traceDetailLookup.equals(other.traceDetailLookup);
  }
}
