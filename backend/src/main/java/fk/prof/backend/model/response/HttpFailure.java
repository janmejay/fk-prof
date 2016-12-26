package fk.prof.backend.model.response;

public class HttpFailure extends RuntimeException {
  private int statusCode;

  public HttpFailure() {
    super();
    statusCode = 500;
  }

  public HttpFailure(String message) {
    super(message);
    statusCode = 500;
  }

  public HttpFailure(Throwable throwable) {
    super(throwable);
    statusCode = 500;
  }

  public HttpFailure(int failureCode) {
    statusCode = failureCode;
    initCause(new RuntimeException());
  }

  public HttpFailure(String message, Throwable throwable) {
    super(message, throwable);
    statusCode = 500;
  }

  public HttpFailure(String message, int failureCode) {
    super(message);
    statusCode = failureCode;
  }

  public HttpFailure(Throwable throwable, int failureCode) {
    super(throwable);
    statusCode = failureCode;
  }

  public HttpFailure(String message, Throwable throwable, int failureCode) {
    super(message, throwable);
    statusCode = failureCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public static HttpFailure failure(Throwable throwable) {
    if (throwable instanceof HttpFailure) {
      return (HttpFailure) throwable;
    }
    if (throwable.getMessage() == null) {
      return new HttpFailure("No message provided", throwable.getCause());
    }
    return new HttpFailure(throwable.getMessage(), throwable.getCause());
  }
}
