package org.unlaxer.tinyexpression.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Java/Rust contract equality of {@link EvalContextService} (issue #221).
 *
 * <p>Evaluates every request of {@code eval-context-contract/requests.tsv} with the service
 * (code blocks denied, its default) and compares the response with the Rust response recorded
 * in {@code rust-responses.tsv} (kept current by the Rust test
 * {@code rust/tinyexpression-rs/tests/eval_context_contract.rs}). Compared: {@code ok},
 * {@code stage}, the exit code; for a value its kind, IEEE bits (numbers) or value, and
 * {@code text}; for a failure the Java exception kind ({@code error.kind}, FormulaInfo load
 * failures {@code error.javaException}); request-error messages; for FormulaInfo every
 * formula's fields and outcome. The differences the service documents (README "サーバ評価")
 * are listed in {@link #DOCUMENTED}: error messages of the evaluator, parser diagnostics,
 * {@code trace} contents, and the FormulaInfo fields the Java loader does not have.
 */
public class EvalContextContractTest {

  static final ObjectMapper MAPPER = new ObjectMapper();

  /** Known differences by case id (asserted to still be the only differences). */
  static final Map<String, String> DOCUMENTED = Map.of(
      "request-invalid-json", "the JSON syntax error message comes from Jackson");

  static EvalContextService service;

  @BeforeClass
  public static void start() {
    service = EvalContextService.withDefaults();
  }

  @AfterClass
  public static void stop() {
    service.close();
  }

  static List<String[]> table(String name, int columns) throws IOException {
    try (InputStream in = EvalContextContractTest.class
        .getResourceAsStream("/eval-context-contract/" + name)) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      List<String[]> rows = new ArrayList<>();
      for (String line : text.split("\n")) { // 規約例外: TSV 行分割（言語解釈ではない）
        if (!line.isEmpty()) {
          rows.add(line.split("\t", columns)); // 規約例外: TSV 列分割
        }
      }
      return rows;
    }
  }

  @Test
  public void javaAnswersLikeRust() throws Exception {
    List<String[]> requests = table("requests.tsv", 3);
    Map<String, String[]> rust = new LinkedHashMap<>();
    for (String[] row : table("rust-responses.tsv", 3)) {
      rust.put(row[0], row);
    }
    assertEquals("rust-responses.tsv is stale (TE_CONTRACT_UPDATE=1 cargo test)",
        requests.size(), rust.size());
    List<String> differences = new ArrayList<>();
    for (String[] request : requests) {
      String id = request[0];
      EvalOperation operation = EvalOperation.ofWireName(request[1]);
      EvalContextResponse java = service.execute(operation, request[2]);
      String[] expected = rust.get(id);
      JsonNode expectedJson = MAPPER.readTree(expected[2]);
      JsonNode javaJson = MAPPER.readTree(java.json());
      assertEquals(id, "java", javaJson.get("evaluator").asText());
      String rustProjection = expected[1] + " " + project(expectedJson, id);
      String javaProjection = java.exitCode() + " " + project(javaJson, id);
      boolean same = rustProjection.equals(javaProjection);
      if (same == DOCUMENTED.containsKey(id)) {
        differences.add(id + (same ? " (documented difference no longer differs)" : "")
            + "\n  rust: " + rustProjection + "\n  java: " + javaProjection);
      }
    }
    assertTrue(differences.size() + " contract differences:\n" + String.join("\n", differences),
        differences.isEmpty());
  }

  /** The compared part of a response. */
  static String project(JsonNode response, String id) {
    ObjectNode out = MAPPER.createObjectNode();
    out.set("ok", response.get("ok"));
    copy(response, out, "stage");
    if ("request".equals(text(response, "stage"))) {
      out.set("message", response.get("message"));
    }
    if (response.has("value")) {
      out.set("value", value(response.get("value")));
      out.set("text", response.get("text"));
    }
    if (response.has("error")) {
      JsonNode error = response.get("error");
      out.put("error", error.has("javaException") ? error.get("javaException").asText()
          : error.get("kind").asText());
    }
    if (response.has("trace")) {
      out.put("trace", true);
    }
    if (response.has("formulas")) {
      for (JsonNode formula : response.get("formulas")) {
        ObjectNode item = out.withArray("formulas").addObject();
        JsonNode info = formula.get("info");
        for (String field : List.of("name", "calculatorName", "description", "tags",
            "periodStartInclusive", "periodEndExclusive", "multiTenancyId", "dependsOn",
            "resultType", "numberType", "formulaText", "hash", "className",
            "classNameWithHash", "extraValueByKey")) {
          item.set(field, info.get(field));
        }
        if (formula.has("value")) {
          item.set("value", value(formula.get("value")));
        } else {
          item.put("error", formula.at("/error/kind").asText());
        }
      }
    }
    return out.toString();
  }

  static ObjectNode value(JsonNode value) {
    ObjectNode out = MAPPER.createObjectNode();
    out.set("kind", value.get("kind"));
    if (value.has("f32Bits")) {
      out.set("bits", value.get("f32Bits"));
    } else if (value.has("f64Bits")) {
      out.set("bits", value.get("f64Bits"));
    } else {
      copy(value, out, "value");
      copy(value, out, "class");
    }
    return out;
  }

  static void copy(JsonNode from, ObjectNode to, String field) {
    if (from.has(field)) {
      to.set(field, from.get(field));
    }
  }

  static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null ? null : value.asText();
  }
}
