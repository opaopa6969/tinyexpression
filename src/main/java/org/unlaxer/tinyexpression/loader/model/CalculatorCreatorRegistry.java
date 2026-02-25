package org.unlaxer.tinyexpression.loader.model;

import java.util.List;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.ast.AstEvaluatorCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public final class CalculatorCreatorRegistry {

  private CalculatorCreatorRegistry() {}

  public static CalculatorCreator forBackend(ExecutionBackend backend) {
    if (backend == ExecutionBackend.AST_EVALUATOR) {
      return astEvaluatorCreator();
    }
    if (backend == ExecutionBackend.DSL_JAVA_CODE) {
      return dslJavaCodeCreator();
    }
    return javaCodeCreator();
  }

  public static CalculatorCreator javaCodeCreator() {
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        return new JavaCodeCalculatorV3(source, className, specifiedExpressionTypes, classLoader);
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        return new JavaCodeCalculatorV3(source, javaCode, className, specifiedExpressionTypes,
            byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
      }
    };
  }

  public static CalculatorCreator astEvaluatorCreator() {
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        return new AstEvaluatorCalculator(source, className, specifiedExpressionTypes, classLoader);
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        return new AstEvaluatorCalculator(source, javaCode, className, specifiedExpressionTypes,
            byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
      }
    };
  }

  public static CalculatorCreator dslJavaCodeCreator() {
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        JavaCodeCalculatorV3 calculator =
            new JavaCodeCalculatorV3(source, className, specifiedExpressionTypes, classLoader);
        calculator.setObject("_tinyExecutionMode", "dsl-javacode");
        return calculator;
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        JavaCodeCalculatorV3 calculator =
            new JavaCodeCalculatorV3(source, javaCode, className, specifiedExpressionTypes,
                byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
        calculator.setObject("_tinyExecutionMode", "dsl-javacode");
        return calculator;
      }
    };
  }
}
