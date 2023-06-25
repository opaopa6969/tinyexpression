package org.unlaxer.tinyexpression;

public class Fee{
  public float calculate(CalculationContext calculationContext , float age , float fee , float taxRate) {
    if(age<18) {
      return 0;
    }
    return fee+fee*taxRate;
  }
}