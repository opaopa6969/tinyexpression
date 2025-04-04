package org.unlaxer.tinyexpression.parser.numbertype;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class NumberIfExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    NumberIfExpressionParser numberIfExpressionParser = new NumberIfExpressionParser();
    
    testAllMatch(numberIfExpressionParser, "if(true){1}else{2}");

  }

}
