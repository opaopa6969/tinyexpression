package org.unlaxer.tinyexpression.dap;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

/**
 * Runtime bridge for generated DAP adapters.
 * <p>
 * The bridge is intentionally best-effort and never throws; on failure it returns
 * diagnostic keys in the returned map.
 */
public final class TinyExpressionDapRuntimeBridge {

  private static final int VALUE_LIMIT = 200;

  private TinyExpressionDapRuntimeBridge() {}

  public static Map<String, String> debugVariables(String formulaSource, String runtimeMode) {
    LinkedHashMap<String, String> vars = new LinkedHashMap<>();
    vars.put("bridgeAttached", "true");
    vars.put("requestedRuntimeMode", normalize(runtimeMode));

    ExecutionBackend backend =
        ExecutionBackend.fromRuntimeMode(runtimeMode).orElse(ExecutionBackend.JAVA_CODE);
    vars.put("selectedExecutionBackend", backend.name());

    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      SpecifiedExpressionTypes types =
          new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float);
      Calculator calculator = CalculatorCreatorRegistry.forBackend(backend).create(
          new Source(formulaSource == null ? "" : formulaSource),
          "TinyExpressionDapRuntimeBridgeProbe",
          types,
          classLoader);
      vars.put("calculatorClass", truncate(calculator.getClass().getName()));

      copyMarker(calculator, "_tinyExecutionBackend", vars);
      copyMarker(calculator, "_tinyExecutionMode", vars);
      copyMarker(calculator, "_tinyExecutionImplementation", vars);
      copyMarker(calculator, "_tinyExecutionBridgeImplementation", vars);
      copyMarker(calculator, "_tinyExecutionNonBridgeImplementation", vars);
      copyMarker(calculator, "_tinyDslJavaEmitterMode", vars);
      copyMarker(calculator, "_tinyDslJavaNativeEmitterUsed", vars);

      try {
        Object value = calculator.apply(CalculationContext.newConcurrentContext());
        vars.put("evaluationResult", truncate(String.valueOf(value)));
        vars.put("evaluationResultType", truncate(value == null ? "null" : value.getClass().getName()));
        vars.put("evaluationResultNormalized", truncate(normalizeResult(value)));
      } catch (Throwable applyError) {
        vars.put("evaluationError",
            truncate(applyError.getClass().getSimpleName() + ":" + safeMessage(applyError)));
      }

