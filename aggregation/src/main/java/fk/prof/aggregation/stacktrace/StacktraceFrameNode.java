package fk.prof.aggregation.stacktrace;

import java.util.Iterator;

/**
 * A generic traverser for a stacktrace tree.
 * @author gaurav.ashok
 */
public abstract class StacktraceFrameNode<T extends StacktraceFrameNode<T>> {

    protected abstract Iterable<T> children();

    public void traverse(NodeVisitor<T> visitor) throws Exception {
        new DFSTraversal(visitor).traverse(this);
    }

    private static class DFSTraversal<T extends StacktraceFrameNode<T>> {
        private NodeVisitor<T> visitor;
        public DFSTraversal(NodeVisitor<T> visitor) {
            this.visitor = visitor;
        }

        public void traverse(T node) throws Exception {
            visitor.visit(node);
            Iterator<T> children = node.children().iterator();
            while(children.hasNext()) {
                T child = children.next();
                traverse(child);
            }
        }
    }

    public interface NodeVisitor<T> {
        void visit(T obj) throws Exception;
    }
}
