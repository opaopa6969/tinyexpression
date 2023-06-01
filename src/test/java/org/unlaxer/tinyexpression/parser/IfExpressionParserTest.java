package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;

public class IfExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    IfExpressionParser ifExpressionParser = new IfExpressionParser();
    
    testAllMatch(ifExpressionParser, "if(true){1}else{2}");
    testAllMatch(ifExpressionParser, "if(true){$foo}else{$boo}");
    testAllMatch(ifExpressionParser, "if(true){$foo}else{$boo/** comment */}");
    testAllMatch(ifExpressionParser, "if(true){$foo}else{$boo/** comment \n niku*/}");
    testAllMatch(ifExpressionParser, "if(true){$foo}else{$boo// niku\n}");
  }

}
