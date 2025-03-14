package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpressionParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class ExpressionsParser extends LazyChoice {
  

  public ExpressionsParser() {
    super();
  }

  public ExpressionsParser(Name name) {
    super(name);
  }

  @Override
  public Parsers getLazyParsers() {
    return Parsers.of(
        Parser.get(NumberExpressionParser.class),
        Parser.get(BooleanExpressionParser.class),
        Parser.get(StringExpressionParser.class)
    );
  }
  
  @TokenExtractor
  public static Token extractExpressionToken(Token thisParserParsed){
    return ChoiceInterface.choiced(thisParserParsed);
  }
  
}