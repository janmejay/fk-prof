package fk.prof.backend.model.association;

import java.util.Comparator;

//Note: this comparator imposes orderings that are inconsistent with equals.
public class ProcessGroupCountBasedBackendComparator implements Comparator<BackendDetail> {

  @Override
  public int compare(BackendDetail b1, BackendDetail b2) {
    int b1PGScore = b1.getAssociatedProcessGroups().size();
    int b2PGScore = b2.getAssociatedProcessGroups().size();
    if (b1PGScore == b2PGScore) {
      String b1IP = b1.getBackendIPAddress();
      String b2IP = b2.getBackendIPAddress();
      return b1IP.compareTo(b2IP);
    } else {
      return b1PGScore - b2PGScore;
    }
  }

}
