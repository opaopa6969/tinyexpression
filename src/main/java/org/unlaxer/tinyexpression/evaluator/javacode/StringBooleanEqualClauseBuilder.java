package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.Token;

public class StringBooleanEqualClauseBuilder implements TokenCodeBuilder {

	public static final StringBooleanEqualClauseBuilder SINGLETON = new StringBooleanEqualClauseBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder , Token token , TinyExpressionTokens tinyExpressionTokens) {
		
		List<Token> filteredChildren = token.filteredChildren;
		
		ExpressionOrLiteral left = StringClauseBuilder.SINGLETON.build(filteredChildren.get(0) , tinyExpressionTokens);
		ExpressionOrLiteral right = StringClauseBuilder.SINGLETON.build(filteredChildren.get(1) , tinyExpressionTokens);
		
		builder.append("(")
			.append(left.toString())
			.append(".equals(")
			.append(right.toString())
			.append("))");
	}
}