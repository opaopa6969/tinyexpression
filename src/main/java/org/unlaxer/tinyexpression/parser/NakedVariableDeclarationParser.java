package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.Tag;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.javalang.AbstractVariableDeclarationParser;

public class NakedVariableDeclarationParser extends AbstractVariableDeclarationParser {

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

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
  public Optional<ExpressionType> type() {
    return Optional.empty();
  }
  
}