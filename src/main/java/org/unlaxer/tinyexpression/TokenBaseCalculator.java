package org.unlaxer.tinyexpression;

public interface TokenBaseCalculator extends TokenBaseOperator<CalculationContext>{
  
  //最終的にはTokenをパラメータとして持たないようにする。現在はbyteCodeのhashを比較する要件があるためそのよう圏外らなくなり次第移行する
//  public Float apply(CalculationContext calculationContext);
}