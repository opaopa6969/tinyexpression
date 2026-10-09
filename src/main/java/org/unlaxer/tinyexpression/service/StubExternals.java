package org.unlaxer.tinyexpression.service;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.unlaxer.tinyexpression.CalculationException;
import org.unlaxer.tinyexpression.evaluator.ast.ExternalInvocationHandler;

/**
 * The {@code externals[]} constant stubs of a request (issue #221), answering {@code external}
 * calls exactly as the Rust {@code StubExternals} does: a class is loadable only when a stub
 * names it (otherwise the {@code Class.forName} failure of Java), a method is found by name and
 * (when given) arity, {@code registered: false} is a class with no instance in the context.
 *
 * <p>Classes in {@code realClasses} (the code blocks the policy let the service compile) are
 * not handled: the evaluator calls the compiled class by reflection. Every other class goes
 * through the stubs, so no class of the host is reachable from a request.
 */
final class StubExternals implements ExternalInvocationHandler {

  static final class Stub {
    final String className;
    final String method;
    final Integer arity;
    final boolean registered;
    final Object result;

    Stub(String className, String method, Integer arity, boolean registered, Object result) {
      this.className = className;
      this.method = method;
      this.arity = arity;
      this.registered = registered;
      this.result = result;
    }
  }

  private final List<Stub> stubs;
  private final Set<String> realClasses;
  private final Set<String> codeBlockClasses;

  StubExternals(List<Stub> stubs) {
    this(stubs, Set.of(), Set.of());
  }

  private StubExternals(List<Stub> stubs, Set<String> realClasses, Set<String> codeBlockClasses) {
    this.stubs = stubs;
    this.realClasses = realClasses;
    this.codeBlockClasses = codeBlockClasses;
  }

  /**
   * These stubs for a formula whose code blocks declare {@code codeBlockClasses}, of which
   * {@code realClasses} were compiled (membership only, never iterated).
   */
  StubExternals forFormula(Set<String> codeBlockClasses, Set<String> realClasses) {
    return new StubExternals(stubs, realClasses, codeBlockClasses);
  }

  @Override
  public boolean handles(String className) {
    return !realClasses.contains(className);
  }

  @Override
  public Object invoke(String className, String methodName, Supplier<List<Object>> arguments) {
    boolean exists = stubs.stream().anyMatch(stub -> stub.className.equals(className));
    if (!exists) {
      String message = "External invocation failed: " + className + "#" + methodName;
      if (codeBlockClasses.contains(className)) {
        message += missingStubHint(className, methodName);
      }
      throw new UnsupportedOperationException(message, new ClassNotFoundException(className));
    }
    List<Object> values = arguments.get();
    Stub found = null;
    for (Stub stub : stubs) {
      if (stub.className.equals(className) && methodName.equals(stub.method)
          && (stub.arity == null || stub.arity == values.size())) {
        found = stub;
        break;
      }
    }
    if (found == null) {
      throw new UnsupportedOperationException("Method not found: " + className + "#" + methodName);
    }
    if (!found.registered) {
      throw new CalculationException(
          "class not found in CalculationContext. please set :" + className);
    }
    return found.result;
  }

  /**
   * The hint the Rust runtime adds to the {@code Class.forName} failure of a code-block class
   * without a stub ({@code runtime::code_block::missing_stub_hint}); here it means the service
   * did not run the block because its policy does not allow code-block execution.
   */
  static String missingStubHint(String className, String methodName) {
    return " (the class is declared by a ```java:" + className + " code block, which this evaluator"
        + " does not compile or run; コードブロックのクラスは externals で値を指定してください: add "
        + "{\"class\":\"" + className + "\",\"method\":\"" + methodName
        + "\",\"result\":{\"type\":...,\"value\":...}} to the request's externals[])";
  }
}
