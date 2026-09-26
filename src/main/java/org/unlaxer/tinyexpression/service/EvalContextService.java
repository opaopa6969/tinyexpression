package org.unlaxer.tinyexpression.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.unlaxer.compiler.ClassAndByteCode;
import org.unlaxer.compiler.ClassName;
import org.unlaxer.compiler.CompileContext;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.parser.ParseException;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.ast.ExternalInvocationHandler;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.FormulaInfoParseException;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreator;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.loader.model.FormulaInfoList;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;
import org.unlaxer.util.Try;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Server-side evaluation with the real Java evaluator behind the JSON contract of the Rust
 * {@code te_eval_context} / {@code te_eval_trace} / {@code te_formula_info_context}
 * (issue #221; request and response formats: {@code rust/README.md}, "CalculationContext 付き評価").
 *
 * <p>Request JSON text in, response JSON text out: the service knows nothing about HTTP. A host
 * (e.g. an internal server behind its own authentication) passes the body of a POST to
 * {@link #dispatch(String)} and answers with {@link EvalContextResponse#json()}. The formula is
 * evaluated by the {@code P4_AST_EVALUATOR} backend, the semantics the Rust runtime mirrors.
 *
 * <ul>
 *   <li>{@code external} calls are answered by the request's {@code externals[]} stubs as in
 *       Rust; no class of the host is reachable from a request.</li>
 *   <li>Java code blocks run only when the host's {@link CodeBlockExecutionPolicy} allows it
 *       (default {@link CodeBlockExecutionPolicy#DENY}); a compiled class takes precedence over
 *       a stub of the same name. Denied, the blocks only declare their classes (Rust
 *       behaviour).</li>
 *   <li>Each request runs under a timeout (default {@link #DEFAULT_TIMEOUT}); past it the
 *       response is {@code "stage":"timeout"}.</li>
 *   <li>{@link EvalAuditHook} sees every request before evaluation and every response.</li>
 * </ul>
 *
 * <p>Responses carry two fields the Rust responses do not have: {@code "evaluator":"java"} and,
 * when the source has code blocks, {@code "codeBlocks":{"classes":[...],"executed":bool}}.
 * {@code te_eval_trace} requests are answered with {@code "trace":null} plus
 * {@code "traceUnavailable"}: the Java evaluator does not record a trace.
 *
 * <p>Instances are thread-safe. {@link #close()} stops the worker threads the service created
 * (an executor passed to the builder is left to its owner).
 */
public final class EvalContextService implements AutoCloseable {

  /** The per-request timeout unless the builder sets another one. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  /** The {@code traceUnavailable} text of {@code evalTrace} responses. */
  public static final String TRACE_UNAVAILABLE =
      "the Java evaluator does not record an evaluation trace; use the wasm evaluator for the trace";

  private static final AtomicLong CALCULATOR_SEQUENCE = new AtomicLong();

  private final CodeBlockExecutionPolicy codeBlockPolicy;
  private final Duration timeout;
  private final EvalAuditHook auditHook;
  private final ClassLoader classLoader;
  private final Supplier<FormulaInfoAdditionalFields> formulaInfoFields;
  private final ExecutorService hostExecutor;
  private ExecutorService ownExecutor;
  private final CalculatorCreator creator = CalculatorCreatorRegistry.p4AstEvaluatorCreator();

  private EvalContextService(Builder builder) {
    this.codeBlockPolicy = builder.codeBlockPolicy;
    this.timeout = builder.timeout;
    this.auditHook = builder.auditHook;
    this.classLoader = builder.classLoader != null ? builder.classLoader
        : EvalContextService.class.getClassLoader();
    this.formulaInfoFields = builder.formulaInfoFields;
    this.hostExecutor = builder.executor;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Code blocks denied, {@link #DEFAULT_TIMEOUT}, no audit. */
  public static EvalContextService withDefaults() {
    return builder().build();
  }

  /** {@code te_eval_context}: the response JSON of a request with {@code formula}. */
  public String evalContext(String requestJson) {
    return execute(EvalOperation.EVAL_CONTEXT, requestJson).json();
  }

  /** {@code te_eval_trace}: {@link #evalContext} plus {@code "trace":null}. */
  public String evalTrace(String requestJson) {
    return execute(EvalOperation.EVAL_TRACE, requestJson).json();
  }

  /** {@code te_formula_info_context}: the response JSON of a request with {@code document}. */
  public String formulaInfoContext(String requestJson) {
    return execute(EvalOperation.FORMULA_INFO_CONTEXT, requestJson).json();
  }

  /**
   * One HTTP-style entry point: the operation is the request's optional {@code "operation"}
   * field ({@code evalContext} (default), {@code evalTrace}, {@code runContext}, see
   * {@link EvalOperation#wireName()}); the rest of the request is the Rust request.
   */
  public EvalContextResponse dispatch(String requestJson) {
    return execute(null, requestJson);
  }

  /** Evaluates one request of {@code operation}. */
  public EvalContextResponse execute(EvalOperation operation, String requestJson) {
    long started = System.nanoTime();
    ContextRequest request;
    EvalOperation resolved = operation;
    try {
      JsonNode json = ContextRequest.parseJson(requestJson == null ? "" : requestJson);
      if (resolved == null) {
        resolved = operationOf(json);
      }
      request = ContextRequest.read(json, resolved.sourceField());
    } catch (IllegalArgumentException invalid) {
      EvalContextResponse response = new EvalContextResponse(EvalContextResponse.EXIT_USAGE,
          JsonOut.withField("{\"ok\":false,\"stage\":\"request\",\"message\":"
              + JsonOut.string(invalid.getMessage()) + "}", "evaluator", "\"java\""));
      auditHook.afterEvaluation(requestJson,
          new EvalAuditHook.Outcome(null, response, elapsedSince(started), false));
      return response;
    }
    EvalOperation op = resolved;
    List<String> classes = CodeBlocks.classes(request.source);
    boolean execute = !classes.isEmpty()
        && codeBlockPolicy.allows(new CodeBlockExecutionPolicy.Request(op, request.source,
            List.copyOf(classes)));
    EvalAuditHook.Event event = new EvalAuditHook.Event(op, requestJson, request.source,
        List.copyOf(classes), execute);
    auditHook.beforeEvaluation(event);

    Callable<EvalContextResponse> work = op == EvalOperation.FORMULA_INFO_CONTEXT
        ? () -> formulaInfo(request, execute)
        : () -> formula(request, execute, op == EvalOperation.EVAL_TRACE);
    boolean timedOut = false;
    EvalContextResponse response;
    try {
      response = run(work);
    } catch (TimeoutException late) {
      timedOut = true;
      String json = "{\"ok\":false,\"stage\":\"timeout\",\"error\":{\"kind\":\"TimeoutException\","
          + "\"message\":" + JsonOut.string("evaluation did not finish within "
          + timeout.toMillis() + " ms") + "}}";
      if (op == EvalOperation.EVAL_TRACE) {
        json = JsonOut.withField(json, "trace", "null");
      }
      response = new EvalContextResponse(EvalContextResponse.EXIT_EVALUATION, json);
    }
    String json = JsonOut.withField(response.json(), "evaluator", "\"java\"");
    if (!classes.isEmpty()) {
      json = JsonOut.withField(json, "codeBlocks",
          "{\"classes\":" + JsonOut.strings(classes) + ",\"executed\":" + execute + "}");
    }
    response = new EvalContextResponse(response.exitCode(), json);
    auditHook.afterEvaluation(requestJson,
        new EvalAuditHook.Outcome(event, response, elapsedSince(started), timedOut));
    return response;
  }

  @Override
  public void close() {
    ExecutorService executor;
    synchronized (this) {
      executor = ownExecutor;
      ownExecutor = null;
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  // ---------------------------------------------------------------- evaluation

  private static EvalOperation operationOf(JsonNode json) {
    JsonNode node = json.isObject() ? json.get("operation") : null;
    if (node == null || node.isNull()) {
      return EvalOperation.EVAL_CONTEXT;
    }
    EvalOperation operation = node.isTextual() ? EvalOperation.ofWireName(node.textValue()) : null;
    if (operation == null) {
      throw new IllegalArgumentException("unknown operation " + node
          + " (expected \"evalContext\", \"evalTrace\" or \"runContext\")");
    }
    return operation;
  }

  private EvalContextResponse formula(ContextRequest request, boolean execute, boolean trace)
      throws Exception {
    Map<String, CodeBlocks.Block> blocks = CodeBlocks.blocks(request.source);
    CalculationContext context = request.newContext();
    try (Compiled compiled = compileBlocks(blocks, execute, context)) {
      Calculator calculator;
      try {
        compiled.check();
        calculator = creator.create(new Source(request.source), nextClassName(),
            new SpecifiedExpressionTypes(request.resultType, request.numberType),
            compiled.loader);
      } catch (Throwable failure) {
        rethrowFatal(failure);
        String json = errorJson("create", failure);
        if (trace) {
          json = JsonOut.withField(json, "trace", "null");
        }
        return new EvalContextResponse(failure instanceof ParseException
            ? EvalContextResponse.EXIT_PARSE : EvalContextResponse.EXIT_MAPPING, json);
      }
      context.setObject(ExternalInvocationHandler.CONTEXT_KEY,
          request.externals.forFormula(new HashSet<>(blocks.keySet()), compiled.realClasses));
      String json;
      int exit;
      try {
        Object value = calculator.apply(context);
        json = "{\"ok\":true,\"value\":" + JsonOut.value(value) + ",\"text\":"
            + JsonOut.string(String.valueOf(value)) + "}";
        exit = EvalContextResponse.EXIT_SUCCESS;
      } catch (Throwable failure) {
        rethrowFatal(failure);
        json = errorJson("apply", failure);
        exit = EvalContextResponse.EXIT_EVALUATION;
      }
      if (trace) {
        json = JsonOut.withField(json, "traceUnavailable", JsonOut.string(TRACE_UNAVAILABLE));
        json = JsonOut.withField(json, "trace", "null");
      }
      return new EvalContextResponse(exit, json);
    }
  }

  private EvalContextResponse formulaInfo(ContextRequest request, boolean execute)
      throws Exception {
    Try<FormulaInfoList> parsed = FormulaInfoList.parse(request.source, formulaInfoFields.get(),
        classLoader);
    if (parsed.throwable.isPresent()) {
      return loadFailure(parsed.throwable.get());
    }
    boolean failed = false;
    List<String> items = new ArrayList<>();
    for (FormulaInfo info : parsed.get().get()) {
      String formula = info.formulaText == null ? "" : info.formulaText;
      Map<String, CodeBlocks.Block> blocks = CodeBlocks.blocks(formula);
      CalculationContext context = request.newContext();
      String result;
      try (Compiled compiled = compileBlocks(blocks, execute, context)) {
        try {
          compiled.check();
          Calculator calculator = creator.create(new Source(formula), nextClassName(),
              new SpecifiedExpressionTypes(info.resultType, info.numberType), compiled.loader);
          context.setObject(ExternalInvocationHandler.CONTEXT_KEY,
              request.externals.forFormula(new HashSet<>(blocks.keySet()), compiled.realClasses));
          result = "\"value\":" + JsonOut.value(calculator.apply(context));
        } catch (Throwable failure) {
          rethrowFatal(failure);
          failed = true;
          result = "\"error\":" + errorObject(failure);
        }
      }
      items.add("{\"info\":" + infoJson(info) + "," + result + "}");
    }
    return new EvalContextResponse(
        failed ? EvalContextResponse.EXIT_EVALUATION : EvalContextResponse.EXIT_SUCCESS,
        "{\"ok\":" + !failed + ",\"formulas\":[" + String.join(",", items) + "]}");
  }

  private static EvalContextResponse loadFailure(Throwable failure) {
    boolean formula = failure instanceof ParseException;
    String message = String.valueOf(failure.getMessage());
    // FormulaInfoList.parse: the document is not (fully) a FormulaInfo document
    boolean syntax = failure instanceof FormulaInfoParseException
        && (message.startsWith("Invalid FormulaInfo document")
            || message.startsWith("FormulaInfo document was only partially parsed"));
    String error = "{\"kind\":" + JsonOut.string(formula ? "formula" : syntax ? "syntax" : "load")
        + ",\"javaException\":" + JsonOut.string(failure.getClass().getSimpleName())
        + ",\"message\":" + JsonOut.string(message)
        + (formula ? ",\"error\":" + errorObject(failure) : "") + "}";
    return new EvalContextResponse(
        syntax ? EvalContextResponse.EXIT_PARSE : EvalContextResponse.EXIT_LOAD,
        "{\"ok\":false,\"stage\":\"load\",\"error\":" + error + "}");
  }

  /** The FormulaInfo fields as the Rust {@code FormulaInfo::canonical_json} names them. */
  private static String infoJson(FormulaInfo info) {
    StringBuilder out = new StringBuilder("{");
    out.append("\"name\":").append(JsonOut.string(info.getName()));
    out.append(",\"calculatorName\":").append(JsonOut.string(info.calculatorName));
    out.append(",\"description\":").append(JsonOut.string(info.description));
    Collection<String> tags = info.tags == null ? List.of() : info.tags;
    out.append(",\"tags\":").append(JsonOut.strings(new ArrayList<>(tags)));
    out.append(",\"periodStartInclusive\":").append(JsonOut.string(info.periodStartInclusive));
    out.append(",\"periodEndExclusive\":").append(JsonOut.string(info.periodEndExclusive));
    out.append(",\"multiTenancyId\":").append(JsonOut.string(
        info.multiTenancyId == null ? null : info.multiTenancyId.orElse(null)));
    out.append(",\"dependsOn\":").append(JsonOut.string(info.dependsOn));
    out.append(",\"resultType\":").append(JsonOut.string(
        info.resultType == null ? Float.class.getName() : info.resultType.javaTypeAsString()));
    out.append(",\"numberType\":").append(JsonOut.string(
        info.numberType == null ? null : info.numberType.javaTypeAsString()));
    out.append(",\"executionBackend\":").append(JsonOut.string(info.executionBackend));
    out.append(",\"formulaText\":").append(JsonOut.string(info.formulaText));
    out.append(",\"hash\":").append(JsonOut.string(info.hash));
    out.append(",\"className\":").append(JsonOut.string(info.className));
    out.append(",\"classNameWithHash\":").append(JsonOut.string(info.classNameWithHash));
    out.append(",\"extraValueByKey\":{");
    if (info.extraValueByKey != null) {
      boolean first = true;
      for (Map.Entry<String, String> entry : info.extraValueByKey.entrySet()) {
        if (!first) {
          out.append(',');
        }
        first = false;
        out.append(JsonOut.string(entry.getKey())).append(':')
            .append(JsonOut.string(entry.getValue()));
      }
    }
    return out.append("}}").toString();
  }

  /** {@code {"kind":<Java exception>,"message":...}}. */
  private static String errorObject(Throwable failure) {
    return "{\"kind\":" + JsonOut.string(failure.getClass().getSimpleName()) + ",\"message\":"
        + JsonOut.string(String.valueOf(failure.getMessage())) + "}";
  }

  private static String errorJson(String stage, Throwable failure) {
    return "{\"ok\":false,\"stage\":" + JsonOut.string(stage) + ",\"error\":"
        + errorObject(failure) + "}";
  }

  /** Errors other than {@link StackOverflowError} (which the Rust contract reports) propagate. */
  private static void rethrowFatal(Throwable failure) {
    if (failure instanceof VirtualMachineError && !(failure instanceof StackOverflowError)) {
      throw (VirtualMachineError) failure;
    }
  }

  private static String nextClassName() {
    return "EvalContextService_" + CALCULATOR_SEQUENCE.incrementAndGet();
  }

  // ---------------------------------------------------------------- code blocks

  /** The class loader of one evaluation and the code-block classes compiled into it. */
  private static final class Compiled implements AutoCloseable {
    final ClassLoader loader;
    final Set<String> realClasses;
    final CompileContext compileContext;
    final Throwable failure;

    Compiled(ClassLoader loader, Set<String> realClasses, CompileContext compileContext,
        Throwable failure) {
      this.loader = loader;
      this.realClasses = realClasses;
      this.compileContext = compileContext;
      this.failure = failure;
    }

    /** Throws the compile failure, reported as a {@code create} failure. */
    void check() throws Throwable {
      if (failure != null) {
        throw failure;
      }
    }

    @Override
    public void close() throws Exception {
      if (compileContext != null) {
        compileContext.close();
      }
    }
  }

  /**
   * Compiles the {@code java} blocks in memory when {@code execute}, registering an instance of
   * each class in the context under its class name (where the evaluator looks it up).
   */
  private Compiled compileBlocks(Map<String, CodeBlocks.Block> blocks, boolean execute,
      CalculationContext context) {
    if (!execute || blocks.isEmpty()) {
      return new Compiled(classLoader, Set.of(), null, null);
    }
    CompileContext compileContext = null;
    Set<String> real = new HashSet<>();
    try {
      compileContext = new CompileContext(classLoader, new JavaFileManagerContext());
      for (CodeBlocks.Block block : blocks.values()) {
        if (!block.scheme.equalsIgnoreCase("java")) {
          continue;
        }
        Try<ClassAndByteCode> compiled =
            compileContext.compile(new ClassName(block.className), block.body);
        compiled.throwIfMatch();
        Class<?> clazz = compiled.get().clazz;
        context.setObject(clazz.getName(), clazz.getDeclaredConstructor().newInstance());
        real.add(clazz.getName());
      }
      return new Compiled(compileContext.memoryClassLoader, real, compileContext, null);
    } catch (Throwable failure) {
      rethrowFatal(failure);
      return new Compiled(classLoader, Set.of(), compileContext, failure);
    }
  }

  // ---------------------------------------------------------------- timeout

  private EvalContextResponse run(Callable<EvalContextResponse> work) throws TimeoutException {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      try {
        return work.call();
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Exception e) {
        return internal(e);
      }
    }
    Future<EvalContextResponse> future = executor().submit(work);
    try {
      return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException late) {
      // An evaluation that ignores interruption keeps its worker thread until it ends; hosts
      // bound the number of concurrent requests (see the README).
      future.cancel(true);
      throw late;
    } catch (InterruptedException interrupted) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      return internal(interrupted);
    } catch (ExecutionException failed) {
      return internal(failed.getCause() == null ? failed : failed.getCause());
    }
  }

  private static EvalContextResponse internal(Throwable failure) {
    return new EvalContextResponse(EvalContextResponse.EXIT_INTERNAL,
        "{\"ok\":false,\"stage\":\"internal\",\"message\":"
            + JsonOut.string(failure.getClass().getSimpleName() + ": " + failure.getMessage())
            + ",\"error\":" + errorObject(failure) + "}");
  }

  private synchronized ExecutorService executor() {
    if (hostExecutor != null) {
      return hostExecutor;
    }
    if (ownExecutor == null) {
      AtomicLong threads = new AtomicLong();
      ownExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "tinyexpression-eval-" + threads.incrementAndGet());
        thread.setDaemon(true);
        return thread;
      });
    }
    return ownExecutor;
  }

  private static Duration elapsedSince(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos);
  }

  /**
   * The FormulaInfo loader configuration of {@code runContext} unless the builder sets one: the
   * Rust {@code LoaderOptions::java_tests()} ({@code siteId} as the multi-tenancy attribute,
   * {@code checkKind} else {@code calculatorName} as the name), with {@code P4_AST_EVALUATOR}
   * as the default backend so that loading does not compile Java.
   */
  public static FormulaInfoAdditionalFields defaultFormulaInfoFields() {
    return new FormulaInfoAdditionalFields("siteId", info -> {
      String checkKind = info.extraValueByKey == null ? null : info.extraValueByKey.get("checkKind");
      return checkKind != null ? checkKind : info.calculatorName;
    }).setExecutionBackend(ExecutionBackend.P4_AST_EVALUATOR);
  }

  /** Configuration of an {@link EvalContextService}. */
  public static final class Builder {
    private CodeBlockExecutionPolicy codeBlockPolicy = CodeBlockExecutionPolicy.DENY;
    private Duration timeout = DEFAULT_TIMEOUT;
    private EvalAuditHook auditHook = EvalAuditHook.NONE;
    private ClassLoader classLoader;
    private Supplier<FormulaInfoAdditionalFields> formulaInfoFields =
        EvalContextService::defaultFormulaInfoFields;
    private ExecutorService executor;

    private Builder() {}

    /** Whether Java code blocks run (default {@link CodeBlockExecutionPolicy#DENY}). */
    public Builder codeBlockPolicy(CodeBlockExecutionPolicy policy) {
      this.codeBlockPolicy = Objects.requireNonNull(policy, "policy");
      return this;
    }

    /**
     * The time one request may take, compilation of code blocks included (default
     * {@link #DEFAULT_TIMEOUT}). Zero or negative: no timeout, the request runs on the calling
     * thread.
     */
    public Builder timeout(Duration timeout) {
      this.timeout = Objects.requireNonNull(timeout, "timeout");
      return this;
    }

    public Builder auditHook(EvalAuditHook auditHook) {
      this.auditHook = Objects.requireNonNull(auditHook, "auditHook");
      return this;
    }

    /** The parent class loader of the evaluator and of compiled code blocks. */
    public Builder classLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    /** The FormulaInfo loader configuration of {@code runContext} (a fresh one per request). */
    public Builder formulaInfoFields(Supplier<FormulaInfoAdditionalFields> formulaInfoFields) {
      this.formulaInfoFields = Objects.requireNonNull(formulaInfoFields, "formulaInfoFields");
      return this;
    }

    /**
     * The executor timed requests run on (default: daemon threads the service owns and
     * {@link EvalContextService#close()} stops). The service never shuts a host executor down.
     */
    public Builder executor(ExecutorService executor) {
      this.executor = executor;
      return this;
    }

    public EvalContextService build() {
      return new EvalContextService(this);
    }
  }
}
