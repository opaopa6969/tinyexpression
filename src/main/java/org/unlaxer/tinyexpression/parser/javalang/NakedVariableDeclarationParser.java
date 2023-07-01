package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;

public class NakedVariableDeclarationParser extends AbstractVariableDeclarationParser {

  @Override
  public java.util.Optional<Parser> typeDeclaration() {
    return java.util.Optional.empty();
  }

  @Override
  public Tag typeTag() {
    return Tag.of(NakedVariableDeclarationParser.class);
  }

  @Override
  public Optional<Parser> setter() {
    return Optional.empty();
  }
  
}