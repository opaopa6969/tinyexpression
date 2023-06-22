package org.unlaxer.tinyexpression;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;

public interface Calculator<T> {
	
	public default CalculateResult calculate(CalculationContext calculateContext, String formula) {
		ParseContext parseContext = new ParseContext(new StringSource(formula));
		Parsed parsed = getParser().parse(parseContext);
		try{
			Token rootToken = tokenReduer().apply(parsed.getRootToken(true));
			TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken);
			T answer = getCalculatorOperator().evaluate(calculateContext,tinyExpressionTokens);
				
			return new CalculateResult(parseContext , parsed, Optional.of(toBigDecimal(answer)),tinyExpressionTokens);
			
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
}
