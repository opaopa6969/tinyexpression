package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;

public class StringBooleanNotEqualClauseBuilder implements TokenCodeBuilder {

	public static final StringBooleanNotEqualClauseBuilder SINGLETON = new StringBooleanNotEqualClauseBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token , TinyExpressionTokens tinyExpressionTokens) {
		
		List<Token> filteredChildren = token.filteredChildren;
		
		ExpressionOrLiteral left = StringClauseBuilder.SINGLETON.build(filteredChildren.get(0) , tinyExpressionTokens);
		ExpressionOrLiteral right = StringClauseBuilder.SINGLETON.build(filteredChildren.get(1) , tinyExpressionTokens);
		
		builder.append("(false==")
			.append(left.toString())
			.append(".equals(")
			.append(right.toString())
			.append("))");
		
    left.populateTo(builder, Kind.Function);
    right.populateTo(builder, Kind.Function);

	}	
}