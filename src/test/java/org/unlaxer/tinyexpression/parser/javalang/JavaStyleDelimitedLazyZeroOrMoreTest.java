package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;

public class JavaStyleDelimitedLazyZeroOrMoreTest extends ParserTestBase{
  
  public static class TestParser extends JavaStyleDelimitedLazyZeroOrMore{

    
    @Override
    public Supplier<Parser> targetParser() {
      return ()->new WordParser("b");
    }

  }
  
  public static class TestChainParser extends JavaStyleDelimitedLazyChain{

    @Override
    public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
      return super.parse(parseContext, tokenKind, invertMatch);
    }

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(new WordParser("b"));
    }
  }
  
  public static class TestZeroOrMoreChain extends LazyZeroOrMore{

    @Override
    public Supplier<Parser> getLazyParser() {
      return TestChainParser::new;
    }

    @Override
    public Optional<Parser> getLazyTerminatorParser() {
      return Optional.empty();
    }
    
  }
  
  
  @Test
  public void testChain() {
    setLevel(OutputLevel.mostDetail);
    
    var parser = new TestChainParser();
    testAllMatch(parser, "b");
    testAllMatch(parser, "   b");
    testAllMatch(parser, "   b  ");
    testAllMatch(parser, "/* */   b  ");
    testAllMatch(parser, "/* */   b //  ");
  }
  
  @Test
  public void testZeroOrMoreChain() {
    
    setLevel(OutputLevel.mostDetail);
    
    var  testParser = new TestZeroOrMoreChain();
    
    testAllMatch(testParser, " b ");
    testAllMatch(testParser, "b b");
    testAllMatch(testParser, "b");
    testAllMatch(testParser, "");
  }

  
  @Test
  public void testZeroOrMore() {
    
    setLevel(OutputLevel.mostDetail);
    
    var testParser = new TestParser();
    
    testAllMatch(testParser, " b ");
    testAllMatch(testParser, "b b");
    testAllMatch(testParser, "b");
    testAllMatch(testParser, "");
  }

}
