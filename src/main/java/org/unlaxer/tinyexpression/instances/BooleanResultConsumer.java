package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public interface BooleanResultConsumer{
  
  void accept(CalculationContext calculationContext,
      Calculator<Boolean> calclator , FormulaInfo formulaInfo , boolean result);
}