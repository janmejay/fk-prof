package fk.prof.backend.exception;

public class BadRequestException extends IllegalArgumentException {

  public BadRequestException() {
    super();
  }

  public BadRequestException(String message) {
    super(message);
  }

  public BadRequestException(Throwable throwable) {
    super(throwable);
  }

  public BadRequestException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
