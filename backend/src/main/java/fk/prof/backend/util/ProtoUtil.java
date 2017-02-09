package fk.prof.backend.util;

import fk.prof.aggregation.proto.AggregatedProfileModel;
import recording.Recorder;

public class ProtoUtil {
  public static AggregatedProfileModel.WorkType mapWorkType(Recorder.WorkType recorderWorkType) {
    return AggregatedProfileModel.WorkType.forNumber(recorderWorkType.getNumber());
  }

  public static String processGroupCompactRepr(Recorder.ProcessGroup processGroup) {
    return String.format("%s,%s,%s", processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName());
  }
}
