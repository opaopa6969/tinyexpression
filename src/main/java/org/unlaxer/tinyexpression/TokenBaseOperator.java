package org.unlaxer.tinyexpression;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;

public interface TokenBaseOperator<C,T>  extends CalculatorOperator<C, TinyExpressionTokens, T>{

	public T evaluate(C context , TinyExpressionTokens token);
	
	@Override
	public default T apply(C context , TinyExpressionTokens token) {
		return evaluate(context, token);
	}
	
}
