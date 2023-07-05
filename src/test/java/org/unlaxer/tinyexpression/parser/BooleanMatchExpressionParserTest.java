package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class BooleanMatchExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    StrictTypedBooleanMatchExpressionParser matchExpressionParser = new StrictTypedBooleanMatchExpressionParser();
    testAllMatch(matchExpressionParser, "match{1==1->1==1,default->1==0}");
    testAllMatch(matchExpressionParser, "match{1==1->$val,default->$val as boolean}");
    testAllMatch(matchExpressionParser, "match{1==1->$val,default->1==0}");
    //typeを特定するhintがない
    testUnMatch(matchExpressionParser, "match{1==1->$val,default->$default}");
  }

}
