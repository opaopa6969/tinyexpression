package org.unlaxer.tinyexpression.service;

/**
 * The three request kinds of {@link EvalContextService} (issue #221) and their Rust
 * counterparts. {@link #wireName()} is the value of the optional {@code "operation"} field of a
 * request sent to {@link EvalContextService#dispatch(String)}; it is also the method name of the
 * playground's runtime ({@code playground/src/te-runtime.js}).
 */
public enum EvalOperation {
  /** Rust {@code te_eval_context} / CLI {@code eval-context}: request field {@code formula}. */
  EVAL_CONTEXT("evalContext", "formula"),
  /** Rust {@code te_eval_trace} / CLI {@code eval-context --trace}: request field {@code formula}. */
  EVAL_TRACE("evalTrace", "formula"),
  /** Rust {@code te_formula_info_context} / CLI {@code run-context}: request field {@code document}. */
  FORMULA_INFO_CONTEXT("runContext", "document");

  private final String wireName;
  private final String sourceField;

  EvalOperation(String wireName, String sourceField) {
    this.wireName = wireName;
    this.sourceField = sourceField;
  }

  public String wireName() {
    return wireName;
  }

  /** The request field that carries the source: {@code formula} or {@code document}. */
  public String sourceField() {
    return sourceField;
  }

  /** The operation of a wire name, or null. */
  public static EvalOperation ofWireName(String name) {
    for (EvalOperation operation : values()) {
      if (operation.wireName.equals(name)) {
        return operation;
      }
    }
    return null;
  }
}
