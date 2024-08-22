package org.unlaxer.tinyexpression.loader.model;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface CalculatorCreator{
  Calculator<?> create(String formulaText, String className, 
      ExpressionType resultType, ClassLoader classLoader);
}