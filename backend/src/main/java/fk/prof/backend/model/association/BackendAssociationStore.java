package fk.prof.backend.model.association;

import io.vertx.core.Future;
import recording.Recorder;

import java.util.Set;

public interface BackendAssociationStore {
  Future<Set<Recorder.ProcessGroup>> reportBackendLoad(String backendIPAddress, double loadFactor);
  Future<String> getAssociatedBackend(Recorder.ProcessGroup processGroup);
}
