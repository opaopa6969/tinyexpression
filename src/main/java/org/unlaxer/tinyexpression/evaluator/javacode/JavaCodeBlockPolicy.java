package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global policy for Java code block (triple-backtick {@code ```java:ClassName} fence)
 * execution.
 *
 * <p>By default, Java code blocks are <strong>enabled</strong> to preserve backward
 * compatibility with existing users who rely on the dynamic-compilation feature.
 *
 * <p>To opt out of arbitrary code execution — for example when formula sources are
 * supplied by external/untrusted tenants — call {@link #setEnabled(boolean) setEnabled(false)}
 * once at application startup before any {@code JavaCodeCalculatorV3} instances are created:
 *
 * <pre>
 *   JavaCodeBlockPolicy.setEnabled(false);
 * </pre>
 *
 * <p>When disabled, {@code createJavaFromCodedBlock} returns an empty list and code
 * blocks in formulas are silently ignored. The rest of the formula evaluation
 * (arithmetic, variables, if/match, etc.) is unaffected.
 *
 * <p>This class is thread-safe; the flag may be toggled at any point, but changing it
 * while calculators are being constructed concurrently is not recommended.
 *
 * @since 1.4.11
 */
public final class JavaCodeBlockPolicy {

  private static final AtomicBoolean ENABLED = new AtomicBoolean(true);

  private JavaCodeBlockPolicy() {}

  /**
   * Returns whether Java code block execution is currently enabled.
   *
   * @return {@code true} (default) if code blocks are executed; {@code false} if disabled.
   */
  public static boolean isEnabled() {
    return ENABLED.get();
  }

  /**
   * Enables or disables Java code block execution globally.
   *
   * <p>Call with {@code false} to prevent arbitrary code embedded in formula strings
   * from being compiled and executed at runtime.
   *
   * <p>The default value is {@code true} to preserve backward compatibility.
   *
   * @param enabled {@code true} to allow code block execution (default),
   *                {@code false} to disable it.
   */
  public static void setEnabled(boolean enabled) {
    ENABLED.set(enabled);
  }

  /**
   * Resets the policy to the default enabled state ({@code true}).
   * Primarily useful in tests to restore state after a test-scoped opt-out.
   */
  public static void reset() {
    ENABLED.set(true);
  }
}
