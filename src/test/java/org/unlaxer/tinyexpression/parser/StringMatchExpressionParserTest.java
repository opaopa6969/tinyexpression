package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.string.StrictTypedStringMatchExpressionParser;

public class StringMatchExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    var stringMatchExpressionParser = new StrictTypedStringMatchExpressionParser();
    testAllMatch(stringMatchExpressionParser, "match{1==1->'niku',default->'sushi'}");
    testAllMatch(stringMatchExpressionParser, "match{1==1->$lunch as string ,default->$defaultValue}");
    testUnMatch(stringMatchExpressionParser, "match{1==1->$lunch,default->$defaultValue}");
  }

}
