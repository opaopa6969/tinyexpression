package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;

public class NakedVariableDeclarationParser extends AbstractVariableDeclarationParser {

  @Override
  public java.util.Optional<Parser> typeDeclaration() {
    return java.util.Optional.empty();
  }
  
}