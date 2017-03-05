package fk.prof.backend.util.proto;

import fk.prof.backend.proto.BackendDTO;
import recording.Recorder;

public class RecorderProtoUtil {

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

  public static Recorder.Work translateWorkFromBackendDTO(BackendDTO.Work backendDTOWork) {
    return Recorder.Work.newBuilder()
        .setWType(translateWorkTypeFromBackendDTO(backendDTOWork.getWType()))
        .setCpuSample(translateCpuSampleWorkFromBackendDTO(backendDTOWork.getCpuSample()))
        .setThdSample(translateThreadSampleWorkFromBackendDTO(backendDTOWork.getThdSample()))
        .setMonitorBlock(translateMonitorContentionWorkFromBackendDTO(backendDTOWork.getMonitorBlock()))
        .setMonitorWait(translateMonitorWaitWorkFromBackendDTO(backendDTOWork.getMonitorWait()))
        .build();
  }

  private static Recorder.WorkType translateWorkTypeFromBackendDTO(BackendDTO.WorkType backendDTOWorkType) {
    return Recorder.WorkType.forNumber(backendDTOWorkType.getNumber());
  }

  private static Recorder.CpuSampleWork translateCpuSampleWorkFromBackendDTO(BackendDTO.CpuSampleWork backendDTOCpuSampleWork) {
    if(backendDTOCpuSampleWork == null) {
      return null;
    }
    return Recorder.CpuSampleWork.newBuilder()
        .setFrequency(backendDTOCpuSampleWork.getFrequency())
        .setMaxFrames(backendDTOCpuSampleWork.getMaxFrames())
        .build();
  }

  private static Recorder.ThreadSampleWork translateThreadSampleWorkFromBackendDTO(BackendDTO.ThreadSampleWork backendDTOThreadSampleWork) {
    if(backendDTOThreadSampleWork == null) {
      return null;
    }
    return Recorder.ThreadSampleWork.newBuilder()
        .setFrequency(backendDTOThreadSampleWork.getFrequency())
        .setMaxFrames(backendDTOThreadSampleWork.getMaxFrames())
        .build();
  }

  private static Recorder.MonitorContentionWork translateMonitorContentionWorkFromBackendDTO(BackendDTO.MonitorContentionWork backendDTOMonitorContentionWork) {
    if(backendDTOMonitorContentionWork == null) {
      return null;
    }
    return Recorder.MonitorContentionWork.newBuilder()
        .setMaxMonitors(backendDTOMonitorContentionWork.getMaxMonitors())
        .setMaxFrames(backendDTOMonitorContentionWork.getMaxFrames())
        .build();
  }

  private static Recorder.MonitorWaitWork translateMonitorWaitWorkFromBackendDTO(BackendDTO.MonitorWaitWork backendDTOMonitorWaitWork) {
    if(backendDTOMonitorWaitWork == null) {
      return null;
    }
    return Recorder.MonitorWaitWork.newBuilder()
        .setMaxMonitors(backendDTOMonitorWaitWork.getMaxMonitors())
        .setMaxFrames(backendDTOMonitorWaitWork.getMaxFrames())
        .build();
  }
}
