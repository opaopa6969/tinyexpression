package org.unlaxer.tinyexpression.loader.model;

import java.util.List;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.ast.AstEvaluatorCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.DslJavaCodeCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyAstCreatorJavaCodeCalculator;
import org.unlaxer.tinyexpression.evaluator.p4.P4AstEvaluatorCalculator;
import org.unlaxer.tinyexpression.evaluator.p4.P4DslJavaCodeCalculator;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public final class CalculatorCreatorRegistry {

  private CalculatorCreatorRegistry() {}

  private static Calculator markExecutionBackend(Calculator calculator, ExecutionBackend backend) {
    String implementation = backend.runtimeImplementationMarker();
    boolean bridgeImplementation = backend.bridgeImplementation();
    if (backend == ExecutionBackend.DSL_JAVA_CODE && calculator instanceof DslJavaCodeCalculator dslJavaCodeCalculator) {
      if (dslJavaCodeCalculator.nativeEmitterUsed()) {
        implementation = "dsl-javacode-native";
        bridgeImplementation = false;
      }
      calculator.setObject("_tinyDslJavaEmitterMode", dslJavaCodeCalculator.dslEmitterMode());
      calculator.setObject("_tinyDslJavaNativeEmitterUsed", dslJavaCodeCalculator.nativeEmitterUsed());
    }
    calculator.setObject("_tinyExecutionBackend", backend.name());
    calculator.setObject("_tinyExecutionMode", backend.runtimeModeMarker());
    calculator.setObject("_tinyExecutionImplementation", implementation);
    calculator.setObject("_tinyExecutionBridgeImplementation", bridgeImplementation);
    calculator.setObject("_tinyExecutionNonBridgeImplementation", !bridgeImplementation);
    return calculator;
  }

  public static CalculatorCreator forBackend(ExecutionBackend backend) {
    if (backend == ExecutionBackend.AST_EVALUATOR) {
      return astEvaluatorCreator();
    }
    if (backend == ExecutionBackend.DSL_JAVA_CODE) {
      return dslJavaCodeCreator();
    }
    if (backend == ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR) {
      return legacyAstCreatorJavaCodeCreator();
    }
    if (backend == ExecutionBackend.P4_AST_EVALUATOR) {
      return p4AstEvaluatorCreator();
    }
    if (backend == ExecutionBackend.P4_DSL_JAVA_CODE) {
      return p4DslJavaCodeCreator();
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

  public static CalculatorCreator p4AstEvaluatorCreator() {
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        return markExecutionBackend(
            new P4AstEvaluatorCalculator(source, className, specifiedExpressionTypes, classLoader),
            ExecutionBackend.P4_AST_EVALUATOR);
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        return markExecutionBackend(
            new P4AstEvaluatorCalculator(source, javaCode, className, specifiedExpressionTypes,
                byteCode, byteCodeHash, classNameAndByteCodeList, classLoader),
            ExecutionBackend.P4_AST_EVALUATOR);
      }
    };
  }

  public static CalculatorCreator p4DslJavaCodeCreator() {
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        Calculator calc = new P4DslJavaCodeCalculator(source, className, specifiedExpressionTypes, classLoader);
        markExecutionBackend(calc, ExecutionBackend.P4_DSL_JAVA_CODE);
        if (calc instanceof P4DslJavaCodeCalculator p4 && p4.nativeEmitterUsed()) {
          calc.setObject("_tinyExecutionImplementation", "p4-dsl-javacode-native");
          calc.setObject("_tinyExecutionBridgeImplementation", false);
        }
        return calc;
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        Calculator calc = new P4DslJavaCodeCalculator(source, javaCode, className, specifiedExpressionTypes,
            byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
        markExecutionBackend(calc, ExecutionBackend.P4_DSL_JAVA_CODE);
        return calc;
      }
    };
  }

  public static CalculatorCreator legacyAstCreatorJavaCodeCreator() {
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        return markExecutionBackend(
            new LegacyAstCreatorJavaCodeCalculator(source, className, specifiedExpressionTypes, classLoader),
            ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR);
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        return markExecutionBackend(
            new LegacyAstCreatorJavaCodeCalculator(source, javaCode, className, specifiedExpressionTypes,
                byteCode, byteCodeHash, classNameAndByteCodeList, classLoader),
            ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR);
      }
    };
  }
}
