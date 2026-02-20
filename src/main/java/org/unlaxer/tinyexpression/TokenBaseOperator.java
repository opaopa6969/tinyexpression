package org.unlaxer.tinyexpression;
import org.unlaxer.Token;

public interface TokenBaseOperator<C>  extends CalculatorOperator<C, Token> {

  public Object evaluate(C context , Token token);

  @Override
  public default Object apply(C context , Token token) {
    return evaluate(context, token);
  }

}
