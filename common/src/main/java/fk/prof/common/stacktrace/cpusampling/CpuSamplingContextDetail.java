package fk.prof.common.stacktrace.cpusampling;

public class CpuSamplingContextDetail {
    private CpuSamplingFrameNode threadRunRoot = null;
    private CpuSamplingFrameNode unclassifiableRoot = null;

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
}
