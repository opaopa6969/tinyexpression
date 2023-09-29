package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.PredicateAnyMatchForParsedParser;
import org.unlaxer.tinyexpression.parser.ExpressionTags;
import org.unlaxer.tinyexpression.parser.StrictTyped;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;

public class StrictTypedStringMatchExpressionParser extends PredicateAnyMatchForParsedParser implements NumberExpression{

  public StrictTypedStringMatchExpressionParser() {
    super(Parser.get(StringMatchExpressionParser.class), 
        TokenPredicators.hasTagInParent(StringMatchExpressionParser.choiceTag)
          .and(TokenPredicators.hasTag(StrictTyped.get()))         
    );
    addTag(ExpressionTags.matchExpression.tag());
  }
}