package org.unlaxer.tinyexpression.service;

import java.util.List;

import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeBlockPolicy;

/**
 * Whether {@link EvalContextService} compiles and runs the Java code blocks
 * ({@code ```java:ClassName}) of a request (issue #221). The host supplies it; the default is
 * {@link #DENY}, the same secure default as {@link JavaCodeBlockPolicy} (ADR-003).
 *
 * <p>A denied request is still evaluated: its code blocks only declare their classes, and calls
 * to them are answered by the request's {@code externals[]} stubs, exactly as the Rust / wasm
 * evaluator does. An allowed request compiles every {@code java} block in memory and calls the
 * compiled class; the real class takes precedence over a stub of the same name.
 *
 * <p><strong>Warning</strong>: Java code blocks compile and execute arbitrary code on the JVM.
 * Only allow them when formula authors are fully trusted. Do not expose this capability to
 * untrusted users (docs/decisions/ADR-003-java-codeblock-safety.md).
 */
@FunctionalInterface
public interface CodeBlockExecutionPolicy {

  /**
   * What the policy decides on: the operation, the formula (or FormulaInfo document) and the
   * classes its code blocks declare, in source order (never empty when the policy is asked).
   */
  record Request(EvalOperation operation, String source, List<String> classes) {}

  boolean allows(Request request);

  /** Never runs code blocks (the default). */
  CodeBlockExecutionPolicy DENY = request -> false;

  /** Always runs code blocks: only for fully trusted formula authors. */
  CodeBlockExecutionPolicy ALLOW = request -> true;

  /** Follows the global {@link JavaCodeBlockPolicy#isEnabled()} at the time of each request. */
  CodeBlockExecutionPolicy FOLLOW_GLOBAL = request -> JavaCodeBlockPolicy.isEnabled();
}
