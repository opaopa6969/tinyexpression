package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.unlaxer.compiler.ClassAndByteCode;
import org.unlaxer.compiler.ClassName;
import org.unlaxer.compiler.CompileContext;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.TokenBaseCalculator;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/** Compatibility coverage for nullable and {@code Optional} external-invocation qualifiers. */
public class P4OptionalExternalQualifierTest {

  private static final String FIXTURE_CLASS = P4OptionalExternalQualifierTest.class.getName();

  private record InvocationCase(
      String typeKeyword, String methodName, ExpressionType resultType, Object expected) {}

  private static final List<InvocationCase> CASES = List.of(
      new InvocationCase("boolean", "externalBoolean", ExpressionTypes._boolean, true),
      new InvocationCase("number", "externalNumber", ExpressionTypes._float, 12.5f),
      new InvocationCase("string", "externalString", ExpressionTypes.string, "external-string"),
      new InvocationCase("object", "externalObject", ExpressionTypes.object,
          List.of("external-object")));

  private enum Backend {
    AST_EVALUATOR,
    DSL_JAVA_CODE
  }

  @Test
  public void qualifiedAndImportedUnqualifiedInvocationsWorkForEveryReturnType() {
    int sequence = 0;
    for (Backend backend : Backend.values()) {
      for (InvocationCase invocation : CASES) {
        String qualified = "external returning as " + invocation.typeKeyword() + " : "
            + FIXTURE_CLASS + "#" + invocation.methodName() + "(1)";
        assertInvocation(backend, invocation, qualified, "qualified", sequence++);

        String imported = "import " + FIXTURE_CLASS + " as " + invocation.methodName()
            + ";\nexternal returning as " + invocation.typeKeyword() + " : "
            + invocation.methodName() + "(1)";
        assertInvocation(backend, invocation, imported, "imported", sequence++);
      }
    }
  }

  @Test
  public void unimportedUnqualifiedTargetFailsExplicitlyInBothBackends() {
    int sequence = 0;
    for (Backend backend : Backend.values()) {
      String formula = "external returning as string : missingTarget(1)";
      try {
        execute(backend, formula, ExpressionTypes.string,
            "MissingExternalTarget" + sequence++, contextWithFixture());
        fail("unimported external target must fail");
      } catch (RuntimeException expected) {
        assertTrue("failure must identify the unresolved external target: " + expected,
            containsMessage(expected, "missingTarget")
                && (containsMessage(expected, "not imported")
                    || containsMessage(expected, "incomplete")));
      }
    }
  }

  @Test
  public void generatedMapperQualifierShapeMatchesRequestedGenerator() throws Exception {
    List<Class<?>> invocationTypes = List.of(
        TinyExpressionP4AST.ExternalBooleanInvocationExpr.class,
        TinyExpressionP4AST.ExternalNumberInvocationExpr.class,
        TinyExpressionP4AST.ExternalStringInvocationExpr.class,
        TinyExpressionP4AST.ExternalObjectInvocationExpr.class);

    String configured = System.getProperty("tinyexpression.expected.mapper.optionalQualifier");
    for (Class<?> invocationType : invocationTypes) {
      Method className = invocationType.getMethod("className");
      boolean optionalQualifier = className.getReturnType() == Optional.class;
      assertTrue("unsupported className() shape on " + invocationType.getSimpleName(),
          optionalQualifier
              || className.getReturnType() == TinyExpressionP4AST.QualifiedNameExpr.class);
      if (configured != null) {
        assertEquals("className() shape on " + invocationType.getSimpleName(),
            Boolean.parseBoolean(configured), optionalQualifier);
      }
    }
  }

  private void assertInvocation(Backend backend, InvocationCase invocation,
      String formula, String form, int sequence) {
    Object actual = execute(backend, formula, invocation.resultType(),
        "ExternalQualifier_" + form + "_" + sequence, contextWithFixture());
    assertEquals(form + " " + invocation.typeKeyword(), invocation.expected(), actual);
  }

  private static Object execute(Backend backend, String formula, ExpressionType resultType,
      String className, CalculationContext context) {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(resultType, ExpressionTypes._float);
    if (backend == Backend.AST_EVALUATOR) {
      Calculator calculator = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
          .create(new Source(formula), className, types, loader);
      return calculator.apply(context);
    }

    DslGeneratedAstJavaEmitter.EmittedJava emitted = DslGeneratedAstJavaEmitter
        .tryEmit(className, new Source(formula), types, loader)
        .orElseThrow(() -> new UnsupportedOperationException(
            "Generated DSL Java emitter cannot emit formula: " + formula));
    assertEquals("p4-typed-emitter", emitted.mode());
    try (CompileContext compileContext = new CompileContext(loader, new JavaFileManagerContext())) {
      ClassAndByteCode compiled = compileContext
          .compile(new ClassName(className), emitted.javaCode()).get();
      TokenBaseCalculator calculator =
          (TokenBaseCalculator) compiled.clazz.getDeclaredConstructor().newInstance();
      return calculator.evaluate(context, null);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private CalculationContext contextWithFixture() {
    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set(this);
    return context;
  }

  private static boolean containsMessage(Throwable failure, String fragment) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  public boolean externalBoolean(CalculationContext context, float ignored) {
    return true;
  }

  public float externalNumber(CalculationContext context, float ignored) {
    return 12.5f;
  }

  public String externalString(CalculationContext context, float ignored) {
    return "external-string";
  }

  public Object externalObject(CalculationContext context, float ignored) {
    return List.of("external-object");
  }
}
