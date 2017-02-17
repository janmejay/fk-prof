package fk.prof.backend.util;

import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.backend.proto.BackendDTO;
import recording.Recorder;

public class ProtoUtil {
  public static AggregatedProfileModel.WorkType mapRecorderWorkTypeToAggregatorWorkType(Recorder.WorkType recorderWorkType) {
    return AggregatedProfileModel.WorkType.forNumber(recorderWorkType.getNumber());
  }

  public static Recorder.ProcessGroup mapRecorderInfoToProcessGroup(Recorder.RecorderInfo recorderInfo) {
    return Recorder.ProcessGroup.newBuilder()
        .setAppId(recorderInfo.getAppId())
        .setCluster(recorderInfo.getCluster())
        .setProcName(recorderInfo.getProcName())
        .build();
  }

  public static String processGroupCompactRepr(Recorder.ProcessGroup processGroup) {
    return String.format("%s,%s,%s", processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName());
  }
}
