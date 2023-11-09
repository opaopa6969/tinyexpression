package org.unlaxer.tinyexpression;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.unlaxer.Parsed;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;

public interface Calculator<T> {
  
  public default CalculateResult calculate(CalculationContext calculateContext, String formula) {
    return calculate(calculateContext, StringSource.createRootSource(formula));
  }
  
  public default CalculateResult calculate(CalculationContext calculateContext, Source formula) {
    ParseContext parseContext = new ParseContext(formula);
    Parsed parsed = getParser().parse(parseContext);
    try{
      Token rootToken = tokenReduer().apply(parsed.getRootToken(true));
      T answer = getCalculatorOperator().evaluate(calculateContext,rootToken);
        
      return new CalculateResult(parseContext , parsed, Optional.of(toBigDecimal(answer)),rootToken);
      
    }catch (Exception e) {
      Errors errors = new Errors(e);
      return new CalculateResult(parseContext , parsed, Optional.empty() , errors,null);
    }finally{
      parseContext.close();
    }
  }
  
  public Parser getParser();
  
  public TokenBaseOperator<CalculationContext ,T> getCalculatorOperator();
  
  public BigDecimal toBigDecimal(T value);
  
  public float toFloat(T value);
  
  public default UnaryOperator<Token> tokenReduer(){
    return UnaryOperator.identity();
  }

  public String javaCode();
  
  public String formula();

  public byte[] byteCode();
  
  public String formulaHash();
  
  public String byteCodeHash();
  
  public Float apply(CalculationContext calculationContext);
  
  public void setObject(String key , Object object);
  
  public <X> X getObject(String key , Class<X> objectClass);
  
  public default <X> Optional<X> getObjectAsOptional(String key , Class<X> objectClass){
    return Optional.ofNullable(getObject(key, objectClass));
  }
  
  public static class CalculationException extends RuntimeException{

    public CalculationException() {
      super();
    }

    public CalculationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
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
