package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;

public class ExpressionBuilderTest {

  @Test
  public void test() {
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
//    NumberExpressionParser expressionParser = new NumberExpressionParser();
    ParseContext parseContext = new ParseContext(new StringSource("1+(8/4)"));
    Parsed parsed= tinyExpressionParser.parse(parseContext);
    Token rootToken = parsed.getRootToken(true); // ASTノードのみにしないとASTCreatorがうまく動かない
    TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken);
    {
      SimpleJavaCodeBuilder simpleJavaCodeBuilder = new SimpleJavaCodeBuilder();
      System.out.println(TokenPrinter.get(rootToken));
      NumberExpressionBuilder.SINGLETON.build(simpleJavaCodeBuilder, 
          tinyExpressionTokens.expressionToken , tinyExpressionTokens);
      String build = simpleJavaCodeBuilder.build();
      System.out.println(build);
      assertFalse(build.contains("(1.0f+(8.0f/4.0f))"));
    }

    {
      SimpleJavaCodeBuilder simpleJavaCodeBuilder = new SimpleJavaCodeBuilder();

      rootToken = OperatorOperandTreeCreator.SINGLETON.apply(rootToken);
      System.out.println(TokenPrinter.get(rootToken));
      
      NumberExpressionBuilder.SINGLETON.build(simpleJavaCodeBuilder, rootToken , tinyExpressionTokens);
      String build = simpleJavaCodeBuilder.build();
      System.out.println(build);
      assertTrue(build.contains("(1.0f+(8.0f/4.0f))"));
    }
  }

}
