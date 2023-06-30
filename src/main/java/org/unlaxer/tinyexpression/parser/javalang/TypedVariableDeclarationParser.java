package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;

public class TypedVariableDeclarationParser extends AbstractVariableDeclarationParser {

  @Override
  public java.util.Optional<Parser> typeDeclaration() {
    return java.util.Optional.of(
        Parser.newInstance(TypeDeclarationParser.class).addTag(typed));
  }
}