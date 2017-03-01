package fk.prof.backend.model.assignment;

public interface RecorderSupplier {
  public int getTargetRecordersCount();
  public RecorderDetail getNextRecorder();
}
