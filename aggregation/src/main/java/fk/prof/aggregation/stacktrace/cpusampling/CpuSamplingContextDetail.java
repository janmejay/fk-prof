package fk.prof.aggregation.stacktrace.cpusampling;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aggregated cpu samples for a context.
 */
public class CpuSamplingContextDetail {

    /**
     * Stacktrace tree originating from Thread.run()
     */
    private CpuSamplingFrameNode threadRunRoot = null;

    /**
     * Stacktrace which are not originated from Thread.run().
     */
    private CpuSamplingFrameNode unclassifiableRoot = null;

    /**
     * Total samples collected
     */
    private final AtomicInteger samplesCount = new AtomicInteger(0);

    public CpuSamplingFrameNode getThreadRunRoot() {
        return this.threadRunRoot;
    }

    public void setThreadRunRoot(CpuSamplingFrameNode threadRunRoot) {
        this.threadRunRoot = threadRunRoot;
    }

    public CpuSamplingFrameNode getUnclassifiableRoot() {
        return this.unclassifiableRoot;
    }

    public void setUnclassifiableRoot(CpuSamplingFrameNode unclassifiableRoot) {
        this.unclassifiableRoot = unclassifiableRoot;
    }

    public int getSamplesCount() {
        return samplesCount.get();
    }
}
