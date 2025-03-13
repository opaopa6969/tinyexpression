package org.unlaxer.tinyexpression.loader.model;

import java.util.List;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;

public interface CalculatorCreator{
  Calculator create(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader);


  Calculator create(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes,
      byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList,
      ClassLoader classLoader);
}