package fk.prof.backend.model.policy;

import fk.prof.backend.proto.PolicyDTO;
import io.vertx.core.Future;
import recording.Recorder;

/**
 * Interface for accessing the store containing policy information
 * Created by rohit.patiyal on 18/05/17.
 */
public interface PolicyStoreAPI {
    PolicyDTO.VersionedPolicyDetails getVersionedPolicy(Recorder.ProcessGroup processGroup);

    Future<Void> createVersionedPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.VersionedPolicyDetails versionedPolicyDetails);

    Future<Void> updateVersionedPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.VersionedPolicyDetails versionedPolicyDetails);
}
