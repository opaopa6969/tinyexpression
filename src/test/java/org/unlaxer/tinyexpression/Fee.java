package org.unlaxer.tinyexpression;

public class Fee{
  public float calculate(CalculationContext calculationContext , float age , float fee , float taxRate) {
    if(age<18) {
      return 0;
    }
    return fee+fee*taxRate;
  }
  
  public float calculate(CalculationContext calculationContext , float age , float fee , float taxRate ,
      boolean free , String name) {
    if(free) {
      return 0;
    }
    if(name.contains("猫")) {
      return -1000;
    }
    if(age<18) {
      fee = fee*0.5f;
    }
    return fee+fee*taxRate;
  }
}