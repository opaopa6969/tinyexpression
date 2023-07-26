package org.unlaxer.tinyexpression;

import java.util.Optional;

import org.unlaxer.Token;

public interface OptionalOperator<C,T> extends TokenBaseOperator<C, T>{
  
  public Optional<T> evaluateOptional(C context , Token token);
  
  @Override
  default T evaluate(C context, Token token) {
    return evaluateOptional(context , token)
      .orElseGet(()->defaultValue(context, token));
  }
  
  public T defaultValue(C context, Token token);
}