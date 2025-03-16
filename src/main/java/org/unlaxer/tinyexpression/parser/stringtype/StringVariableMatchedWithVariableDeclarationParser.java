package org.unlaxer.tinyexpression.parser.stringtype;

import java.util.Optional;

import org.unlaxer.parser.ChildOccurs;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.VariableDeclarationMatchedTokenParser;
import org.unlaxer.tinyexpression.parser.VariableParser;

@SuppressWarnings("serial")
public class StringVariableMatchedWithVariableDeclarationParser extends VariableDeclarationMatchedTokenParser
    implements StringExpression, VariableParser {

  public StringVariableMatchedWithVariableDeclarationParser() {
    super(ExpressionTypes.string);
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
    return Optional.of(ExpressionTypes.string);
  }
}