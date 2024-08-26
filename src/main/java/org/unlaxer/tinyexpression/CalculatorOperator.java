package org.unlaxer.tinyexpression;

import java.util.function.BiFunction;

public interface CalculatorOperator<C, S> extends BiFunction<C, S , Object>{

	public default Object evaluate(C context , S source) {
		return apply(context , source);
	}
	
}
