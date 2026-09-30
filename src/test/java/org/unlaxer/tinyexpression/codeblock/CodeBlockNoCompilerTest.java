package org.unlaxer.tinyexpression.codeblock;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.Test;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class CodeBlockNoCompilerTest {
  @Test
  public void rustRejectionDoesNotInitializeJavac() throws Exception {
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Process child = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "--limit-modules", "java.se", "-Xmx512m", "-cp", classpath, Probe.class.getName())
        .redirectErrorStream(true).start();
    if (!child.waitFor(30, TimeUnit.SECONDS)) {
      child.destroyForcibly();
      fail("compiler-free rejection probe timed out");
    }
    String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(output, 0, child.exitValue());
    assertTrue(output, output.contains("compiler-free rejection: all backends passed"));
  }

  /** Separate JVM: the JDK compiler implementation is deliberately not observable. */
  public static class Probe {
    public static void main(String[] args) throws Exception {
      assertNull("probe must not have jdk.compiler", ToolProvider.getSystemJavaCompiler());
      String rust = "```rust:Demo\nnot valid Rust\n```\n";
      for (String source : new String[] {rust + "1", rust + "```java:Other\nnot valid Java\n```\n1"}) {
        var blocks = CodeBlockSource.parse(source);
        assertEquals("CB004", CodeBlockSource.preflight(blocks, CodeBlockSource.Target.RUST, false).get(0).code());
        for (var backend : ExecutionBackend.values()) {
          var creator = CalculatorCreatorRegistry.forBackend(backend);
          Throwable error = assertThrows(backend.toString(), Exception.class, () -> creator.create(
              new Source(source), "NoCompiler", new SpecifiedExpressionTypes(
                  ExpressionTypes._float, ExpressionTypes._float), Probe.class.getClassLoader()));
          assertTrue(backend + ": " + error, error.getMessage().contains("CB005"));
          while (error.getCause() != null) error = error.getCause();
          assertTrue(backend.toString(), error instanceof UnsupportedOperationException);
        }
        try (var service = org.unlaxer.tinyexpression.service.EvalContextService.builder()
            .codeBlockPolicy(org.unlaxer.tinyexpression.service.CodeBlockExecutionPolicy.ALLOW).build()) {
          var request = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("formula", source);
          String response = service.evalContext(request.toString());
          assertTrue(response, response.contains("CB005"));
        }
      }
      System.out.println("compiler-free rejection: all backends passed");
    }
  }
}
