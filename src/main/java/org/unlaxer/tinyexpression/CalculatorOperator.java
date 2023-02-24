package org.unlaxer.tinyexpression;

import java.util.function.BiFunction;

public interface CalculatorOperator<C, S, T> extends BiFunction<C, S, T>{

	public default T evaluate(C context , S source) {
		return apply(context , source);
	}
	
}
