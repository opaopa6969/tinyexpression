package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.stringtype.StringSetterParser;

@SuppressWarnings("serial")
public class StringVariableDeclarationParser extends AbstractVariableDeclarationParser {
  
  @Override
  public java.util.Optional<Parser> typeDeclaration() {
    return java.util.Optional.of(
        Parser.newInstance(StringTypeDeclarationParser.class).addTag(typed, typeTag()));
  }

  @Override
  public Tag typeTag() {
    return Tag.of(StringVariableDeclarationParser.class);
  }

  @Override
  public Optional<Parser> setter() {
    return Optional.of(
        new org.unlaxer.parser.combinator.Optional(
            StringSetterParser.class
        )
    );
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(ExpressionTypes.string);
  }

}