package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;

public interface TypedTokenCodeBuilder<T extends Parser>{
  public void build(SimpleJavaCodeBuilder builder, TypedToken<T> token ,
      TinyExpressionTokens tinyExpressionTokens);
}