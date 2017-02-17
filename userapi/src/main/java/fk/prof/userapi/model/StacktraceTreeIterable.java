package fk.prof.userapi.model;

import fk.prof.aggregation.proto.AggregatedProfileModel;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * @author gaurav.ashok
 */
public class StacktraceTreeIterable implements Iterable<AggregatedProfileModel.FrameNode> {

    private List<AggregatedProfileModel.FrameNodeList> stackTraceParts;

    public StacktraceTreeIterable(List<AggregatedProfileModel.FrameNodeList> stackTraceParts) {
        this.stackTraceParts = stackTraceParts;
    }

    @Override
    public Iterator<AggregatedProfileModel.FrameNode> iterator() {
        return new FrameNodeIterator();
    }

    class FrameNodeIterator implements Iterator<AggregatedProfileModel.FrameNode> {

        int partIndex = 0;
        int offsetWithinPart = 0;

        @Override
        public boolean hasNext() {
            return partIndex < stackTraceParts.size() && offsetWithinPart < stackTraceParts.get(partIndex).getFrameNodesCount();
        }

        @Override
        public AggregatedProfileModel.FrameNode next() {
            if(!hasNext()) {
                throw new NoSuchElementException();
            }

            AggregatedProfileModel.FrameNode result = stackTraceParts.get(partIndex).getFrameNodes(offsetWithinPart);

            ++offsetWithinPart;
            if(offsetWithinPart >= stackTraceParts.get(partIndex).getFrameNodesCount()) {
                offsetWithinPart = 0;
                ++partIndex;
            }

            return result;
        }
    }
}
