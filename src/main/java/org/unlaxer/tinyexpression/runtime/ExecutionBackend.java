package org.unlaxer.tinyexpression.runtime;

import java.util.Locale;
import java.util.Optional;

public enum ExecutionBackend {
  JAVA_CODE,
  JAVA_CODE_LEGACY_ASTCREATOR,
  AST_EVALUATOR,
  DSL_JAVA_CODE;

  public String runtimeModeMarker() {
    return switch (this) {
      case JAVA_CODE -> "javacode";
      case JAVA_CODE_LEGACY_ASTCREATOR -> "legacy-astcreator";
      case AST_EVALUATOR -> "ast-evaluator";
      case DSL_JAVA_CODE -> "dsl-javacode";
    };
  }

  public String runtimeImplementationMarker() {
    return switch (this) {
      case JAVA_CODE -> "legacy-javacode";
      case JAVA_CODE_LEGACY_ASTCREATOR -> "legacy-javacode-astcreator";
      case AST_EVALUATOR -> "ast-evaluator";
      case DSL_JAVA_CODE -> "legacy-javacode-bridge";
    };
  }

  public boolean bridgeImplementation() {
    return this == DSL_JAVA_CODE;
  }

  public static Optional<ExecutionBackend> fromRuntimeMode(String runtimeMode) {
    if (runtimeMode == null || runtimeMode.isBlank()) {
      return Optional.empty();
    }
    String normalized = runtimeMode.strip().toLowerCase(Locale.ROOT).replace('_', '-');
    if ("token".equals(normalized) || "javacode".equals(normalized) || "java-code".equals(normalized)) {
      return Optional.of(JAVA_CODE);
    }
    if ("legacy-astcreator".equals(normalized)
        || "legacy-ast-creator".equals(normalized)
        || "ootc-legacy".equals(normalized)
        || "astcreator".equals(normalized)
        || "ootc".equals(normalized)) {
      return Optional.of(JAVA_CODE_LEGACY_ASTCREATOR);
    }
    if ("ast".equals(normalized) || "ast-evaluator".equals(normalized)) {
      return Optional.of(AST_EVALUATOR);
    }
    if ("dsl-javacode".equals(normalized) || "dsl-java-code".equals(normalized)) {
      return Optional.of(DSL_JAVA_CODE);
    }
    return parse(runtimeMode);
  }

  public static Optional<ExecutionBackend> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    normalized = normalized
        .replace('-', '_')
        .replace(' ', '_')
        .toUpperCase(Locale.ROOT);
    String compact = normalized.replace("_", "");
    if ("DSLJAVACODE".equals(compact)) {
      return Optional.of(DSL_JAVA_CODE);
    }
    if ("JAVACODE".equals(compact)) {
      return Optional.of(JAVA_CODE);
    }
    if ("JAVACODELEGACYASTCREATOR".equals(compact)
        || "LEGACYASTCREATOR".equals(compact)
        || "OOTCLEGACY".equals(compact)) {
      return Optional.of(JAVA_CODE_LEGACY_ASTCREATOR);
    }
    if ("ASTEVALUATOR".equals(compact)) {
      return Optional.of(AST_EVALUATOR);
    }
    try {
      return Optional.of(ExecutionBackend.valueOf(normalized));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
