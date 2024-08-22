package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface TokenCodeBuilder {
	public void build(SimpleJavaCodeBuilder builder, Token token ,
	    TinyExpressionTokens tinyExpressionTokens , ExpressionType resultType);
}