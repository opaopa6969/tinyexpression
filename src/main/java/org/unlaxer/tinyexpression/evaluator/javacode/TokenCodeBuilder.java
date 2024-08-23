package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;

public interface TokenCodeBuilder {
	public void build(SimpleJavaCodeBuilder builder, Token token ,
	    TinyExpressionTokens tinyExpressionTokens);
}