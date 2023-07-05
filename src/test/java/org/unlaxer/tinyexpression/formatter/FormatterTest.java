package org.unlaxer.tinyexpression.formatter;

import org.junit.Test;

public class FormatterTest {

  @Test
  public void test() {
    String string = "if(isPresent($accountBalance)){if(external:jp.caulis.external.MoneyTransfer#getDestinationAccountNumberOfCurrentSourceAccountWithSpecifiedTransferAmountWithinSpecifiedPeriod(10000,3600)>=3){1}else{0}}else{0}";
    String format = Formatter.format(string);
    System.out.println(format);
  }

}
