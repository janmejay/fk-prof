package fk.prof.common.stacktrace.cpusampling;

import fk.prof.common.stacktrace.MethodIdLookup;

public class CpuSamplingContextDetail {
    private CpuSamplingFrameNode globalRoot = null;
    private CpuSamplingFrameNode unclassifiableRoot = null;

    public CpuSamplingContextDetail() {
        this.globalRoot = new CpuSamplingFrameNode(MethodIdLookup.GLOBAL_ROOT_METHOD_ID, 0);
        this.unclassifiableRoot = this.globalRoot.getOrAddChild(MethodIdLookup.UNCLASSIFIABLE_ROOT_METHOD_ID, 0);
    }

    public CpuSamplingFrameNode getGlobalRoot() {
        return this.globalRoot;
    }

    public CpuSamplingFrameNode getUnclassifiableRoot() {
        return this.unclassifiableRoot;
    }
}
