package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.parser.booltype.*;
import org.unlaxer.tinyexpression.parser.numbertype.*;
import org.unlaxer.tinyexpression.parser.stringtype.*;
import org.unlaxer.tinyexpression.parser.javatype.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class BooleanExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {

    setLevel(OutputLevel.detail);
    BooleanExpressionParser parser = new BooleanExpressionParser();

    testAllMatch(parser, "true");
    testAllMatch(parser, "true | false ");
    testAllMatch(parser, "true & $foo == 1");
    testAllMatch(parser, "$hour>0 & $hour<5");
  }

  @Test
  public void testGroupingFollowedByComment() {
    // Regression test for issue #14:
    // comment after grouping like (a & b) // comment should not cause a parse error
    BooleanExpressionParser parser = new BooleanExpressionParser();

    testAllMatch(parser, "($foo == 1 & $bar == 2)");
    testAllMatch(parser, "($foo == 1 & $bar == 2) // comment");
    testAllMatch(parser, "($foo == 1 & $bar == 2) /* block comment */");
    testAllMatch(parser, "($foo == 1) | ($bar == 2) // trailing comment");
  }

}
