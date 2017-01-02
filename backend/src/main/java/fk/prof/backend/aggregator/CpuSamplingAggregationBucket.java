package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.request.RecordedProfileIndexes;
import fk.prof.common.stacktrace.MethodIdLookup;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingContextDetail;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingFrameNode;
import recording.Recorder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CpuSamplingAggregationBucket {
  private final MethodIdLookup methodIdLookup = new MethodIdLookup();
  private final ConcurrentHashMap<String, CpuSamplingContextDetail> contextLookup = new ConcurrentHashMap<>();

  public Set<String> getAvailableContexts() {
    return contextLookup.keySet();
  }

  public CpuSamplingContextDetail getContext(String traceName) {
    return contextLookup.get(traceName);
  }

  /**
   * Generates a method id -> method name lookup map
   * Thread safety of map implementation is not guaranteed
   * This method should be called once the aggregation window, this map is member of, has expired.
   * Calling this method while window is in progress and receiving profiles for aggregation will result in incomplete method name lookup being generated
   * @return returns methodId -> method name lookup map
   */
  public Map<Long, String> getMethodNameLookup() {
    return this.methodIdLookup.getReverseLookup();
  }

  /**
   * Aggregates stack samples in the bucket. Throws {@link AggregationFailure} if aggregation fails
   * @param stackSampleWse
   */
  public void aggregate(Recorder.StackSampleWse stackSampleWse, RecordedProfileIndexes indexes)
      throws AggregationFailure {

    for(Recorder.StackSample stackSample: stackSampleWse.getStackSampleList()) {
      if(stackSample.hasTraceId()) {
        String trace = indexes.getTrace(stackSample.getTraceId());
        CpuSamplingContextDetail contextDetail = contextLookup.computeIfAbsent(trace,
            key -> new CpuSamplingContextDetail()
        );

        List<Recorder.Frame> frames = stackSample.getFrameList();
        if(frames != null && frames.size() > 0) {

          //TODO: Read this flag from proto. Enhance proto to support this flag
          boolean framesSnipped = true;
          CpuSamplingFrameNode currentNode = framesSnipped ? contextDetail.getUnclassifiableRoot() : contextDetail.getGlobalRoot();

          //NOTE: callee -> caller ordering in frames, so iterating bottom up in the list
          for(int i = frames.size() - 1; i >= 0; i--) {
            Recorder.Frame frame = frames.get(i);
            String method = indexes.getMethod(frame.getMethodId());
            if(method == null) {
              throw new AggregationFailure("Unknown method id encountered in stack sample, aborting aggregation of this profile");
            }
            long methodId = methodIdLookup.getOrAdd(method);
            currentNode = currentNode.getOrAddChild(methodId, frame.getLineNo());
            currentNode.incrementOnStackSamples();
            //The first frame is the on-cpu frame so incrementing on-cpu samples count
            if(i == 0) {
              currentNode.incrementOnCpuSamples();
            }
          }
        }
      }
    }
  }
}
