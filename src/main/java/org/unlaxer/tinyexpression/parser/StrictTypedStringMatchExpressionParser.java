package org.unlaxer.tinyexpression.parser;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.PredicateAnyMatchForParsedParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;
import org.unlaxer.tinyexpression.parser.stringtype.StringMatchExpressionParser;

public class StrictTypedStringMatchExpressionParser extends PredicateAnyMatchForParsedParser implements NumberExpression{

  public StrictTypedStringMatchExpressionParser() {
    super(Parser.get(StringMatchExpressionParser.class), 
        TokenPredicators.hasTagInParent(StringMatchExpressionParser.choiceTag)
          .and(TokenPredicators.hasTag(StrictTyped.get()))         
    );
    addTag(ExpressionTags.matchExpression.tag());
  }
}