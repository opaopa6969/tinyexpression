package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.bool.BooleanMethodParser;
import org.unlaxer.tinyexpression.parser.number.NumberMethodParser;
import org.unlaxer.tinyexpression.parser.string.StringMethodParser;

public class MethodChoiceParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(NumberMethodParser.class),
        Parser.get(StringMethodParser.class),
        Parser.get(BooleanMethodParser.class)
    );
  }
  
//  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
//  public List<Token> extractMethods(Token thisParserParsed) {
//    
//    checkTokenParsedByThisParser(thisParserParsed);
//    Token choiced = ChoiceInterface.choiced(thisParserParsed);
//  }

}