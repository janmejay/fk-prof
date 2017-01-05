package fk.prof.aggregation.stacktrace.cpusampling;

import fk.prof.aggregation.stacktrace.MethodIdLookup;

import java.util.concurrent.atomic.AtomicInteger;

public class CpuSamplingTraceDetail {
    private final AtomicInteger samples = new AtomicInteger(0);
    private CpuSamplingFrameNode globalRoot = null;
    private CpuSamplingFrameNode unclassifiableRoot = null;

    public CpuSamplingTraceDetail() {
        this.globalRoot = new CpuSamplingFrameNode(MethodIdLookup.GLOBAL_ROOT_METHOD_ID, 0);
        this.unclassifiableRoot = this.globalRoot.getOrAddChild(MethodIdLookup.UNCLASSIFIABLE_ROOT_METHOD_ID, 0);
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

    public int getSamples() {
        return samples.get();
    }
}
