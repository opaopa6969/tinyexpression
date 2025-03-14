package org.unlaxer.tinyexpression.parser;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.PredicateAnyMatchForParsedParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;
import org.unlaxer.tinyexpression.parser.booltype.BooleanMatchExpressionParser;

public class StrictTypedBooleanMatchExpressionParser extends PredicateAnyMatchForParsedParser implements BooleanExpression{

  public StrictTypedBooleanMatchExpressionParser() {
    super(Parser.get(BooleanMatchExpressionParser.class), 
        TokenPredicators.hasTagInParent(BooleanMatchExpressionParser.choiceTag)
          .and(TokenPredicators.hasTag(StrictTyped.get()))         
    );
    addTag(ExpressionTags.matchExpression.tag());
  }
}