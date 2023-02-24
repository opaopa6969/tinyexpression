package org.unlaxer.tinyexpression.evaluator.javacode;
import org.unlaxer.Token;
import org.unlaxer.tinyexpression.TokenBaseOperator;

public class _CalculatorClass1819039006215327509 implements TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>{

	@Override
	public Float evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , Token token) {
		float answer = (float) 

(((false ==((calculateContext.getValue("userCountGroupedByCookieOnThisSite").orElse(0f)-calculateContext.getValue("userCountGroupedByCookieOnThisSiteOn12H").orElse(0f))==0.0))) ? 
1.0:
0.0)
		;
		return answer;
	}
}