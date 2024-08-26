package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public interface ObjectResultConsumer{
  
  void accept(CalculationContext calculationContext,
      Calculator calclator , FormulaInfo formulaInfo , Object result);
}