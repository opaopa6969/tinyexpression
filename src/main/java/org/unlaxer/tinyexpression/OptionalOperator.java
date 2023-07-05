package org.unlaxer.tinyexpression;

import java.util.Optional;

import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;

public interface OptionalOperator<C,T> extends TokenBaseOperator<C, T>{
	
	public Optional<T> evaluateOptional(C context , TinyExpressionTokens token);
	
	@Override
	default T evaluate(C context, TinyExpressionTokens token) {
		return evaluateOptional(context , token)
			.orElseGet(()->defaultValue(context, token));
	}
	
	public T defaultValue(C context, TinyExpressionTokens token);
}