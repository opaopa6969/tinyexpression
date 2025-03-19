package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.RangedString;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;

public interface RootVariableParser extends TypedVariableParser{

  public default TypedToken<RootVariableParser> newWithTypedParser(
      TypedToken<ExclusiveNakedVariableParser> tokenOfNakedVariable) {

    RootVariableParser typedVariable = Parser.get(rootOfTypedVariableParser());
    TypedToken<RootVariableParser> root = tokenOfNakedVariable.newWithReplaceTyped(typedVariable);
    TypedToken<? extends VariableParser> child = tokenOfNakedVariable.newWithReplaceTyped(Parser.get(oneOfTypedVariableParser()));

    ExpressionType expressionType = typedVariable.typeAsOptional().get();

    Token typePrefix = new Token(TokenKind.consumed, new RangedString(0, expressionType.javaType().getSimpleName()), Parser.get(typeHintVariableParser()));

    TypedToken<? extends VariableParser> newCreatesOfTyped = child.newCreatesOfTyped(typePrefix ,
        tokenOfNakedVariable.newWithReplaceTyped(Parser.get(NakedVariableParser.class)));
    root = root.newCreatesOfTyped(List.of(newCreatesOfTyped));
    return root;
  }

  public Class<? extends RootVariableParser> rootOfTypedVariableParser();
  public Class<? extends VariableParser> oneOfTypedVariableParser();
  public Class<? extends TypeHintVariableParser> typeHintVariableParser();
}