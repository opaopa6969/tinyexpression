package org.unlaxer.tinyexpression;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;

public interface Calculator<T> {
	
	public default CalculateResult calculateReturningDetails(CalculationContext calculateContext) {
    try{
      TinyExpressionTokens tinyExpressionTokens = tinyExpressionTokens();
      T answer = getCalculatorOperator().evaluate(calculateContext,tinyExpressionTokens);
        
      return new CalculateResult(parseContext() , parsed(), Optional.of(toBigDecimal(answer)),tinyExpressionTokens);
      
    }catch (Exception e) {
      Errors errors = new Errors(e);
      return new CalculateResult(parseContext() , parsed(), Optional.empty() , errors,null);
    }
  }
	
	public TinyExpressionTokens tinyExpressionTokens();
  public ParseContext parseContext();
  public Parsed parsed();
	
	public Parser getParser();
	
	public TokenBaseOperator<CalculationContext ,T> getCalculatorOperator();
	
	public BigDecimal toBigDecimal(T value);
	
	public float toFloat(T value);
	
	public default UnaryOperator<Token> tokenReduer(){
		return UnaryOperator.identity();
	}

	public String javaCode();	
	
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
