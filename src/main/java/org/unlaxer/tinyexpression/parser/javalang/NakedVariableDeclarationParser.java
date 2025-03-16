package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;

@SuppressWarnings("serial")
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

  @Override
  public Optional<ExpressionType> type(TypedToken<? extends VariableDeclaration> thisParserParsed) {
    return Optional.empty();
  }
  
}