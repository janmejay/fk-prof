package fk.prof.backend.model.association;

import fk.prof.backend.proto.BackendDTO;
import io.vertx.core.Future;

import java.util.Set;

public interface BackendAssociationStore {
  Future<Set<BackendDTO.ProcessGroup>> reportBackendLoad(String backendIPAddress, double loadFactor);
  Future<String> getAssociatedBackend(BackendDTO.ProcessGroup processGroup);
}
