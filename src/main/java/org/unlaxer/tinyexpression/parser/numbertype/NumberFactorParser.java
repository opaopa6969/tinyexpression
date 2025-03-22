package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.AbstractNumberFactorParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class NumberFactorParser extends AbstractNumberFactorParser /*implements TypedParser*/{

  public NumberFactorParser(SpecifiedExpressionTypes specifiedExpressionType) {
    super(specifiedExpressionType);
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(true);
  }

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

//  @Override
//  public ExpressionTypes type() {
//    return ExpressionTypes.number;
//  }
//
//
//  @Override
//  public Set<TypedParser> allTypes() {
//
//    return allTypes;
//  }
//
//  static Set<TypedParser> allTypes = Set.of(
//      Singletons.get(NumberFactorParser.class),
//      Singletons.get(StringFactorParser.class),
//      Singletons.get(BooleanFactorParser.class)
//  );
//
//  @Override
//  public Set<TypedParser> otherTypes() {
//    return otherTypes;
//  }
//
//  static Set<TypedParser> otherTypes = Set.of(
//      Singletons.get(StringFactorParser.class),
//      Singletons.get(BooleanFactorParser.class)
//  );

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes.number;
  }

}