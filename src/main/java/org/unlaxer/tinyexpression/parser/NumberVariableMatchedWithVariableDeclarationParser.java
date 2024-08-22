package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.ChildOccurs;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;

public class NumberVariableMatchedWithVariableDeclarationParser extends VariableDeclarationMatchedTokenParser
    implements NumberExpression, VariableParser {

  public NumberVariableMatchedWithVariableDeclarationParser() {
    super(ExpressionTypes.number);
  }

  @Override
  public void prepareChildren(Parsers childrenContainer) {
  }

  @Override
  public ChildOccurs getChildOccurs() {
    return ChildOccurs.none;
  }

  @Override
  public Parser createParser() {
    return this;
  }

  @Override
  public Optional<ExpressionTypes> typeAsOptional() {
    return Optional.of(ExpressionTypes.number);
  }
}