package fk.prof.aggregation.model;

import fk.prof.aggregation.proto.AggregatedProfileModel.*;
import fk.prof.aggregation.stacktrace.StacktraceFrameNode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class FinalizedCpuSamplingAggregationBucket {
  protected final MethodIdLookup methodIdLookup;
  protected final Map<String, CpuSamplingTraceDetail> traceDetailLookup;

  public FinalizedCpuSamplingAggregationBucket(MethodIdLookup methodIdLookup, Map<String, CpuSamplingTraceDetail> traceDetailLookup) {
    this.methodIdLookup = methodIdLookup;
    this.traceDetailLookup = traceDetailLookup;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FinalizedCpuSamplingAggregationBucket)) {
      return false;
    }

    FinalizedCpuSamplingAggregationBucket other = (FinalizedCpuSamplingAggregationBucket) o;
    return this.methodIdLookup.equals(other.methodIdLookup)
        && this.traceDetailLookup.equals(other.traceDetailLookup);
  }

  protected TraceCtxList buildTraceCtxList() {
    TraceCtxList.Builder builder = TraceCtxList.newBuilder();
    for(String trace: traceDetailLookup.keySet()) {
      builder.addAllTraceCtx(TraceCtxDetail.newBuilder()
          .setName(trace).setSampleCount(traceDetailLookup.get(trace).getSampleCount()));
    }
    return builder.build();
  }

  /**
   * Serializes the stacktrace tree in a dfs order. It serializes the tree in batches of fixed size, reusing the memory
   * allocated for temporary data structures in subsequent batches.
   */
  protected static class NodeVisitor implements StacktraceFrameNode.NodeVisitor<CpuSamplingFrameNode> {
    private OutputStream out;
    private int batchSize;
    private FrameNodeList.Builder builder = FrameNodeList.newBuilder();

    public NodeVisitor(OutputStream out, int batchSize, int traceCtxId) {
      this.out = out;
      this.batchSize = batchSize;
      this.builder.setTraceCtxIdx(traceCtxId);
    }

    @Override
    public void visit(CpuSamplingFrameNode node) throws IOException {
      if(builder.getFrameNodesCount() >= batchSize) {
        builder.build().writeDelimitedTo(out);

        // clear this batch of nodes
        builder.clearFrameNodes();
      }
      builder.addFrameNodes(node.buildFrameNode());
    }

    protected void end() throws IOException {
      if(builder.getFrameNodesCount() > 0) {
        builder.build().writeDelimitedTo(out);
      }
    }
  }
}
