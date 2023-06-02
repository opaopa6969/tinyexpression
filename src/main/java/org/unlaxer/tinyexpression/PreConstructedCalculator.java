package org.unlaxer.tinyexpression;

import java.util.Random;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;

public abstract class PreConstructedCalculator<T> implements Function<CalculationContext, Float> , Calculator<T>{
	
	public final String name;
	public final String formula;
	public final Token rootToken;
	
	public PreConstructedCalculator(String formula ) {
		this(formula , "_CalculatorClass"  + Math.abs(new Random().nextLong()));
	}
	
	public PreConstructedCalculator(String formula ,String name) {
		super();
		this.formula = formula;
		this.name = name;
		
		try(ParseContext parseContext = new ParseContext(new StringSource(formula));){
			Parsed parsed = getParser().parse(parseContext);
			if(false == parsed.isSucceeded()) {
				throw new IllegalArgumentException("failed to parse:"+formula);
			}
			rootToken = tokenReduer().apply(parsed.getRootToken(true));
		}catch (Exception e) {
		  e.printStackTrace();
			throw new IllegalArgumentException("failed to parse:"+formula,e);
		}
	}
	
	@SuppressWarnings("unused")
	private PreConstructedCalculator() {
		super();
		throw new IllegalArgumentException();
	}
	
	public UnaryOperator<Token> tokenReduer(){
		return UnaryOperator.identity();
	}

	
	@Override
	public Float apply(CalculationContext calculateContext) {
		return calculate(calculateContext);
	}

	public float calculate(CalculationContext calculateContext) {
		return toFloat(getCalculatorOperator().evaluate(calculateContext,rootToken));
	}
	
	@Override
	public String toString() {
//		String tokenPresentation = TokenPrinter.get(rootToken);
		return formula;
	}
}