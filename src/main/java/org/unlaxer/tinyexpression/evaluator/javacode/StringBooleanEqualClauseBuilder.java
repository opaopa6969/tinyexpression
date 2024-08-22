package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class StringBooleanEqualClauseBuilder implements TokenCodeBuilder {

	public static final StringBooleanEqualClauseBuilder SINGLETON = new StringBooleanEqualClauseBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder , Token token , 
	    TinyExpressionTokens tinyExpressionTokens , ExpressionType resultType) {
		
		List<Token> filteredChildren = token.filteredChildren;
		
		ExpressionOrLiteral left = StringClauseBuilder.SINGLETON.build(filteredChildren.get(0) , 
		    tinyExpressionTokens , resultType);
		ExpressionOrLiteral right = StringClauseBuilder.SINGLETON.build(filteredChildren.get(1) ,
		    tinyExpressionTokens , resultType);
		
		builder.append("(")
			.append(left.toString())
			.append(".equals(")
			.append(right.toString())
			.append("))");
		
    left.populateTo(builder, Kind.Function);
    right.populateTo(builder, Kind.Function);
	}
}