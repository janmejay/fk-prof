package fk.prof.backend.exception;

public class AssociationException extends RuntimeException {
  public AssociationException() {
    super();
  }

  public AssociationException(String message) {
    super(message);
  }

  public AssociationException(Throwable cause) {
    super(cause);
  }
}
