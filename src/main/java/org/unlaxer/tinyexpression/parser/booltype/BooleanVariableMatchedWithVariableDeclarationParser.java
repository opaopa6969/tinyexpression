package org.unlaxer.tinyexpression.parser.booltype;

import java.util.Optional;

import org.unlaxer.parser.ChildOccurs;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.VariableDeclarationMatchedTokenParser;
import org.unlaxer.tinyexpression.parser.VariableParser;

public class BooleanVariableMatchedWithVariableDeclarationParser extends VariableDeclarationMatchedTokenParser
    implements BooleanExpression, VariableParser {

  public BooleanVariableMatchedWithVariableDeclarationParser() {
    super(ExpressionTypes._boolean);
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
    return Optional.of(ExpressionTypes._boolean);
  }
}