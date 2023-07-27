package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.TokenBaseOperator;

public class DummyCalculator implements TokenBaseOperator<CalculationContext, Float> {

	@Override
	public Float evaluate(CalculationContext calculateContext, Token token) {
		float answer =(float)

				(((((calculateContext.getValue("number_accessCountByIPAddressInShortPeriod").orElse(0f) >= 15.0)
						|| (calculateContext.getValue("number_accessCountByCaulisCookieInShortPeriod")
								.orElse(0f) >= 10.0))
						|| (calculateContext.getValue("number_accessCountByIPAddressInMiddlePeriod")
								.orElse(0f) >= 60.0))
						|| (calculateContext.getValue("number_accessCountByCaulisCookieInMiddlePeriod")
								.orElse(0f) >= 30.0)) ? 1.0 : 0.0);
		return answer;
	}

}