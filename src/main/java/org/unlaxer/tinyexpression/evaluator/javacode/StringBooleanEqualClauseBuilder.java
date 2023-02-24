package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculator.CodeBuilder;

public class StringBooleanEqualClauseBuilder implements CodeBuilder {

	public static final StringBooleanEqualClauseBuilder SINGLETON = new StringBooleanEqualClauseBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder , Token token) {
		
		List<Token> filteredChildren = token.filteredChildren;
		
		ExpressionOrLiteral left = StringClauseBuilder.SINGLETON.build(filteredChildren.get(0));
		ExpressionOrLiteral right = StringClauseBuilder.SINGLETON.build(filteredChildren.get(1));
		
		builder.append("(")
			.append(left.toString())
			.append(".equals(")
			.append(right.toString())
			.append("))");
	}
}