package org.unlaxer.tinyexpression;

public interface TokenBaseCalculator<T> extends TokenBaseOperator<CalculationContext, T>{
  
  //最終的にはTokenをパラメータとして持たないようにする。現在はbyteCodeのhashを比較する要件があるためそのよう圏外らなくなり次第移行する
//  public Float apply(CalculationContext calculationContext);
}