      copyMarker(calculator, "_astEvaluatorRuntime", vars);
      copyMarker(calculator, "_astEvaluatorMapperAvailable", vars);
      copyMarker(calculator, "_astEvaluatorGeneratedAstNodeCount", vars);
      copyMarker(calculator, "_astEvaluatorGeneratedEmbeddedBridgeUsed", vars);
      copyMappedAstType(calculator, vars);
      copyMarker(calculator, "_tinyP4ParserUsed", vars);
      copyMarker(calculator, "_tinyP4AstNodeType", vars);
      collectParityProbe(formulaSource, classLoader, vars);
    } catch (Throwable createError) {
      vars.put("bridgeError", truncate(
          createError.getClass().getSimpleName() + ":" + safeMessage(createError)));
    }
    return vars;
  }

  private static void copyMarker(Calculator calculator, String key, Map<String, String> target) {
    try {
      Object value = calculator.getObject(key, Object.class);
      if (value != null) {
        target.put(key, truncate(String.valueOf(value)));
      }
    } catch (Throwable ignored) {
    }
  }

  private static void copyMappedAstType(Calculator calculator, Map<String, String> target) {
    try {
      Object mappedAst = calculator.getObject("_astEvaluatorMappedAst", Object.class);
      if (mappedAst != null) {
        target.put("mappedAstType", truncate(mappedAst.getClass().getName()));
      }
    } catch (Throwable ignored) {
    }
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    return message == null ? "" : message;
  }

  private static String normalize(String text) {
    if (text == null) {
      return "";
    }
    return text.strip().toLowerCase().replace('_', '-');
  }

  private static String truncate(String text) {
    if (text == null) {
      return "";
    }
    String normalized = text.replace('\n', ' ').replace('\r', ' ');
    if (normalized.length() <= VALUE_LIMIT) {
      return normalized;
    }
    return normalized.substring(0, VALUE_LIMIT) + "...";
  }

  private static void collectParityProbe(String formulaSource, ClassLoader classLoader, Map<String, String> vars) {
    String formula = formulaSource == null ? "" : formulaSource;
    String legacyNormalized = null;
    String legacyAstCreatorNormalized = null;
    String astNormalized = null;
    String dslNormalized = null;
    String p4AstNormalized = null;
    String p4DslNormalized = null;
    for (ExecutionBackend backend : new ExecutionBackend[] {
        ExecutionBackend.JAVA_CODE,
        ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR,
        ExecutionBackend.AST_EVALUATOR,
        ExecutionBackend.DSL_JAVA_CODE,
        ExecutionBackend.P4_AST_EVALUATOR,
        ExecutionBackend.P4_DSL_JAVA_CODE
    }) {
      String prefix = "parity." + backend.name() + ".";
      try {
        SpecifiedExpressionTypes types =
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float);
        Calculator calculator = CalculatorCreatorRegistry.forBackend(backend).create(
            new Source(formula),
            "TinyExpressionDapRuntimeBridgeParityProbe",
            types,
            classLoader);
        Object value = calculator.apply(CalculationContext.newConcurrentContext());
        String normalized = normalizeResult(value);
        vars.put(prefix + "value", truncate(String.valueOf(value)));
        vars.put(prefix + "type", truncate(value == null ? "null" : value.getClass().getName()));
        vars.put(prefix + "normalized", truncate(normalized));
        if (backend == ExecutionBackend.JAVA_CODE) {
          legacyNormalized = normalized;
        } else if (backend == ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR) {
          legacyAstCreatorNormalized = normalized;
        } else if (backend == ExecutionBackend.AST_EVALUATOR) {
          astNormalized = normalized;
        } else if (backend == ExecutionBackend.DSL_JAVA_CODE) {
          dslNormalized = normalized;
        } else if (backend == ExecutionBackend.P4_AST_EVALUATOR) {
          p4AstNormalized = normalized;
        } else if (backend == ExecutionBackend.P4_DSL_JAVA_CODE) {
          p4DslNormalized = normalized;
        }
      } catch (Throwable error) {
        vars.put(prefix + "error", truncate(
            error.getClass().getSimpleName() + ":" + safeMessage(error)));
      }
    }
    boolean parityComplete = legacyNormalized != null
        && legacyAstCreatorNormalized != null
        && astNormalized != null
        && dslNormalized != null;
    vars.put("parity.allBackendsEvaluated", String.valueOf(parityComplete));
    vars.put("parity.p4BackendsEvaluated", String.valueOf(
        p4AstNormalized != null && p4DslNormalized != null));
    if (parityComplete) {
      vars.put("parity.equalAll", String.valueOf(
          Objects.equals(legacyNormalized, legacyAstCreatorNormalized)
              && Objects.equals(legacyNormalized, astNormalized)
              && Objects.equals(legacyNormalized, dslNormalized)));
    }
    if (parityComplete && p4AstNormalized != null && p4DslNormalized != null) {
      vars.put("parity.equalAllWithP4", String.valueOf(
          Objects.equals(legacyNormalized, legacyAstCreatorNormalized)
              && Objects.equals(legacyNormalized, astNormalized)
              && Objects.equals(legacyNormalized, dslNormalized)
              && Objects.equals(legacyNormalized, p4AstNormalized)
              && Objects.equals(legacyNormalized, p4DslNormalized)));
    }
  }

  private static String normalizeResult(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Number number) {
      try {
        return new BigDecimal(String.valueOf(number)).stripTrailingZeros().toPlainString();
      } catch (Throwable ignored) {
        return String.valueOf(value);
      }
    }
    return String.valueOf(value);
  }
}
