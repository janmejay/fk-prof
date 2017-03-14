package fk.prof.backend.util.proto;

import fk.prof.backend.proto.BackendDTO;

public class BackendDTOProtoUtil {
  public static String recordingPolicyCompactRepr(BackendDTO.WorkProfile workProfile) {
    return String.format("dur=%d,cov=%d,desc=%s", workProfile.getDuration(), workProfile.getCoveragePct(), workProfile.getDescription());
  }
}
