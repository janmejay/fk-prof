package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.request.RecordedProfileIndexes;
import fk.prof.common.stacktrace.MethodIdLookup;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingContextDetail;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingFrameNode;
import recording.Recorder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CpuSamplingAggregationBucket {
  private final MethodIdLookup methodIdLookup = new MethodIdLookup();
  private final ConcurrentHashMap<String, CpuSamplingContextDetail> contextLookup = new ConcurrentHashMap<>();

  public CpuSamplingContextDetail getContext(String traceName) {
    return contextLookup.get(traceName);
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

        List<Recorder.Frame> frames = new ArrayList<>(stackSample.getFrameList());
        //NOTE: callee -> caller ordering in frames, so reversing for merging in the tree
        if(frames != null && frames.size() > 0) {
          Collections.reverse(frames);
          CpuSamplingFrameNode parentNode = null;
          for(Recorder.Frame frame: frames) {
            String method = indexes.getMethod(frame.getMethodId());
            if(method == null) {
              throw new AggregationFailure("Unknown method id encountered in stack sample, aborting aggregation of this profile");
            }

            if(parentNode == null) {
//              boolean isThreadRunRoot = method.equals("java.lang.Thread.run()");
//              parentNode = isThreadRunRoot ? contextDetail.getThreadRunRoot() : contextDetail.getUnclassifiableRoot();
            }
          }
        }
      }
    }
  }
}
