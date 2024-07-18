package org.unlaxer.tinyexpression;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;

public interface Calculator<T> {

  public default Type getResultType() {
    return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
  }

  @SuppressWarnings("unchecked")
  public default Class<T> getTypeClass() {
    return (Class<T>) getResultType();
  }

  public Parser getParser();

  public TokenBaseOperator<CalculationContext, T> getCalculatorOperator();

  public default UnaryOperator<Token> tokenReduer() {
    return UnaryOperator.identity();
  }
  
  public String returningType();

  public String javaCode();

  public String formula();

  public byte[] byteCode();

  public String formulaHash();

  public String byteCodeHash();

  public T apply(CalculationContext calculationContext);

  public void setObject(String key, Object object);

  public <X> X getObject(String key, Class<X> objectClass);

  public default <X> Optional<X> getObjectAsOptional(String key, Class<X> objectClass) {
    return Optional.ofNullable(getObject(key, objectClass));
  }

  public static class CalculationException extends RuntimeException {

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
}
