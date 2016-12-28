package fk.prof.aggregation.stacktrace.cpusampling;

import fk.prof.aggregation.stacktrace.StacktraceFrameNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  Frame node representing a node in a stacktrace tree. Contains methodInfo, and cpu samples info.
 */
public class CpuSamplingFrameNode implements StacktraceFrameNode<CpuSamplingFrameNode> {
    /**
     * A methodId reference from methodId lookup table
     */
    private final int methodId;

    /**
     * Line number from where it was called.
     */
    private final int lineNumber;

    /**
     * List of callees which this method called.
     */
    private final List<CpuSamplingFrameNode> callees = new ArrayList<>(2);

    /**
     *  Total number of samples that this method was encountered in stacktrace.
     */
    private final AtomicInteger onStackSamples = new AtomicInteger(0);

    /**
     * Total number of samples that this method was found running on cpu.
     */
    private final AtomicInteger onCpuSamples = new AtomicInteger(0);

    public CpuSamplingFrameNode(int methodId, int lineNumber) {
        this.methodId = methodId;
        this.lineNumber = lineNumber;
    }

    public CpuSamplingFrameNode getOrAddChild(int calleeMethodId, int lineNumber) {
        synchronized (callees) {
            CpuSamplingFrameNode result = null;
            for(CpuSamplingFrameNode child : callees) {
                if (child.methodId == calleeMethodId && child.lineNumber == lineNumber) {
                    result = child;
                    break;
                }
            }

            if (result == null) {
                result = new CpuSamplingFrameNode(calleeMethodId, lineNumber);
                callees.add(result);
            }

            return result;
        }
    }

    public int getMethodId() {
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

    @Override
    public int calleeCount() {
        return callees.size();
    }

    @Override
    public Iterable<CpuSamplingFrameNode> callees() {
        return callees;
    }
}
