package org.unlaxer.tinyexpression.evaluator.javacode;


import org.unlaxer.Token;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.TokenBaseOperator;

public class DummyCalculator2 implements TokenBaseOperator<CalculationContext> {

	@Override
	public Object evaluate(CalculationContext calculateContext, Token token) {
		
		java.util.Optional<WhiteListSetter> function1 = calculateContext.getObject(
				org.unlaxer.tinyexpression.evaluator.javacode.WhiteListSetter.class);
		
		float answer =(float)

				(((((calculateContext.getValue("number_accessCountByIPAddressInShortPeriod").orElse(0f) >= 15.0)
						|| (calculateContext.getValue("number_accessCountByCaulisCookieInShortPeriod")
								.orElse(0f) >= 10.0))
						|| (calculateContext.getValue("number_accessCountByIPAddressInMiddlePeriod")
								.orElse(0f) >= 60.0))
						|| (calculateContext.getValue("number_accessCountByCaulisCookieInMiddlePeriod")
								.orElse(0f) >= 30.0)) ? 
							function1.map(_function->_function.setWhiteList(calculateContext, 1.0f)).orElse(1.0f)	: 
							0.0);
		return answer;
	}
}