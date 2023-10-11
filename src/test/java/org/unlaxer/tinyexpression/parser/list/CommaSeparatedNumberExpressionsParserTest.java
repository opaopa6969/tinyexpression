package org.unlaxer.tinyexpression.parser.list;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class CommaSeparatedNumberExpressionsParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.mostDetail);
    
    var parser = new CommaSeparatedNumberExpressionsParser();
    
    testAllMatch( parser,"10,1,3");
    testAllMatch( parser,"10 ,1/*immediates*/ ,3 * 10//formula");
    testAllMatch( parser,"10 ,1/*immediates*/ ,3 /*aa*/ * 10//formula");
    testUnMatch( parser,"");
  }

}
