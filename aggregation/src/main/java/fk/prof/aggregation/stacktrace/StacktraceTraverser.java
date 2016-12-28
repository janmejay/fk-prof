package fk.prof.aggregation.stacktrace;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * A generic traverser for a stacktrace tree. The supplied consumer is called for every node in DFS order.
 * @author gaurav.ashok
 */
public class StacktraceTraverser<T extends StacktraceFrameNode<T>> {

    private Consumer<T> consumer;

    public StacktraceTraverser(Consumer<T> consumer) {
        this.consumer = consumer;
    }

    /**
     * node must not be null and must be ensured while building stacktrace tree.
     * @param node
     */
    public void traverse(T node) {
        consumer.accept(node);
        Iterator<T> children = node.callees().iterator();
        while(children.hasNext()) {
            T child = children.next();
            traverse(child);
        }
    }
}
