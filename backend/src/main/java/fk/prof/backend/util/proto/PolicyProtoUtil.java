package fk.prof.backend.util.proto;

import fk.prof.backend.proto.PolicyDTO;

/**
 * Utility methods for policy proto
 * Created by rohit.patiyal on 22/05/17.
 */
public class PolicyProtoUtil {
    public static String policyDetailsCompactRepr(PolicyDTO.PolicyDetails policyDetails) {
        return String.format("modAt=%s,creatAt=%s,creatBy=%s", policyDetails.getModifiedAt(), policyDetails.getModifiedBy(), policyDetails.getCreatedAt());
    }
}
