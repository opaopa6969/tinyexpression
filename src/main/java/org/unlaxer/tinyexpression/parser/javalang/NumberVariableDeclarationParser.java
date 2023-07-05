package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.NumberSetterParser;
import org.unlaxer.tinyexpression.parser.VariableType;

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
            Parser.get(NumberSetterParser.class)
        )
    );
  }

  @Override
  public Optional<VariableType> type() {
    return Optional.of(VariableType.number);
  }
}