package org.unlaxer.tinyexpression.parser;

import org.unlaxer.CodePointOffset;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenList;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;

public interface RootVariableParser extends TypedVariableParser{
  
  public default TypedToken<RootVariableParser> newWithTypedParser(
      TypedToken<ExclusiveNakedVariableParser> tokenOfNakedVariable) {
    
    RootVariableParser typedVariable = Parser.get(rootOfTypedVariableParser());
    TypedToken<RootVariableParser> root = tokenOfNakedVariable.newWithReplaceTyped(typedVariable);
    TypedToken<? extends VariableParser> child = tokenOfNakedVariable.newWithReplaceTyped(Parser.get(oneOfTypedVariableParser()));
    
    ExpressionType expressionType = typedVariable.typeAsOptional().get();
    Source source = tokenOfNakedVariable.getSource();
    CodePointOffset offsetFromParent = source.offsetFromParent();
    Source rootSource = source.root();
    Token typePrefix = new Token(TokenKind.consumed, StringSource.createSubSource(expressionType.javaType(),rootSource, offsetFromParent), Parser.get(typeHintVariableParser()));
    
    TypedToken<? extends VariableParser> newCreatesOfTyped = child.newCreatesOfTyped(typePrefix ,
        tokenOfNakedVariable.newWithReplaceTyped(Parser.get(NakedVariableParser.class)));
    root = root.newCreatesOfTyped(TokenList.of(newCreatesOfTyped));
    return root;
  }
  
  public Class<? extends RootVariableParser> rootOfTypedVariableParser();
  public Class<? extends VariableParser> oneOfTypedVariableParser();
  public Class<? extends TypeHintVariableParser> typeHintVariableParser();
}