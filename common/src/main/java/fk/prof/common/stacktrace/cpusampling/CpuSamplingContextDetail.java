package fk.prof.common.stacktrace.cpusampling;

import fk.prof.common.stacktrace.MethodIdLookup;

public class CpuSamplingContextDetail {
    private CpuSamplingFrameNode root = null;

    public CpuSamplingContextDetail() {
        this.root = new CpuSamplingFrameNode(MethodIdLookup.GLOBAL_ROOT_METHOD_ID, 0);
    }

    public CpuSamplingFrameNode getRoot() {
        return this.root;
    }
}
