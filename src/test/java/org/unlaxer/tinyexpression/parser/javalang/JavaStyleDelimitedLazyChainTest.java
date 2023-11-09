package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;

public class JavaStyleDelimitedLazyChainTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    
    FooCStyleDelimitedLazyChain parser = new FooCStyleDelimitedLazyChain();
    testAllMatch(parser, "ABCDEFG");
    testAllMatch(parser, " ABCDEFG ");
    testAllMatch(parser, " ABCDEFG//コメント");
    testAllMatch(parser, "/*コメント*/ ABCDEFG//コメント");
    testAllMatch(parser, " ABCDE//コメント  \nFG");
    testAllMatch(parser, " ABC/* aaaコメント */DE//コメント  \nFG/* asdasd \n*///asd");
    
    testUnMatch(parser, "//コメントABCDEFG");
    testUnMatch(parser, "/*コメント*/ AB//コメント\nCDEFG//コメント");
    testUnMatch(parser, "/*コメント*/ AB//コメント/*コメント \n* niku*/\nCDEFG//コメント");
  }
  
  
  public static class FooCStyleDelimitedLazyChain extends JavaStyleDelimitedLazyChain{

    @Override
    public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
      return super.parse(parseContext, tokenKind, invertMatch);
    }

    @Override
    public Parsers getLazyParsers() {
      return 
        new Parsers(
          new WordParser("ABC"),
          new WordParser("DE"),
          new WordParser("FG")
        );
    }
  }

}
