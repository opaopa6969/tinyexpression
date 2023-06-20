package org.unlaxer.tinyexpression.parser;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.PredicateAnyMatchForParsedParser;

public class StrictTypedStringMatchExpressionParser extends PredicateAnyMatchForParsedParser implements NumberExpression{

  public StrictTypedStringMatchExpressionParser() {
    super(Parser.get(StringMatchExpressionParser.class), 
        TokenPredicators.hasTagInParent(StringMatchExpressionParser.choiceTag)
          .and(TokenPredicators.hasTag(StrictTyped.get()))         
    );
  }
}