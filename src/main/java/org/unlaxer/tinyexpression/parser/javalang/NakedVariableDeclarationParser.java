package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ObjectSetterParser;

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
    return Optional.of(
        new org.unlaxer.parser.combinator.Optional(
            ObjectSetterParser.class
        )
    );
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(org.unlaxer.tinyexpression.parser.ExpressionTypes.object);
  }
  
}
