package org.unlaxer.tinyexpression.evaluator.ast;

import java.util.List;
import java.util.function.Supplier;

/**
 * Optional replacement for the reflective {@code external} invocation of
 * {@link P4TypedAstEvaluator} (issue #221).
 *
 * <p>When a {@link org.unlaxer.tinyexpression.CalculationContext} holds a handler under
 * {@link #CONTEXT_KEY} (via {@code setObject}), every {@code external} call whose resolved class
 * the handler {@linkplain #handles(String) handles} goes to
 * {@link #invoke(String, String, Supplier)} instead of {@code Class.forName} and reflection.
 * Classes it does not handle take the usual reflective path. Without a handler in the context
 * the evaluator behaves exactly as before.
 *
 * <p>{@code org.unlaxer.tinyexpression.service.EvalContextService} uses this to answer calls
 * with the constant stubs of an evaluation request ({@code externals[]}), the same way the Rust
 * runtime does, so that no class of the host is reachable from a request.
 */
public interface ExternalInvocationHandler {

  /** The {@code CalculationContext} object key the evaluator looks the handler up with. */
  String CONTEXT_KEY = ExternalInvocationHandler.class.getName();

  /** Whether calls to {@code className} go to {@link #invoke}. */
  boolean handles(String className);

  /**
   * Answers one call. The Java evaluator loads the class before it evaluates the arguments, so
   * a handler that emulates a missing class throws before calling {@code arguments.get()}.
   *
   * @param className the resolved (imported) class name
   * @param methodName the resolved method name
   * @param arguments evaluates the call's arguments (in order) when first called
   * @return the raw result; the evaluator coerces it to the expected type as for reflection
   */
  Object invoke(String className, String methodName, Supplier<List<Object>> arguments);
}
