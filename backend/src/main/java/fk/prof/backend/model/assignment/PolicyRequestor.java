package fk.prof.backend.model.assignment;

import com.codahale.metrics.Meter;
import fk.prof.backend.proto.BackendDTO;
import io.vertx.core.Future;
import recording.Recorder;

@FunctionalInterface
public interface PolicyRequestor {
  Future<BackendDTO.RecordingPolicy> get(Recorder.ProcessGroup processGroup, Meter mtrSuccess, Meter mtrFailure);
}
