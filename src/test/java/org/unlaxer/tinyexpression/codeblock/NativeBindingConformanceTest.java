package org.unlaxer.tinyexpression.codeblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.unlaxer.tinyexpression.service.CodeBlockExecutionPolicy;
import org.unlaxer.tinyexpression.service.EvalContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Real Java bodies and real compiled Rust bodies share this oracle, not external stubs. */
public class NativeBindingConformanceTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private static String resource(String name) throws Exception {
    try (var input = NativeBindingConformanceTest.class.getResourceAsStream("/native-bindings/" + name)) {
      if (input == null) throw new AssertionError("missing fixture " + name);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  public void realJavaCodeMatchesSharedNativeOracle() throws Exception {
    String blocks = "```java:NativeDemo\n" + resource("NativeDemo.java") + "```\n"
        + "```java:NativeSecond\n" + resource("NativeSecond.java") + "```\n";
    var failures = new java.util.ArrayList<String>();
    try (var service = EvalContextService.builder().codeBlockPolicy(CodeBlockExecutionPolicy.ALLOW).build()) {
      for (JsonNode row : JSON.readTree(resource("cases.json"))) {
        ObjectNode request = row.has("request") ? (ObjectNode) row.get("request").deepCopy() : JSON.createObjectNode();
        request.put("formula", blocks + row.get("formula").asText());
        JsonNode actual = JSON.readTree(service.evalContext(request.toString()));
        String label = row.get("name").asText() + ": " + actual;
        try {
          if (row.has("error")) {
            assertFalse(label, actual.path("ok").asBoolean());
            assertEquals(label, "apply", actual.path("stage").asText());
            assertEquals(label, row.get("error").asText(), actual.at("/error/kind").asText());
          } else {
            assertTrue(label, actual.path("ok").asBoolean());
            assertEquals(label, row.get("text").asText(), actual.path("text").asText());
            assertTrue(label, actual.at("/codeBlocks/executed").asBoolean());
          }
        } catch (AssertionError error) {
          failures.add(error.getMessage());
        }
      }
    }
    assertTrue(String.join("\n", failures), failures.isEmpty());
  }
}
