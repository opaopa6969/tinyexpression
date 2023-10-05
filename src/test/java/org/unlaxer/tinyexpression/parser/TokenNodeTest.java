package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;

public class TokenNodeTest{
  
  @Test
  public void testAstNode() {
    
    var parser =
        new ZeroOrMore(new org.unlaxer.parser.combinator.Optional(new WordParser("abc")));
    
    StringSource stringSource = new StringSource("abc");
    ParseContext parseContext = new ParseContext(stringSource);
    
    Parsed parse = parser.parse(parseContext);
    
    Token rootToken = parse.getRootToken();
    printToken(rootToken.getAstNodeChildren());
    printToken(rootToken.getOriginalChildren());
    
    Token childWithParser = rootToken.getChildWithParser(WordParser.class);
    System.out.println(childWithParser);
  }
  
  static void printToken(List<Token> tokens) {
    for (Token token : tokens) {
      String string = TokenPrinter.get(token);
      System.out.println(string);
    }
    
  }

  
}