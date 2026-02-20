package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;

public class IfExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    NumberIfExpressionParser ifExpressionParser = new NumberIfExpressionParser();
    testAllMatch(ifExpressionParser, "if('niku' == if(1==1){'niku'}else{'sushi'}){100}else{0}");
    testAllMatch(ifExpressionParser, "if(false == if(1==1){false}else{true}){100}else{0}");
    testAllMatch(ifExpressionParser, "if(true){if(true){1}else{2}}else{2}");
    testAllMatch(ifExpressionParser, "if(true){1}else{2}");
    testAllMatch(ifExpressionParser, "if(true){1}else{$boo}");
    testUnMatch(ifExpressionParser, "if(true){$foo}else{$boo}");
    testAllMatch(ifExpressionParser, "if(true){$foo}else{$boo as number}");
    testAllMatch(ifExpressionParser, "if(true){$foo as float}else{$boo as number}");
    testAllMatch(ifExpressionParser, "if(true){$foo as float}else{$boo/** comment */}");
    testAllMatch(ifExpressionParser, "if(true){$foo as float}else{$boo/** comment \n niku*/}");
    testAllMatch(ifExpressionParser, "if(true){$foo as float}else{$boo// niku\n}");
    

    testAllMatch(ifExpressionParser, "if((1==1) == if(1==1){'niku'=='niku'}else{'niku'=='sushi'}){100}else{0}");
    testAllMatch(ifExpressionParser, "if((1==1) == if(1==1){'nikuniku'[0:4]=='niku'}else{1*3==4}){100}else{0}");
    testAllMatch(ifExpressionParser, "if((10==10) == if(1==1){false}else{true}){100}else{0}");
    testAllMatch(ifExpressionParser, "if((10==10) == if(1==1){false} else {true}){100} else {0}");
    testAllMatch(ifExpressionParser, "if(10==20 /*test*/) /*test*/{ /*test*/ 10/*test*/ }/*test*/ else/*test*/ {/*test*/ 0/*test*/}");

  }
}
