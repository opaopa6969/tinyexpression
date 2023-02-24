package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.function.Function;

import org.unlaxer.util.Either;

public class ExpressionOrLiteral extends Either<String, String> {

	private ExpressionOrLiteral(String raw, String mustWrap) {
		super(raw, mustWrap);
	}

	public static ExpressionOrLiteral literalOf(String literal) {
		if (literal == null) {
			throw new IllegalArgumentException("must be not null");
		}
		return new ExpressionOrLiteral(null, literal);
	}

	public static ExpressionOrLiteral expressionOf(String expresion) {
		if (expresion == null) {
			throw new IllegalArgumentException("must be not null");
		}
		return new ExpressionOrLiteral(expresion, null);
	}

	public String toString() {
		return apply(Function.identity(), word -> "\"" + word + "\"");
//		return apply(Function.identity(),Function.identity());
	}
}