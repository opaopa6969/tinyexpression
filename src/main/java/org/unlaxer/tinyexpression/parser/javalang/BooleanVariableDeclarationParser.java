package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;

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
}