package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpressionParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringSuffixedVariableParser;

public class StringSuffixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    StringSuffixedVariableParser stringSuffixedVariableParser = new StringSuffixedVariableParser();
    testAllMatch(stringSuffixedVariableParser, "$hoge as string");
    testAllMatch(stringSuffixedVariableParser, "$hoge as String");
    testUnMatch(stringSuffixedVariableParser, "$hoge");

    StringExpressionParser stringExpressionParser = new StringExpressionParser();
    testAllMatch(stringExpressionParser, "$hoge as string");
    testAllMatch(stringExpressionParser, "$hoge as String");
    testAllMatch(stringExpressionParser, "$hoge");
  }

}
