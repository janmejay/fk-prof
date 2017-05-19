package fk.prof.backend.model.policy;

import fk.prof.backend.proto.PolicyDTO;
import io.vertx.core.Future;
import recording.Recorder;

/**
 * Interface for accessing the store containing policy information
 * Created by rohit.patiyal on 18/05/17.
 */
public interface PolicyStoreAPI {
    PolicyDTO.PolicyDetails getPolicy(Recorder.ProcessGroup processGroup);

    Future<Void> createPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails policyDetails);

    Future<Void> updatePolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails policyDetails);
}
