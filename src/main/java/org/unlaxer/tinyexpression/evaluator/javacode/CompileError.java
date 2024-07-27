package org.unlaxer.tinyexpression.evaluator.javacode;

public class CompileError extends RuntimeException {

  public CompileError() {
    super();
  }

  public CompileError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public CompileError(String message, Throwable cause) {
    super(message, cause);
  }

  public CompileError(String message) {
    super(message);
  }

  public CompileError(Throwable cause) {
    super(cause);
  }
}