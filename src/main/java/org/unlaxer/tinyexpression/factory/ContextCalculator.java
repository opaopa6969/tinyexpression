package org.unlaxer.tinyexpression.factory;

import java.util.function.Function;

import org.unlaxer.tinyexpression.CalculationContext;

@Deprecated
public interface ContextCalculator extends Function<CalculationContext, Float>{
  
  default float calculate(CalculationContext context) {
    
    return apply(context);
  }

  
}