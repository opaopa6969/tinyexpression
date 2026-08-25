package org.unlaxer.tinyexpression.dap.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.junit.Test;

public class TinyExpressionP4DebugAdapterExtTest {

  @Test
  public void launchStopsOnAstAndEvaluatesWithTypedVariables() throws Exception {
    Path program = Files.createTempFile("tinyexpression-dap-", ".tinyexp");
    Files.writeString(program,
        "var $score as number set if not exists 0 description='score';\n$score + 8");
    try {
      List<String> clientEvents = new ArrayList<>();
      IDebugProtocolClient client = recordingClient(clientEvents);
      TinyExpressionP4DebugAdapterExt adapter = new TinyExpressionP4DebugAdapterExt();
      adapter.connect(client);

      adapter.initialize(new InitializeRequestArguments()).join();
      Map<String, Object> launch = new LinkedHashMap<>();
      launch.put("program", program.toString());
      launch.put("runtimeMode", "p4-ast");
      launch.put("steppingMode", "ast");
      launch.put("stopOnEntry", true);
      launch.put("variables", Map.of("score", 42));
      adapter.launch(launch).join();
      adapter.configurationDone(new ConfigurationDoneArguments()).join();

      assertTrue(clientEvents.contains("initialized"));
      assertTrue(clientEvents.contains("stopped"));

      StackTraceArguments stackArgs = new StackTraceArguments();
      stackArgs.setThreadId(1);
      var stack = adapter.stackTrace(stackArgs).join();
      assertEquals(1, stack.getStackFrames().length);
      assertEquals(program.toString(), stack.getStackFrames()[0].getSource().getPath());
      assertTrue(stack.getStackFrames()[0].getName().contains("Expr"));

      VariablesArguments variableArgs = new VariablesArguments();
      variableArgs.setVariablesReference(1);
      Variable[] variables = adapter.variables(variableArgs).join().getVariables();
      assertVariable(variables, "runtimeMode", "p4-ast");
      assertVariable(variables, "steppingMode", "ast");
      assertVariable(variables, "selectedExecutionBackend", "P4_AST_EVALUATOR");
      assertVariable(variables, "evaluationResultNormalized", "50");
      assertVariable(variables, "parity.equalAll", "true");
      assertVariable(variables, "$score", "42");
      assertVariable(variables, "_tinyP4ParserUsed", "true");

      EvaluateArguments evaluateArgs = new EvaluateArguments();
      evaluateArgs.setExpression("$score + 10");
      assertEquals("52", adapter.evaluate(evaluateArgs).join().getResult());
    } finally {
      Files.deleteIfExists(program);
    }
  }

  @Test
  public void legacyFormulaSourceLaunchKeyRemainsCompatible() throws Exception {
    Path program = Files.createTempFile("tinyexpression-dap-alias-", ".tinyexp");
    Files.writeString(program, "1 + 1");
    try {
      List<String> clientEvents = new ArrayList<>();
      TinyExpressionP4DebugAdapterExt adapter = new TinyExpressionP4DebugAdapterExt();
      adapter.connect(recordingClient(clientEvents));
      adapter.launch(Map.of(
          "formulaSource", program.toString(),
          "runtimeMode", "p4-ast",
          "steppingMode", "ast",
          "stopOnEntry", true)).join();
      adapter.configurationDone(new ConfigurationDoneArguments()).join();
      assertTrue(clientEvents.contains("stopped"));
    } finally {
      Files.deleteIfExists(program);
    }
  }

  private static void assertVariable(Variable[] variables, String name, String value) {
    Variable variable = java.util.Arrays.stream(variables)
        .filter(candidate -> name.equals(candidate.getName()))
        .findFirst()
        .orElse(null);
    assertNotNull("Missing DAP variable: " + name, variable);
    assertEquals(value, variable.getValue());
  }

  private static IDebugProtocolClient recordingClient(List<String> events) {
    return (IDebugProtocolClient) Proxy.newProxyInstance(
        IDebugProtocolClient.class.getClassLoader(),
        new Class<?>[] {IDebugProtocolClient.class},
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
              case "toString" -> "RecordingDebugProtocolClient";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> null;
            };
          }
          events.add(method.getName());
          return null;
        });
  }
}
