package org.unlaxer.tinyexpression.dap;

import java.util.LinkedHashMap;
import java.util.Map;

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

      try {
        Object value = calculator.apply(CalculationContext.newConcurrentContext());
        vars.put("evaluationResult", truncate(String.valueOf(value)));
      } catch (Throwable applyError) {
        vars.put("evaluationError",
            truncate(applyError.getClass().getSimpleName() + ":" + safeMessage(applyError)));
      }

      copyMarker(calculator, "_astEvaluatorRuntime", vars);
      copyMarker(calculator, "_astEvaluatorMapperAvailable", vars);
      copyMarker(calculator, "_astEvaluatorGeneratedAstNodeCount", vars);
      copyMappedAstType(calculator, vars);
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
}
