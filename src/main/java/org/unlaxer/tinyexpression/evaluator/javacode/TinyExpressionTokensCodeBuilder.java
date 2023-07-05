package org.unlaxer.tinyexpression.evaluator.javacode;

public interface TinyExpressionTokensCodeBuilder {
  public void build(SimpleJavaCodeBuilder builder, TinyExpressionTokens token);
}