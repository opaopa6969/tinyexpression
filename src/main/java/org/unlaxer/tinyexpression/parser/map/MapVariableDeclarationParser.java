package org.unlaxer.tinyexpression.parser.map;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.javalang.AbstractVariableDeclarationParser;

public class MapVariableDeclarationParser extends AbstractVariableDeclarationParser {
  
  @Override
  public java.util.Optional<Parser> typeDeclaration() {
    return java.util.Optional.of(
        Parser.newInstance(MapTypeDeclarationParser.class).addTag(typed, typeTag()));
  }

  @Override
  public Tag typeTag() {
    return Tag.of(MapVariableDeclarationParser.class);
  }

  @Override
  public Optional<Parser> setter() {
    return Optional.of(
        new org.unlaxer.parser.combinator.Optional(
            Parser.get(MapSetterParser.class)
        )
    );
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(ExpressionType.map);
  }

}