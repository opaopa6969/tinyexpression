package org.unlaxer.tinyexpression.formatter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatterTest {

  @Test
  public void testIfNumber() {
    String string = "if(isPresent($accountBalance)){if(external:jp.caulis.external.MoneyTransfer#getDestinationAccountNumberOfCurrentSourceAccountWithSpecifiedTransferAmountWithinSpecifiedPeriod(10000,3600)>=3){1}else{0}}else{0}";
    String format = Formatter.format(string);
    System.out.println(format);
    assertEquals(9, format.split("\n").length);
  }
  
  @Test
  public void testIfBoolean() {
    String string = "if(if(isPresent($accountBalance)){if(external:jp.caulis.external.MoneyTransfer#getDestinationAccountNumberOfCurrentSourceAccountWithSpecifiedTransferAmountWithinSpecifiedPeriod(10000,3600)>=3){true}else{false}}else{false}){0}else{1}";
    String format = Formatter.format(string);
    System.out.println(format);
    assertEquals(13, format.split("\n").length);
  }
  
  @Test
  public void testIfString() {
    String string = "if('a'==if(isPresent($accountBalance)){if(external:jp.caulis.external.MoneyTransfer#getDestinationAccountNumberOfCurrentSourceAccountWithSpecifiedTransferAmountWithinSpecifiedPeriod(10000,3600)>=3){'a'}else{'b'}}else{'c'}){0}else{1}";
    String format = Formatter.format(string);
    System.out.println(format);
    assertEquals(13, format.split("\n").length);
  }

  
  @Test
  public void testMatchNumber() {
    String string = "match{isPresent($country)->match{$country.in('cn','kr')->5,$country.in('kz','ru')->4,default->0},default->0}";
    String format = Formatter.format(string);
    System.out.println(format);
    assertEquals(8, format.split("\n").length);
  }
  
  @Test
  public void testMatchBoolean() {
    String string = "if(match{isPresent($country)->match{$country.in('cn','kr')->true,$country.in('kz','ru')->true,default->false},default->false}){1}else{0}";
    String format = Formatter.format(string);
    System.out.println(format);
    assertEquals(12, format.split("\n").length);
  }

  @Test
  public void testMatchString() {
    String string = "if('a'==match{isPresent($country)->match{$country.in('cn','kr')->'a',$country.in('kz','ru')->'a',default->'b'},default->'c'}){1}else{0}";
    String format = Formatter.format(string);
    System.out.println(format);
    assertEquals(12, format.split("\n").length);
  }

  @Test
  public void testSideEffect() {
    String string = "if(isPresent($sourceFinancialInstituionCode)&$sourceFinancialInstituionCode=='0035'&$sourceStoreNumber=='001'){if(external:jp.caulis.external.MoneyTransfer#getUniqueTransferAccountNumberOfCurrentDestinationAccountWithinSpecifiedPeriod(86400)>=10){1}else{0}}else{0}";
    String format = Formatter.format(string);
    System.out.println(format);
//    assertEquals(12, format.split("\n").length);
  }
  
  
  @Test
  public void testMultipleConditions() {
    String string = "if($name=='mintia' & $age>=18 & ($gender!='female' | $country=='jp')){0}else{1}";
    String format = Formatter.format(string);
    System.out.println(format);
//    assertEquals(12, format.split("\n").length);
  }


  public static void main(String[] args) {
    String string="if(true){0}else{1}";
    String format = Formatter.format(string);
    System.out.println(format);
  }
  
}
