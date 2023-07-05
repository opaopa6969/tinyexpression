package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class NumberMatchExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    StrictTypedNumberMatchExpressionParser numberMatchExpressionParser = new StrictTypedNumberMatchExpressionParser();
    testAllMatch(numberMatchExpressionParser, "match{1==1->3,default->0}");
    testAllMatch(numberMatchExpressionParser, "match{1==1->$val,default->$val as number}");
    testAllMatch(numberMatchExpressionParser, "match{1==1->$val,default->0}");
    //typeを特定するhintがない
    testUnMatch(numberMatchExpressionParser, "match{1==1->$val,default->$default}");
  }

}
