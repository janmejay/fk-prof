package fk.prof.aggregation.stacktrace;

/**
 * Interface for a Stacktrace tree node giving it a tree like structure by exposing
 * callee count and list of callees.
 * @author gaurav.ashok
 */
public interface StacktraceFrameNode<T> {
    int calleeCount();
    Iterable<T> callees();
}
