package fk.prof.backend;

import fk.prof.aggregation.proto.AggregatedProfileModel;
import recording.Recorder;

public class ProtoUtil {
  public static AggregatedProfileModel.WorkType mapWorkType(Recorder.WorkType recorderWorkType) {
    return AggregatedProfileModel.WorkType.forNumber(recorderWorkType.getNumber());
  }
}
