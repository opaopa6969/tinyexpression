package org.unlaxer.tinyexpression.evaluator.javacode.validator;

/** Indicates a specific error or validation failure encountered during parsing */
public class ExpressionValidationException extends RuntimeException {

  public ExpressionValidationException(String message) {
    super(message);
  }

  public ExpressionValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
