package org.unlaxer.tinyexpression.factory;

import org.unlaxer.tinyexpression.CalculationContext;

@Deprecated
public class SimpleContextCalculator implements ExtendedContextCalculator{
  
  public final String formula;
  public final String javaCode;
  public final byte[] byteCode;
  
  ContextCalculator inner;
  
  public SimpleContextCalculator(String formula , String javaCode , byte[] byteCode , 
      ContextCalculator calculator) {
    super();
    this.formula = formula;
    this.inner = calculator;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
  }

  @Override
  public Float apply(CalculationContext context) {
    return inner.apply(context);
  }

  @Override
  public String formula() {
    return formula;
  }

  @Override
  public String javaCode() {
    return javaCode;
  }

  @Override
  public byte[] byteCode() {
    return byteCode;
  }
}