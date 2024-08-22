package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class StringInBooleanExpressionBuilder implements TokenCodeBuilder {

	public static final StringInBooleanExpressionBuilder SINGLETON = new StringInBooleanExpressionBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token , 
	    TinyExpressionTokens tinyExpressionTokens , ExpressionType resultTyhpe) {
		
		builder.append("org.unlaxer.util.StringIn.match(");
		
		List<Token> filteredChildren = token.filteredChildren;
		Iterator<ExpressionOrLiteral> iterator = filteredChildren.stream()
			.map(_token-> StringClauseBuilder.SINGLETON.build(_token, tinyExpressionTokens , resultTyhpe))
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