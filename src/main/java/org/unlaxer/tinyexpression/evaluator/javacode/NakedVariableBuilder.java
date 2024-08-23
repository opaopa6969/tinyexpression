package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.TypedToken;
import org.unlaxer.tinyexpression.parser.VariableParser;

public class NakedVariableBuilder implements TokenCodeBuilder{

  public static NakedVariableBuilder SINGLETON = new NakedVariableBuilder();

  @Override
  public void build(SimpleJavaCodeBuilder builder, Token token ,
      TinyExpressionTokens tinyExpressionTokens) {
    
    TypedToken<VariableParser> typed = token.typed(VariableParser.class);
    VariableParser parser = typed.getParser();
    String variableName = parser.getVariableName(typed);
    builder.append("calculateContext.getFromNumberOrStringOrBoolean(").w(variableName).append(")");
  }
}