package fk.prof.backend.model.profile;

import com.koloboke.collect.map.hash.HashIntObjMap;
import com.koloboke.collect.map.hash.HashIntObjMaps;
import com.koloboke.collect.map.hash.HashLongObjMap;
import com.koloboke.collect.map.hash.HashLongObjMaps;
import recording.Recorder;

import java.util.List;

public class RecordedProfileIndexes {
  private final HashLongObjMap<String> methodLookup = HashLongObjMaps.newUpdatableMap();
  private final HashIntObjMap<String> traceLookup = HashIntObjMaps.newUpdatableMap();

  public String getMethod(long methodId) {
    return methodLookup.get(methodId);
  }

  public String getTrace(int traceId) {
    return traceLookup.get(traceId);
  }

  public void update(Recorder.IndexedData indexedData) {
    updateMethodIndex(indexedData.getMethodInfoList());
    updateTraceIndex(indexedData.getTraceCtxList());
  }

  private void updateMethodIndex(List<Recorder.MethodInfo> methods) {
    if (methods != null) {
      for (Recorder.MethodInfo methodInfo : methods) {
        methodLookup.put(methodInfo.getMethodId(),
            methodInfo.getClassFqdn() + "#" + methodInfo.getMethodName() + " " + methodInfo.getSignature());
      }
    }
  }

  private void updateTraceIndex(List<Recorder.TraceContext> traces) {
    if (traces != null) {
      for (Recorder.TraceContext traceContext : traces) {
        traceLookup.put(traceContext.getTraceId(), traceContext.getTraceName());
      }
    }
  }

}
