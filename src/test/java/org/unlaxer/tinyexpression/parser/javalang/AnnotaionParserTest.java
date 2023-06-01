package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class AnnotaionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    
    AnnotaionParser parser = new AnnotaionParser();
   
    testAllMatch(parser,"@annotation()");
    testAllMatch(parser,"@annotation(niku='beaf')");
    testAllMatch(parser,"@annotation(niku='meat' , age = 63)");
    testAllMatch(parser,"@annotation(niku='meat' , age = 60+3)");
    testAllMatch(parser,"@annotation(niku='meat' , age = 63 , isDead=true)");
    testAllMatch(parser,"@annotation(niku='meat' , age = 63 , isDead=(true))");
    testAllMatch(parser,"@annotation(niku='meat' , age = 63 , isDead=1==1)");
    testUnMatch(parser,"@annotation(niku='meat' , age = 63 , isDead=1=1)");
    testUnMatch(parser,"@annotation(niku='meat' , age = 63 , isDead=(1===1))");
    testUnMatch(parser,"@annotation");
  }

}
