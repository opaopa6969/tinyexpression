package org.unlaxer.tinyexpression.service;

import java.time.Duration;
import java.util.List;

/**
 * The audit hook of {@link EvalContextService} (issue #221), implemented by the host (e.g. to
 * write the formula and the result to an audit log). Both methods run on the thread that called
 * the service. An exception thrown by the hook propagates to that caller: a request whose audit
 * record could not be written is not answered (and, from {@link #beforeEvaluation}, not
 * evaluated).
 */
public interface EvalAuditHook {

  /**
   * What is about to be evaluated. {@code source} is the formula or FormulaInfo document;
   * {@code codeBlockClasses} the classes its code blocks declare; {@code codeBlocksExecuted}
   * whether the policy let the service compile and run them.
   */
  record Event(EvalOperation operation, String requestJson, String source,
      List<String> codeBlockClasses, boolean codeBlocksExecuted) {}

  /**
   * How it ended: the response, the time taken, whether the per-request timeout cut it off.
   * {@code event} is null when the request itself was invalid (the response is then a
   * {@code "stage":"request"} failure).
   */
  record Outcome(Event event, EvalContextResponse response, Duration elapsed, boolean timedOut) {}

  /** Called after the request was read and before anything is compiled or evaluated. */
  default void beforeEvaluation(Event event) {}

  /** Called once per request, with the response the service is about to return. */
  default void afterEvaluation(String requestJson, Outcome outcome) {}

  /** A hook that records nothing (the default). */
  EvalAuditHook NONE = new EvalAuditHook() {};
}
