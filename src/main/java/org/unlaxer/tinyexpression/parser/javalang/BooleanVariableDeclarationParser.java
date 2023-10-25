package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.BooleanSetterParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class BooleanVariableDeclarationParser extends AbstractVariableDeclarationParser {

  @Override
  public java.util.Optional<Parser> typeDeclaration() {
    return java.util.Optional.of(
        Parser.newInstance(BooleanTypeDeclarationParser.class).addTag(typed, typeTag()));
  }

  @Override
  public Tag typeTag() {
    return Tag.of(BooleanVariableDeclarationParser.class);
  }

  @Override
  public Optional<Parser> setter() {
    return Optional.of(
        new org.unlaxer.parser.combinator.Optional(
            BooleanSetterParser.class
        )
    );
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(ExpressionType.bool);
  }

}