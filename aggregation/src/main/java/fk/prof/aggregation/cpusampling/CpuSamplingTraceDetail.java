package fk.prof.aggregation.cpusampling;

import fk.prof.aggregation.MethodIdLookup;
import fk.prof.aggregation.SerializableAggregationEntity;

import java.util.concurrent.atomic.AtomicInteger;

public class CpuSamplingTraceDetail implements SerializableAggregationEntity {
  private final AtomicInteger samples = new AtomicInteger(0);
  private final CpuSamplingFrameNode globalRoot;
  private final CpuSamplingFrameNode unclassifiableRoot;

  public CpuSamplingTraceDetail() {
    this.globalRoot = new CpuSamplingFrameNode(MethodIdLookup.GLOBAL_ROOT_METHOD_ID, MethodIdLookup.DEFAULT_LINE_NUMBER);
    this.unclassifiableRoot = this.globalRoot.getOrAddChild(MethodIdLookup.UNCLASSIFIABLE_ROOT_METHOD_ID, MethodIdLookup.DEFAULT_LINE_NUMBER);
  }

  public CpuSamplingFrameNode getGlobalRoot() {
    return this.globalRoot;
  }

  public CpuSamplingFrameNode getUnclassifiableRoot() {
    return this.unclassifiableRoot;
  }

  public void incrementSamples() {
    this.samples.incrementAndGet();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CpuSamplingTraceDetail)) {
      return false;
    }

    CpuSamplingTraceDetail other = (CpuSamplingTraceDetail) o;
    return this.samples.get() == other.samples.get()
        && this.globalRoot.equals(other.globalRoot);
  }
}
