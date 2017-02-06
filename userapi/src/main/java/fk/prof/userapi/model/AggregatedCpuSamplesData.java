package fk.prof.userapi.model;

/**
 * @author gaurav.ashok
 */
public class AggregatedCpuSamplesData implements AggregatedSamples {

    private StacktraceTreeIterable stacktraceTree;

    public AggregatedCpuSamplesData(StacktraceTreeIterable stacktraceTree) {
        this.stacktraceTree = stacktraceTree;
    }

    public StacktraceTreeIterable getFrameNodes() {
        return stacktraceTree;
    }
}
