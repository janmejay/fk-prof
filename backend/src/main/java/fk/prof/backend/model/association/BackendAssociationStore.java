package fk.prof.backend.model.association;

import fk.prof.backend.proto.BackendDTO;
import io.vertx.core.Future;

import java.util.Set;

public interface BackendAssociationStore {
  Future<BackendDTO.ProcessGroups> reportBackendLoad(String backendIPAddress, double loadFactor);
  Future<String> getAssociatedBackend(BackendDTO.ProcessGroup processGroup);
}
