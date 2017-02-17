package fk.prof.backend.aggregator;

import fk.prof.aggregation.FinalizableBuilder;
import fk.prof.aggregation.model.MethodIdLookup;
import fk.prof.aggregation.model.CpuSamplingFrameNode;
import fk.prof.aggregation.model.CpuSamplingTraceDetail;
import fk.prof.aggregation.model.FinalizedCpuSamplingAggregationBucket;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.profile.RecordedProfileIndexes;
import recording.Recorder;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CpuSamplingAggregationBucket extends FinalizableBuilder<FinalizedCpuSamplingAggregationBucket> {
  private final MethodIdLookup methodIdLookup = new MethodIdLookup();
  private final ConcurrentHashMap<String, CpuSamplingTraceDetail> traceDetailLookup = new ConcurrentHashMap<>();

  /**
   * Aggregates stack samples in the bucket. Throws {@link AggregationFailure} if aggregation fails
   *
   * @param stackSampleWse
   */
  public void aggregate(Recorder.StackSampleWse stackSampleWse, RecordedProfileIndexes indexes)
      throws AggregationFailure {

    for (Recorder.StackSample stackSample : stackSampleWse.getStackSampleList()) {
      String trace = indexes.getTrace(stackSample.getTraceId());
      if (trace == null) {
        throw new AggregationFailure("Unknown trace id encountered in stack sample, aborting aggregation of this profile");
      }
      CpuSamplingTraceDetail traceDetail = traceDetailLookup.computeIfAbsent(trace,
          key -> new CpuSamplingTraceDetail()
      );

      List<Recorder.Frame> frames = stackSample.getFrameList();
      if (frames.size() > 0) {
        boolean framesSnipped = stackSample.getSnipped();
        CpuSamplingFrameNode currentNode = framesSnipped ? traceDetail.getUnclassifiableRoot() : traceDetail.getGlobalRoot();
        traceDetail.incrementSamples();

        //callee -> caller ordering in frames, so iterating bottom up in the list to merge in existing tree in root->leaf fashion
        for (int i = frames.size() - 1; i >= 0; i--) {
          Recorder.Frame frame = frames.get(i);
          String method = indexes.getMethod(frame.getMethodId());
          if (method == null) {
            throw new AggregationFailure("Unknown method id encountered in stack sample, aborting aggregation of this profile");
          }
          int methodId = methodIdLookup.getOrAdd(method);
          currentNode = currentNode.getOrAddChild(methodId, frame.getLineNo());
          currentNode.incrementOnStackSamples();
          //The first frame is the on-cpu frame so incrementing on-cpu samples count
          if (i == 0) {
            currentNode.incrementOnCpuSamples();
          }
        }
      }
    }
  }

  @Override
  protected FinalizedCpuSamplingAggregationBucket buildFinalizedEntity() {
    return new FinalizedCpuSamplingAggregationBucket(
        methodIdLookup,
        traceDetailLookup
    );
  }
}
