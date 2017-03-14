package fk.prof.backend.exception;

public class WorkSlotException extends RuntimeException implements ProfException {
  private boolean serverFailure = false;

  public WorkSlotException() {
    super();
  }

  public WorkSlotException(String message) {
    super(message);
  }

  public WorkSlotException(Throwable throwable) {
    super(throwable);
  }

  public WorkSlotException(boolean serverFailure) {
    this.serverFailure = serverFailure;
    initCause(new RuntimeException());
  }

  public WorkSlotException(String message, Throwable throwable) {
    super(message, throwable);
  }

  public WorkSlotException(String message, boolean serverFailure) {
    super(message);
    this.serverFailure = serverFailure;
  }

  public WorkSlotException(Throwable throwable, boolean serverFailure) {
    super(throwable);
    this.serverFailure = serverFailure;
  }

  public WorkSlotException(String message, Throwable throwable, boolean serverFailure) {
    super(message, throwable);
    this.serverFailure = serverFailure;
  }

  @Override
  public boolean isServerFailure() {
    return serverFailure;
  }

  public static WorkSlotException failure(Throwable throwable) {
    if (throwable instanceof WorkSlotException) {
      return (WorkSlotException) throwable;
    }
    if (throwable.getMessage() == null) {
      return new WorkSlotException("No message provided", throwable.getCause());
    }
    return new WorkSlotException(throwable.getMessage(), throwable.getCause());
  }
}
