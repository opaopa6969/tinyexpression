package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.util.SimpleBuilder;

public class MethodInvocationParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.mostDetail);
    
    var parser = new MethodInvocationParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("call test()")
      ;
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(parser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testArguments() {
    
    setLevel(OutputLevel.mostDetail);
    
    var parser = new MethodInvocationParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("internal test($fee,true,'niku')")
      ;
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(parser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }

}
