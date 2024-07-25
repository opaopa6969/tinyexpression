package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public interface StringResultConsumer{
  
  void accept(CalculationContext calculationContext,
      Calculator<String> calclator , FormulaInfo formulaInfo , String result);
}