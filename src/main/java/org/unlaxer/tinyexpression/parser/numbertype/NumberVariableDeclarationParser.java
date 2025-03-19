package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.javalang.AbstractVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclaration;

@SuppressWarnings("serial")
public class NumberVariableDeclarationParser extends AbstractVariableDeclarationParser{

  @Override
  public java.util.Optional<Parser> typeDeclaration() {
    return java.util.Optional.of(
        Parser.newInstance(NumberTypeDeclarationParser.class).addTag(typed, typeTag()));
  }

  @Override
  public Tag typeTag() {
    return Tag.of(NumberVariableDeclarationParser.class);
  }

  @Override
  public Optional<Parser> setter() {
    return Optional.of(
        new org.unlaxer.parser.combinator.Optional(
            NumberSetterParser.class
        )
    );
  }

  @Override
  public Optional<ExpressionType> type(TypedToken<? extends VariableDeclaration> thisParserParsed) {

    Token typeHintToken = thisParserParsed.flatten().stream()
        .filter(TokenPredicators.parsers(NumberTypeHintParser.class))
        .findFirst()
        .get();
    String string = typeHintToken.getToken().get();
    return Optional.of(ExpressionTypes.of(string));
  }
}