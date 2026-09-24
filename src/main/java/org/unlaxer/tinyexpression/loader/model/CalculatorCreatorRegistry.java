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
import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

/**
 * Creates calculators per {@link ExecutionBackend}.
 *
 * <p>Since 2.0.0 the P4 parser engine is a second, orthogonal axis: {@link P4ParserEngine#UBNFC}
 * (default) or {@link P4ParserEngine#LEGACY}. {@link #forBackend(ExecutionBackend, P4ParserEngine)}
 * pins it for the calculators a creator builds; {@code null} defers to
 * {@code -Dtinyexpression.p4.engine}, then to the default. Calculators of backends that parse
 * with the P4 grammar carry the engine that built them in {@value P4ParserEngine#CALCULATOR_MARKER}
 * ({@code "ubnfc"} / {@code "legacy"}); precompiled bytecode is not reparsed and keeps
 * {@code _tinyDslJavaEmitterMode = precompiled-bytecode}.
 */
public final class CalculatorCreatorRegistry {

  private CalculatorCreatorRegistry() {}

  private static Calculator markExecutionBackend(Calculator calculator, ExecutionBackend backend) {
    String implementation = backend.runtimeImplementationMarker();
    boolean bridgeImplementation = backend.bridgeImplementation();
    if (backend == ExecutionBackend.DSL_JAVA_CODE && calculator instanceof DslJavaCodeCalculator dslJavaCodeCalculator) {
      if (dslJavaCodeCalculator.nativeEmitterUsed()) {
        implementation = "p4-typed-emitter";
        bridgeImplementation = false;
      } else if ("precompiled-bytecode".equals(dslJavaCodeCalculator.dslEmitterMode())) {
        implementation = "precompiled-bytecode";
      }
      calculator.setObject("_tinyDslJavaEmitterMode", dslJavaCodeCalculator.dslEmitterMode());
      calculator.setObject("_tinyDslJavaNativeEmitterUsed", dslJavaCodeCalculator.nativeEmitterUsed());
    }
    if (usesP4Parser(backend)) {
      calculator.setObject(P4ParserEngine.CALCULATOR_MARKER, P4ParserEngine.current().id());
    }
    calculator.setObject("_tinyExecutionBackend", backend.name());
    calculator.setObject("_tinyExecutionMode", backend.runtimeModeMarker());
    calculator.setObject("_tinyExecutionImplementation", implementation);
    calculator.setObject("_tinyExecutionBridgeImplementation", bridgeImplementation);
    calculator.setObject("_tinyExecutionNonBridgeImplementation", !bridgeImplementation);
    return calculator;
  }

  /** Whether calculators of {@code backend} are built from the P4 parser's typed AST. */
  public static boolean usesP4Parser(ExecutionBackend backend) {
    return backend == ExecutionBackend.AST_EVALUATOR
        || backend == ExecutionBackend.DSL_JAVA_CODE
        || backend == ExecutionBackend.P4_AST_EVALUATOR
        || backend == ExecutionBackend.P4_DSL_JAVA_CODE;
  }

  /**
   * Like {@link #forBackend(ExecutionBackend)}, with the P4 parser engine pinned for every
   * calculator the returned creator builds. {@code null} means "no override".
   */
  public static CalculatorCreator forBackend(ExecutionBackend backend, P4ParserEngine engine) {
    return withP4ParserEngine(forBackend(backend), engine);
  }

  /** Wraps {@code creator} so that construction (where parsing happens) runs under {@code engine}. */
  public static CalculatorCreator withP4ParserEngine(CalculatorCreator creator, P4ParserEngine engine) {
    if (engine == null) {
      return creator;
    }
    return new CalculatorCreator() {

      @Override
      public Calculator create(Source source, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
        return P4ParserEngine.with(engine,
            () -> creator.create(source, className, specifiedExpressionTypes, classLoader));
      }

      @Override
      public Calculator create(Source source, String javaCode, String className,
          SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
          List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
        return P4ParserEngine.with(engine,
            () -> creator.create(source, javaCode, className, specifiedExpressionTypes, byteCode,
                byteCodeHash, classNameAndByteCodeList, classLoader));
      }
    };
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
          calc.setObject("_tinyExecutionImplementation", "p4-typed-emitter");
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
