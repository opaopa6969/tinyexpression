package org.unlaxer.tinyexpression;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public interface Calculator<T> {

  public default Type getReturningType() {
    return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
  }

  @SuppressWarnings("unchecked")
  public default Class<T> getReturningTypeClass() {
    return (Class<T>) getReturningType();
  }

  public Parser getParser();

  public TokenBaseOperator<CalculationContext, T> getCalculatorOperator();

  public default UnaryOperator<Token> tokenReduer() {
    return UnaryOperator.identity();
  }
  
  public String returningTypeAsString();

  public String javaCode();

  public String formula();

  public byte[] byteCode();

  public String formulaHash();

  public String byteCodeHash();
  
  public List<Calculator<?>> dependsOns();
  
  public Optional<Calculator<?>> dependsOnBy();

  public default int dependsOnByNestLevel(){
    int nestLevel = 0;
    Calculator<?> current = this;
    while(true) {
      if(current.dependsOnBy().isEmpty()) {
        break;
      }
      current = dependsOnBy().get();
      nestLevel++;
    }
    return nestLevel;
  }
  
  public default Calculator<?> rootDependsOnBy(){
    Calculator<?> current = this;
    while(true) {
      if(current.dependsOnBy().isEmpty()) {
        break;
      }
      current = dependsOnBy().get();
    }
    return current;
  }

  public T apply(CalculationContext calculationContext);

  public void setObject(String key, Object object);

  public <X> X getObject(String key, Class<X> objectClass);
  
  public default Optional<FormulaInfo> formulaInfo(){
    return Optional.of(getObject(FormulaInfo.class.getSimpleName(), FormulaInfo.class));
  }
  
  public default void setFormulaInfo(FormulaInfo formulaInfo){
    setObject(FormulaInfo.class.getSimpleName(), formulaInfo);
  }

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
  
  public CreatedFrom createdFrom();
  
  public enum CreatedFrom{
    formula,
    byteCode
  }

  public default void addDependsOn(Calculator<?> dependsOncalculator) {
    dependsOns().add(dependsOncalculator);
    dependsOncalculator.setDependsOnBy(this);
  }
  
  public void setDependsOnBy(Calculator<?> calculator);
}
