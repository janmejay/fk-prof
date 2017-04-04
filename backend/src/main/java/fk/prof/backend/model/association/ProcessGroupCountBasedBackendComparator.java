package fk.prof.backend.model.association;

import fk.prof.backend.util.proto.RecorderProtoUtil;

import java.util.Comparator;

//Note: this comparator imposes orderings that are inconsistent with equals.
public class ProcessGroupCountBasedBackendComparator implements Comparator<BackendDetail> {

  @Override
  public int compare(BackendDetail b1, BackendDetail b2) {
    int b1PGScore = b1.getAssociatedProcessGroups().size();
    int b2PGScore = b2.getAssociatedProcessGroups().size();
    if (b1PGScore == b2PGScore) {
      String b1StringRepr = RecorderProtoUtil.assignedBackendCompactRepr(b1.getBackend());
      String b2StringRepr = RecorderProtoUtil.assignedBackendCompactRepr(b2.getBackend());
      return b1StringRepr.compareTo(b2StringRepr);
    } else {
      return b1PGScore - b2PGScore;
    }
  }

}
