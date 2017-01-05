package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.request.RecordedProfileIndexes;
import fk.prof.aggregation.stacktrace.MethodIdLookup;
import fk.prof.aggregation.stacktrace.cpusampling.CpuSamplingFrameNode;
import fk.prof.aggregation.stacktrace.cpusampling.CpuSamplingTraceDetail;
import recording.Recorder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CpuSamplingAggregationBucket {
  private final MethodIdLookup methodIdLookup = new MethodIdLookup();
  private final ConcurrentHashMap<String, CpuSamplingTraceDetail> traceDetailLookup = new ConcurrentHashMap<>();

  public Set<String> getAvailableTraces() {
    return traceDetailLookup.keySet();
  }

  public CpuSamplingTraceDetail getTraceDetail(String traceName) {
    return traceDetailLookup.get(traceName);
  }

  /**
   * Generates a unmodifiable view of method id -> method name lookup
   * This method should be called once the aggregation window, this map is member of, has expired.
   * Calling this method while window is in progress and receiving profiles for aggregation will result in incomplete method name lookup being generated
   *
   * @return returns methodId -> method name lookup map
   */
  public Map<Long, String> getMethodNameLookup() {
    return this.methodIdLookup.generateReverseLookup();
  }

  /**
   * Aggregates stack samples in the bucket. Throws {@link AggregationFailure} if aggregation fails
   *
   * @param stackSampleWse
   */
  public void aggregate(Recorder.StackSampleWse stackSampleWse, RecordedProfileIndexes indexes)
      throws AggregationFailure {

    for (Recorder.StackSample stackSample : stackSampleWse.getStackSampleList()) {
      //NOTE: As of now, only those samples are aggregated which are associated with a trace id
      if (stackSample.hasTraceId()) {
        String trace = indexes.getTrace(stackSample.getTraceId());
        if (trace == null) {
          throw new AggregationFailure("Unknown trace id encountered in stack sample, aborting aggregation of this profile");
        }
        CpuSamplingTraceDetail traceDetail = traceDetailLookup.computeIfAbsent(trace,
            key -> new CpuSamplingTraceDetail()
        );

        List<Recorder.Frame> frames = stackSample.getFrameList();
        if (frames != null && frames.size() > 0) {
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
            long methodId = methodIdLookup.getOrAdd(method);
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
  }
}
