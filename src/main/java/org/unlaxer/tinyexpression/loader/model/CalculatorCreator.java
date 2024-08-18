package org.unlaxer.tinyexpression.loader.model;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.evaluator.javacode.ResultType;

public interface CalculatorCreator{
  Calculator<?> create(String formulaText, String className, 
      ResultType resultType, ClassLoader classLoader);
}