package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.VariableParser;

@SuppressWarnings("serial")
public class VariableDeclarationParser extends LazyChoice{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(NumberVariableDeclarationParser.class),
        Parser.get(StringVariableDeclarationParser.class),
        Parser.get(BooleanVariableDeclarationParser.class)
    );
  }
  
  public static TypedToken<VariableParser> extractVariableParserToken(Token thisParserParsed) {
    
    Token choiced = thisParserParsed;
    Parser parser = thisParserParsed.getParser();
    if(parser instanceof Choice) {
      
      choiced = ChoiceInterface.choiced(thisParserParsed);
    }
    
    parser = choiced.getParser();
      
    if(parser instanceof AbstractVariableDeclarationParser) {
      
      return choiced.getChild(TokenPredicators.parserImplements(VariableParser.class))
          .typed(VariableParser.class);
    }
    throw new IllegalArgumentException();
    
  }
  
}