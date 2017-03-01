package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import recording.Recorder;

public class RecorderIdentifier {
  private final String ipAddress;
  private final String procName;

  private RecorderIdentifier(String ipAddress, String procName) {
    this.ipAddress = Preconditions.checkNotNull(ipAddress);
    this.procName = Preconditions.checkNotNull(procName);
  }

  public static RecorderIdentifier from(Recorder.RecorderInfo recorderInfo) {
    return new RecorderIdentifier(recorderInfo.getIp(), recorderInfo.getProcName());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof RecorderIdentifier)) {
      return false;
    }

    RecorderIdentifier other = (RecorderIdentifier) o;
    return this.ipAddress.equals(other.ipAddress) && this.procName.equals(other.procName);
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + this.ipAddress.hashCode();
    result = result * PRIME + this.procName.hashCode();
    return result;
  }
}
