package org.unlaxer.tinyexpression;

@SuppressWarnings("serial")
public class CalculationException extends RuntimeException {

  public CalculationException() {
    super();
  }

  public CalculationException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public CalculationException(String message, Throwable cause) {
    super(message, cause);
  }

  public CalculationException(String message) {
    super(message);
  }

  public CalculationException(Throwable cause) {
    super(cause);
  }
}