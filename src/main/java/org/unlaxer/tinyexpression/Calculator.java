package org.unlaxer.tinyexpression;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.compiler.InstanceAndByteCode;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface Calculator {

  public default Type getReturningTypeFromImplements() {
    return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
  }

  public default Class<?> getReturningTypeClassFromImplements() {
    return (Class<?>) getReturningTypeFromImplements();
  }

  public ExpressionType resultType(); 
  
  public Parser getParser();

  public TokenBaseOperator<CalculationContext> getCalculatorOperator();

  public default UnaryOperator<Token> tokenReduer() {
    return UnaryOperator.identity();
  }
  
  public String returningTypeAsString();

  public String javaCode();

  public String formula();

  public byte[] byteCode();

  public String formulaHash();

  public String byteCodeHash();
  
  public List<Calculator> dependsOns();
  
  public Optional<Calculator> dependsOnBy();

  public default int dependsOnByNestLevel(){
    int nestLevel = 0;
    Calculator current = this;
    while(true) {
      if(current.dependsOnBy().isEmpty()) {
        break;
      }
      current = dependsOnBy().get();
      nestLevel++;
    }
    return nestLevel;
  }
  
  public default Calculator rootDependsOnBy(){
    Calculator current = this;
    while(true) {
      if(current.dependsOnBy().isEmpty()) {
        break;
      }
      current = dependsOnBy().get();
    }
    return current;
  }

  public void before(CalculationContext calculationContext);

  public Object apply(CalculationContext calculationContext);
  
  public void after(CalculationContext calculationContext);

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

  public CreatedFrom createdFrom();
  
  public enum CreatedFrom{
    formula,
    byteCode
  }

  public default void addDependsOn(Calculator dependsOncalculator) {
    dependsOns().add(dependsOncalculator);
    dependsOncalculator.setDependsOnBy(this);
  }
  
  public void setDependsOnBy(Calculator calculator);
  
  public default CalculateResult calculate(CalculationContext calculateContext,
      String formula , ExpressionType resultType) {
    ParseContext parseContext = new ParseContext(new StringSource(formula));
    Parsed parsed = getParser().parse(parseContext);
    try {
      Token rootToken = tokenReduer().apply(parsed.getRootToken(true));
      Object answer = getCalculatorOperator().evaluate(calculateContext, rootToken);

      return new CalculateResult(parseContext, parsed, Optional.of(answer), rootToken,resultType);

    } catch (Exception e) {
      Errors errors = new Errors(e);
      return new CalculateResult(parseContext, parsed, Optional.empty(), errors, null , resultType);
    } finally {
      parseContext.close();
    }
  }

  public default List<InstanceAndByteCode> instanceAndByteCodeList(){
    return List.of();
  }
}
