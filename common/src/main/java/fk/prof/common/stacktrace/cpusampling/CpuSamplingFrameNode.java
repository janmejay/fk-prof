package fk.prof.common.stacktrace.cpusampling;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CpuSamplingFrameNode {
    private final long methodId;
    private final int lineNumber;
    private final List<CpuSamplingFrameNode> children = new ArrayList<>();

    private final AtomicInteger onStackSamples = new AtomicInteger(0);
    private final AtomicInteger onCpuSamples = new AtomicInteger(0);

    public CpuSamplingFrameNode(long methodId, int lineNumber) {
        this.methodId = methodId;
        this.lineNumber = lineNumber;
    }

    /**
     * Gets list of children of the node
     * Any operation on the returned value is not thread-safe and modifies the children list of node instance
     * @return gets backing array list of children
     */
    public List<CpuSamplingFrameNode> getChildrenUnsafe() {
        return this.children;
    }

    public CpuSamplingFrameNode getOrAddChild(long childMethodId, int childLineNumber) {
        synchronized (children) {
            CpuSamplingFrameNode result = null;
            Iterator<CpuSamplingFrameNode> i = children.iterator();
            while (i.hasNext()) {
                CpuSamplingFrameNode child = i.next();
                if (child.methodId == childMethodId && child.lineNumber == childLineNumber) {
                    result = child;
                    break;
                }
            }

            if (result == null) {
                result = new CpuSamplingFrameNode(childMethodId, childLineNumber);
                children.add(result);
            }

            return result;
        }
    }

    public long getMethodId() {
        return this.methodId;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }

    public int getOnStackSamples() {
        return this.onStackSamples.get();
    }

    public int incrementOnStackSamples () {
        return this.onStackSamples.incrementAndGet();
    }

    public int getOnCpuSamples() {
        return this.onCpuSamples.get();
    }

    public int incrementOnCpuSamples () {
        return this.onCpuSamples.incrementAndGet();
    }
}
