package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.RangedString;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;

public interface RootVariableParser extends VariableParser{
  
  public default Token newWithTypedParser(Token tokenOfNakedVariable) {
    
    RootVariableParser typedVariable = Parser.get(rootOfTypedVariableParser());
    Token root = tokenOfNakedVariable.newWithReplace(typedVariable);
    Token child = tokenOfNakedVariable.newWithReplace(Parser.get(oneOfTypedVariableParser()));
    
    ExpressionType expressionType = typedVariable.type().get();

    Token typePrefix = new Token(TokenKind.consumed, new RangedString(0, expressionType.javaType()), Parser.get(typeHintVariableParser()));
    
    child = child.newCreatesOf(typePrefix ,
        tokenOfNakedVariable.newWithReplace(Parser.get(NakedVariableParser.class)));
    root = root.newCreatesOf(List.of(child));
    return root;
  }
  
  public Class<? extends RootVariableParser> rootOfTypedVariableParser();
  public Class<? extends VariableParser> oneOfTypedVariableParser();
  public Class<? extends TypeHintVariableParser> typeHintVariableParser();
}