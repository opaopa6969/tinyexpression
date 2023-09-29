package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.PredicateAnyMatchForParsedParser;
import org.unlaxer.tinyexpression.parser.ExpressionTags;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedNumberMatchExpressionParser extends PredicateAnyMatchForParsedParser implements NumberExpression{

  public StrictTypedNumberMatchExpressionParser() {
    super(Parser.get(NumberMatchExpressionParser.class), 
        TokenPredicators.hasTagInParent(NumberMatchExpressionParser.choiceTag)
          .and(TokenPredicators.hasTag(StrictTyped.get()))         
    );
    addTag(ExpressionTags.matchExpression.tag());
  }
}