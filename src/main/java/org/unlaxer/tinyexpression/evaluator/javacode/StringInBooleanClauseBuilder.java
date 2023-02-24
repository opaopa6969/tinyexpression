package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculator.CodeBuilder;

public class StringInBooleanClauseBuilder implements CodeBuilder {

	public static final StringInBooleanClauseBuilder SINGLETON = new StringInBooleanClauseBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token) {
		
		builder.append("org.unlaxer.util.StringIn.match(");
		
		List<Token> filteredChildren = token.filteredChildren;
		Iterator<ExpressionOrLiteral> iterator = filteredChildren.stream()
			.map(StringClauseBuilder.SINGLETON::build)
			.iterator();
		
		while (iterator.hasNext()) {
			ExpressionOrLiteral expressionOrLiteral = iterator.next();
			builder.append(expressionOrLiteral.toString());
			if(iterator.hasNext()) {
				builder.append(",");
			}
		}
		
		builder.append(")");
	}
}