package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.ChildOccurs;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;

public class StringVariableMatchedWithVariableDeclarationParser extends VariableDeclarationMatchedTokenParser
    implements StringExpression, VariableParser {

  public StringVariableMatchedWithVariableDeclarationParser() {
    super(ExpressionType.string);
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
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.string);
  }
}