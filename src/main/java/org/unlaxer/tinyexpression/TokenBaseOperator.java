package org.unlaxer.tinyexpression;
import org.unlaxer.Token;

public interface TokenBaseOperator<C,T>  extends CalculatorOperator<C, Token, T>{

  public T evaluate(C context , Token token);

  public default T evaluate(C context) {
    return evaluate(context,getRootToken());
  }

  @Override
  public default T apply(C context , Token token) {
    return evaluate(context, token);
  }
  
  public default T apply(C context) {
    return evaluate(context, getRootToken());
  }
  
  public Token getRootToken();
  
}
