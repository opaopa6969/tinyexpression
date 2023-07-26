package org.unlaxer.tinyexpression;
import org.unlaxer.Token;

public interface TokenBaseOperator<C,T>  extends CalculatorOperator<C, Token, T>{

  public T evaluate(C context , Token token);
  
  @Override
  public default T apply(C context , Token token) {
    return evaluate(context, token);
  }
  
}
