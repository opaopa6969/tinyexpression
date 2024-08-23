package org.unlaxer.tinyexpression.loader.model;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;

public interface CalculatorCreator{
  Calculator<?> create(String formulaText, String className, 
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader);
}