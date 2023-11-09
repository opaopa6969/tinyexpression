package org.unlaxer.tinyexpression.parser.tuple;

import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.Tag;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.javalang.AbstractVariableDeclarationParser;

public class TupleVariableDeclarationParser extends AbstractVariableDeclarationParser{

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(ExpressionType.tuple);
  }

  @Override
  public Optional<Parser> setter() {
    return Optional.of(
        new org.unlaxer.parser.combinator.Optional(
            Parser.get(TupleSetterParser.class)
        )
    );
  }

  @Override
  public Optional<Parser> typeDeclaration() {
    return java.util.Optional.of(
        Parser.newInstance(TupleTypeDeclarationParser.class).addTag(typed, typeTag()));
  }

  @Override
  public Tag typeTag() {
    return Tag.of(TupleVariableDeclarationParser.class);
  }
  
}