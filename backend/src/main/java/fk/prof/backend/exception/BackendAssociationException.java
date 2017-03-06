package fk.prof.backend.exception;

public class BackendAssociationException extends RuntimeException implements ProfException {
  private boolean serverFailure = false;

  public BackendAssociationException() {
    super();
  }

  public BackendAssociationException(String message) {
    super(message);
  }

  public BackendAssociationException(Throwable throwable) {
    super(throwable);
  }

  public BackendAssociationException(boolean serverFailure) {
    this.serverFailure = serverFailure;
    initCause(new RuntimeException());
  }

  public BackendAssociationException(String message, Throwable throwable) {
    super(message, throwable);
  }

  public BackendAssociationException(String message, boolean serverFailure) {
    super(message);
    this.serverFailure = serverFailure;
  }

  public BackendAssociationException(Throwable throwable, boolean serverFailure) {
    super(throwable);
    this.serverFailure = serverFailure;
  }

  public BackendAssociationException(String message, Throwable throwable, boolean serverFailure) {
    super(message, throwable);
    this.serverFailure = serverFailure;
  }

  @Override
  public boolean isServerFailure() {
    return serverFailure;
  }

  public static BackendAssociationException failure(Throwable throwable) {
    if (throwable instanceof BackendAssociationException) {
      return (BackendAssociationException) throwable;
    }
    if (throwable.getMessage() == null) {
      return new BackendAssociationException("No message provided", throwable.getCause());
    }
    return new BackendAssociationException(throwable.getMessage(), throwable.getCause());
  }
}
