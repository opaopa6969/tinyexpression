package org.unlaxer.tinyexpression.loader.model;

import java.util.List;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.ast.AstEvaluatorCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.DslJavaCodeCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public final class CalculatorCreatorRegistry {

  private CalculatorCreatorRegistry() {}

  private static Calculator markExecutionBackend(Calculator calculator, ExecutionBackend backend) {
    calculator.setObject("_tinyExecutionBackend", backend.name());
    calculator.setObject("_tinyExecutionMode", backend.runtimeModeMarker());
    calculator.setObject("_tinyExecutionImplementation", backend.runtimeImplementationMarker());
    calculator.setObject("_tinyExecutionBridgeImplementation", backend.bridgeImplementation());
    calculator.setObject("_tinyExecutionNonBridgeImplementation", !backend.bridgeImplementation());
    return calculator;
  }

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
        return markExecutionBackend(
            new JavaCodeCalculatorV3(source, className, specifiedExpressionTypes, classLoader),
            ExecutionBackend.JAVA_CODE);
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        return markExecutionBackend(
            new JavaCodeCalculatorV3(source, javaCode, className, specifiedExpressionTypes,
                byteCode, byteCodeHash, classNameAndByteCodeList, classLoader),
            ExecutionBackend.JAVA_CODE);
      }
    };
  }

  public static CalculatorCreator astEvaluatorCreator() {
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        return markExecutionBackend(
            new AstEvaluatorCalculator(source, className, specifiedExpressionTypes, classLoader),
            ExecutionBackend.AST_EVALUATOR);
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        return markExecutionBackend(
            new AstEvaluatorCalculator(source, javaCode, className, specifiedExpressionTypes,
                byteCode, byteCodeHash, classNameAndByteCodeList, classLoader),
            ExecutionBackend.AST_EVALUATOR);
      }
    };
  }

  public static CalculatorCreator dslJavaCodeCreator() {
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        return markExecutionBackend(
            new DslJavaCodeCalculator(source, className, specifiedExpressionTypes, classLoader),
            ExecutionBackend.DSL_JAVA_CODE);
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        return markExecutionBackend(
            new DslJavaCodeCalculator(source, javaCode, className, specifiedExpressionTypes,
                byteCode, byteCodeHash, classNameAndByteCodeList, classLoader),
            ExecutionBackend.DSL_JAVA_CODE);
      }
    };
  }
}
