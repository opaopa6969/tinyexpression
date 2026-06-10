package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global policy for Java code block (triple-backtick {@code ```java:ClassName} fence)
 * execution.
 *
 * <p>By default, Java code blocks are <strong>disabled</strong>. Applications that rely on
 * dynamic compilation of embedded Java code must explicitly opt in by calling
 * {@link #setEnabled(boolean) setEnabled(true)} once at application startup, before any
 * {@code JavaCodeCalculatorV3} instances are created:
 *
 * <pre>
 *   JavaCodeBlockPolicy.setEnabled(true);
 * </pre>
 *
 * <p>When disabled, formulas that contain a Java code block are rejected with a
 * {@link org.unlaxer.compiler.CompileError} so that callers are aware that part of
 * their formula was not evaluated.  The rest of the formula evaluation
 * (arithmetic, variables, if/match, etc.) is unaffected only when no code block is present.
 *
 * <p>This class is thread-safe; the flag may be toggled at any point, but changing it
 * while calculators are being constructed concurrently is not recommended.
 *
 * @since 1.4.11
 */
public final class JavaCodeBlockPolicy {

  private static final AtomicBoolean ENABLED = new AtomicBoolean(false);

  private JavaCodeBlockPolicy() {}

  /**
   * Returns whether Java code block execution is currently enabled.
   *
   * @return {@code true} if code blocks are executed; {@code false} (default) if disabled.
   */
  public static boolean isEnabled() {
    return ENABLED.get();
  }

  /**
   * Enables or disables Java code block execution globally.
   *
   * <p>Call with {@code true} to allow arbitrary code embedded in formula strings
   * to be compiled and executed at runtime. Only do this when formula sources are trusted.
   *
   * <p>The default value is {@code false} (secure by default). Formulas containing a
   * Java code block will be rejected with a {@link org.unlaxer.compiler.CompileError}
   * until this flag is explicitly set to {@code true}.
   *
   * @param enabled {@code true} to allow code block execution (explicit opt-in),
   *                {@code false} to disable it (default).
   */
  public static void setEnabled(boolean enabled) {
    ENABLED.set(enabled);
  }

  /**
   * Resets the policy to the default disabled state ({@code false}).
   * Primarily useful in tests to restore state after a test-scoped opt-in.
   */
  public static void reset() {
    ENABLED.set(false);
  }
}
