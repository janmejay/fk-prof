package fk.prof.backend.exception;

public class InternalServerException extends IllegalStateException {

  public InternalServerException() {
    super();
  }

  public InternalServerException(String message) {
    super(message);
  }

  public InternalServerException(Throwable throwable) {
    super(throwable);
  }

  public InternalServerException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
