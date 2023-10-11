package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.CommaParser;

public class JavaStyleDelimitedLazySeparatedValuesZeroOrMoreTest extends ParserTestBase{

  @Test
  public void test() {

    setLevel(OutputLevel.mostDetail);
    
    var parser = new JavaStyleDelimitedLazySeparatedValuesZeroOrMore() {

      @Override
      public Supplier<Parser> getLazyParser() {
        return ()-> new WordParser("a");
      }

      @Override
      public Optional<Parser> getLazyTerminatorParser() {
        return Optional.empty();
      }

      @Override
      public Supplier<Parser> getSeparatorParser() {
        return CommaParser::new;
      }
      
    };
    
    testAllMatch(parser, "");
    testPartialMatch(parser, " ","");
    testAllMatch(parser, "a");
    testAllMatch(parser, " a ");
    testAllMatch(parser, "/*.*/a//");
    testAllMatch(parser, "a, a ,/* aa */a");
    testPartialMatch(parser, "a, a, ,/* aa */a","a, a");
    testPartialMatch(parser, ",a, a, ,/* aa */a","");
  }
  
  
  @Test
  public void testEmptyAcceptable() {
    setLevel(OutputLevel.mostDetail);
    
    
    var parser = new JavaStyleDelimitedLazySeparatedValuesZeroOrMore() {

      @Override
      public Supplier<Parser> getLazyParser() {
        return ()-> new org.unlaxer.parser.combinator.Optional(
            new WordParser("a")
        );
      }

      @Override
      public Optional<Parser> getLazyTerminatorParser() {
        return Optional.empty();
      }

      @Override
      public Supplier<Parser> getSeparatorParser() {
        return CommaParser::new;
      }
      
    };
    
    testPartialMatch(parser, "b,a, a, ,/* aa */a","");
    testAllMatch(parser, "");
    testPartialMatch(parser, " "," ");
    testAllMatch(parser, "a");
    testAllMatch(parser, " a ");
    testAllMatch(parser, "/*.*/a//");
    testAllMatch(parser, "a, a ,/* aa */a");
    testAllMatch(parser, ", a ,/* aa */");
    testAllMatch(parser, "a, a, ,/* aa */a");
    testAllMatch(parser, ",a, a, ,/* aa */a");
  }

}