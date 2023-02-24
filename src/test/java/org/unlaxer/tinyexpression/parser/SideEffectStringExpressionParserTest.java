package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;


public class SideEffectStringExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    
    SideEffectStringExpressionParser sideEffectStringExpressionParser = new SideEffectStringExpressionParser();
    
    testAllMatch(sideEffectStringExpressionParser,("with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList('niku')"));
    {
      String formula =
          "with side effect:jp.caulis.calc.Effects#foo('niku',true,'niku')";
      
      TestResult testAllMatch = testAllMatch(sideEffectStringExpressionParser, formula,false);
      
      Token rootToken = testAllMatch.parsed.getRootToken();
      
      String string = TokenPrinter.get(rootToken);
      System.out.println(string);
    }
  }
  
  @Test
  public void testFormula() {
    
    SideEffectExpressionParser sideEffectExpressionParser = new SideEffectExpressionParser();
    
    String[] formulas= {
        "with side effect:jp.caulis.calc.Effects#foo('niku')",
        "with side effect:jp.caulis.calc.Effects#foo('niku')",
        "with side effect:jp.caulis.calc.Effects#foo('niku')"
    };
    
    for (String formula : formulas) {
      
      System.out.println(formula);
      System.out.println();
      
      Parsed parsed = parse(sideEffectExpressionParser, formula);
      Token rootToken = parsed.getRootToken();
      TokenPrinter.output(rootToken, System.out);
      
      System.out.println();
    }
    
  }
  
  @Test
  public void testFormula2() {
    SideEffectExpressionParser sideEffectExpressionParser = new SideEffectExpressionParser();
    
    String[] formulas= {
        "with side effect:jp.caulis.calc.Effects#foo(1+3*4+len('foo'),1==3,'niku')",
        "with side effect:jp.caulis.calc.Effects#foo(1+3*4,1==3,'niku')",
        "with side effect:jp.caulis.calc.Effects#foo(1,true | false ,'niku')"
    };
    
    for (String formula : formulas) {
      
      TestResult result = testAllMatch(sideEffectExpressionParser,formula,false);
      assertTrue(result.isOK());
      System.out.println(result.parsed.status);
    }
  }
}
