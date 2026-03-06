package org.unlaxer.tinyexpression;

import java.util.Optional;

import org.unlaxer.Token;

public interface OptionalOperator<C> extends TokenBaseOperator<C>{
  
  public Optional<Object> evaluateOptional(C context , Token token);
  
  @Override
  default Object evaluate(C context, Token token) {
    return evaluateOptional(context , token)
      .orElseGet(()->defaultValue(context, token));
  }
  
  public Object defaultValue(C context, Token token);
}