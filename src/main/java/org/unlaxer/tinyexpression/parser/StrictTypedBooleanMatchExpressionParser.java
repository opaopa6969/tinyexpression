package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.parser.booltype.*;
import org.unlaxer.tinyexpression.parser.numbertype.*;
import org.unlaxer.tinyexpression.parser.stringtype.*;
import org.unlaxer.tinyexpression.parser.javatype.*;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.PredicateAnyMatchForParsedParser;

public class StrictTypedBooleanMatchExpressionParser extends PredicateAnyMatchForParsedParser implements BooleanExpression{

  public StrictTypedBooleanMatchExpressionParser() {
    super(Parser.get(BooleanMatchExpressionParser.class), 
        TokenPredicators.hasTagInParent(BooleanMatchExpressionParser.choiceTag)
          .and(TokenPredicators.hasTag(StrictTyped.get()))         
    );
    addTag(ExpressionTags.matchExpression.tag());
  }
}