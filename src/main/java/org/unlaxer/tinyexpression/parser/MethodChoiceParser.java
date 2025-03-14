package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.booltype.BooleanMethodParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberMethodParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringMethodParser;

public class MethodChoiceParser extends LazyChoice{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
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