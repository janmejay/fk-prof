package fk.prof.backend.model.policy;

import fk.prof.backend.proto.BackendDTO;
import recording.Recorder;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO: Liable for refactoring. Dummy impl for now
 */
public class PolicyStore {
  private final Map<Recorder.ProcessGroup, BackendDTO.RecordingPolicy> store = new HashMap<>();

  public void put(Recorder.ProcessGroup processGroup, BackendDTO.RecordingPolicy recordingPolicy) {
    this.store.put(processGroup, recordingPolicy);
  }

  public BackendDTO.RecordingPolicy get(Recorder.ProcessGroup processGroup) {
    //TODO: Remove. Temporary for e2e testing
    if(processGroup.getAppId().startsWith("bar-app") && processGroup.getCluster().startsWith("quux-cluster") &&
        processGroup.getProcName().startsWith("grault-proc")) {
      return buildRecordingPolicy(30);
    }
    return this.store.get(processGroup);
  }

  //TODO: Ugly hack for e2e testing, remove!
  private BackendDTO.RecordingPolicy buildRecordingPolicy(int profileDuration) {
    return BackendDTO.RecordingPolicy.newBuilder()
        .setDuration(profileDuration)
        .setCoveragePct(100)
        .setDescription("Test work profile")
        .addWork(BackendDTO.Work.newBuilder()
            .setWType(BackendDTO.WorkType.cpu_sample_work)
            .setCpuSample(BackendDTO.CpuSampleWork.newBuilder().setFrequency(50).setMaxFrames(64))
            .build())
        .build();
  }
}
