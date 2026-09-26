package org.unlaxer.tinyexpression.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeBlockPolicy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** {@link EvalContextService} (issue #221). */
public class EvalContextServiceTest {

  static final ObjectMapper MAPPER = new ObjectMapper();

  static final String CHECK_DIGITS = String.join("\n",
      "```java:CheckDigits",
      "import org.unlaxer.tinyexpression.CalculationContext;",
      "",
      "public class CheckDigits{",
      "\tpublic boolean check(CalculationContext calculationContext,String target){",
      "\t\tfor (int i = 0; i < target.length(); i++) {",
      "\t\t\tif (!Character.isDigit(target.charAt(i))) return false;",
      "\t\t}",
      "\t\treturn !target.isEmpty();",
      "\t}",
      "}",
      "```",
      "import CheckDigits#check as checkDigits;",
      "if(external returning as boolean checkDigits($input)){",
      "  1",
      "}else{",
      "  0",
      "}");

  static final String SLEEPER = String.join("\n",
      "```java:Sleeper",
      "import org.unlaxer.tinyexpression.CalculationContext;",
      "",
      "public class Sleeper{",
      "\tpublic float nap(CalculationContext calculationContext){",
      "\t\ttry { Thread.sleep(30000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }",
      "\t\treturn 1f;",
      "\t}",
      "}",
      "```",
      "import Sleeper#nap as nap;",
      "external returning as number nap()");

  private final List<EvalContextService> services = new ArrayList<>();

  @After
  public void tearDown() {
    services.forEach(EvalContextService::close);
    JavaCodeBlockPolicy.reset();
  }

  private EvalContextService service(EvalContextService.Builder builder) {
    EvalContextService service = builder.build();
    services.add(service);
    return service;
  }

  static ObjectNode request(String formula) {
    ObjectNode request = MAPPER.createObjectNode();
    request.put("formula", formula);
    request.putArray("variables");
    request.putArray("externals");
    return request;
  }

  static void variable(ObjectNode request, String name, String type, String value) {
    ObjectNode variable = ((ArrayNode) request.get("variables")).addObject();
    variable.put("name", name);
    variable.put("type", type);
    variable.put("value", value);
  }

  static void stub(ObjectNode request, String className, String method, String type,
      String value) {
    ObjectNode stub = ((ArrayNode) request.get("externals")).addObject();
    stub.put("class", className);
    stub.put("method", method);
    stub.put("registered", true);
    ObjectNode result = stub.putObject("result");
    result.put("type", type);
    result.put("value", value);
  }

  static JsonNode json(String text) throws Exception {
    return MAPPER.readTree(text);
  }

  @Test
  public void evaluatesWithTheRequestContext() throws Exception {
    EvalContextService service = service(EvalContextService.builder());
    ObjectNode request = request("if($member){$price * 2}else{$price}");
    variable(request, "member", "boolean", "true");
    variable(request, "$price", "float", "1.5");
    EvalContextResponse response = service.execute(EvalOperation.EVAL_CONTEXT, request.toString());
    JsonNode json = json(response.json());
    assertEquals(0, response.exitCode());
    assertTrue(json.get("ok").asBoolean());
    assertEquals("number", json.at("/value/kind").asText());
    assertEquals("3", json.at("/value/value").asText());
    assertEquals("0x40400000", json.at("/value/f32Bits").asText());
    assertEquals("3.0", json.get("text").asText());
    assertEquals("java", json.get("evaluator").asText());
    assertNull(json.get("codeBlocks"));
  }

  @Test
  public void parseFailureIsACreateFailure() throws Exception {
    EvalContextResponse response = service(EvalContextService.builder())
        .execute(EvalOperation.EVAL_CONTEXT, request("1 +").toString());
    JsonNode json = json(response.json());
    assertEquals(EvalContextResponse.EXIT_PARSE, response.exitCode());
    assertFalse(json.get("ok").asBoolean());
    assertEquals("create", json.get("stage").asText());
    assertEquals("ParseException", json.at("/error/kind").asText());
  }

  @Test
  public void evaluationFailureIsAnApplyFailure() throws Exception {
    EvalContextService service = service(EvalContextService.builder());
    // no stub for the class: Class.forName fails as in Java / Rust
    String formula = "import sample.Fee#calculate as fee;\nexternal returning as number fee(1)";
    JsonNode missing = json(service.evalContext(request(formula).toString()));
    assertEquals("apply", missing.get("stage").asText());
    assertEquals("UnsupportedOperationException", missing.at("/error/kind").asText());
    // a stub of an unregistered instance
    ObjectNode unregistered = request(formula);
    stub(unregistered, "sample.Fee", "calculate", "float", "2");
    ((ObjectNode) unregistered.get("externals").get(0)).put("registered", false);
    JsonNode notRegistered = json(service.evalContext(unregistered.toString()));
    assertEquals("apply", notRegistered.get("stage").asText());
    assertEquals("CalculationException", notRegistered.at("/error/kind").asText());
    // a stub answers the call
    ObjectNode stubbed = request(formula);
    stub(stubbed, "sample.Fee", "calculate", "float", "12.5");
    JsonNode ok = json(service.evalContext(stubbed.toString()));
    assertTrue(ok.toString(), ok.get("ok").asBoolean());
    assertEquals("12.5", ok.get("text").asText());
  }

  @Test
  public void hostClassesAreNotReachable() throws Exception {
    // java.lang.Math exists on the host, but only stubs (and allowed code blocks) are loadable
    String formula = "import java.lang.Math#abs as abs;\nexternal returning as number abs(1)";
    JsonNode json = json(service(EvalContextService.builder().codeBlockPolicy(
        CodeBlockExecutionPolicy.ALLOW)).evalContext(request(formula).toString()));
    assertEquals("apply", json.get("stage").asText());
    assertTrue(json.at("/error/message").asText(),
        json.at("/error/message").asText().contains("External invocation failed: java.lang.Math#abs"));
  }

  @Test
  public void requestErrors() throws Exception {
    EvalContextService service = service(EvalContextService.builder());
    List<String[]> cases = List.of(
        new String[] {"{\"formula\":1}", "the request needs a string field \"formula\""},
        new String[] {"[1]", "the request needs a string field \"formula\""},
        new String[] {"{\"formula\":\"1\",\"resultType\":\"date\"}", "unknown resultType \"date\""},
        new String[] {"{\"formula\":\"1\",\"variables\":[{\"name\":\"b\",\"map\":\"string\",\"type\":\"boolean\",\"value\":true}]}",
            "variable b: a boolean value cannot go into the string map"},
        new String[] {"{\"formula\":\"1\",\"seed\":-1}", "seed must be a non-negative integer, found -1"},
        new String[] {"{\"formula\":\"1\",\"operation\":\"x\"}", null},
        new String[] {"{\"formula\":", null});
    for (String[] c : cases) {
      EvalContextResponse response = service.dispatch(c[0]);
      JsonNode json = json(response.json());
      assertEquals(c[0], EvalContextResponse.EXIT_USAGE, response.exitCode());
      assertEquals(c[0], "request", json.get("stage").asText());
      if (c[1] != null) {
        assertEquals(c[0], c[1], json.get("message").asText());
      }
    }
  }

  @Test
  public void codeBlocksAreNotRunByDefault() throws Exception {
    EvalContextService service = service(EvalContextService.builder());
    ObjectNode request = request(CHECK_DIGITS);
    variable(request, "input", "string", "abc");
    JsonNode missing = json(service.evalContext(request.toString()));
    assertEquals("apply", missing.get("stage").asText());
    assertTrue(missing.at("/error/message").asText(),
        missing.at("/error/message").asText().contains("externals で値を指定してください"));
    assertFalse(missing.at("/codeBlocks/executed").asBoolean());
    assertEquals("CheckDigits", missing.at("/codeBlocks/classes/0").asText());

    stub(request, "CheckDigits", "check", "boolean", "true");
    JsonNode stubbed = json(service.evalContext(request.toString()));
    assertTrue(stubbed.toString(), stubbed.get("ok").asBoolean());
    assertEquals("1.0", stubbed.get("text").asText());
  }

  @Test
  public void allowedCodeBlocksRunAndWinOverStubs() throws Exception {
    List<CodeBlockExecutionPolicy.Request> asked = new ArrayList<>();
    EvalContextService service = service(EvalContextService.builder().codeBlockPolicy(request -> {
      asked.add(request);
      return true;
    }));
    ObjectNode request = request(CHECK_DIGITS);
    variable(request, "input", "string", "abc");
    stub(request, "CheckDigits", "check", "boolean", "true");
    JsonNode json = json(service.evalContext(request.toString()));
    assertTrue(json.toString(), json.get("ok").asBoolean());
    assertEquals("0.0", json.get("text").asText()); // the real check, not the stub's true
    assertTrue(json.at("/codeBlocks/executed").asBoolean());
    assertEquals(1, asked.size());
    assertEquals(List.of("CheckDigits"), asked.get(0).classes());

    ObjectNode digits = request(CHECK_DIGITS);
    variable(digits, "input", "string", "123");
    assertEquals("1.0", json(service.evalContext(digits.toString())).get("text").asText());
  }

  @Test
  public void followGlobalPolicy() throws Exception {
    EvalContextService service = service(EvalContextService.builder()
        .codeBlockPolicy(CodeBlockExecutionPolicy.FOLLOW_GLOBAL));
    ObjectNode request = request(CHECK_DIGITS);
    variable(request, "input", "string", "123");
    assertFalse(json(service.evalContext(request.toString())).at("/codeBlocks/executed").asBoolean());
    JavaCodeBlockPolicy.setEnabled(true);
    JsonNode json = json(service.evalContext(request.toString()));
    assertTrue(json.at("/codeBlocks/executed").asBoolean());
    assertEquals("1.0", json.get("text").asText());
  }

  @Test
  public void timeout() throws Exception {
    EvalContextService service = service(EvalContextService.builder()
        .codeBlockPolicy(CodeBlockExecutionPolicy.ALLOW)
        .timeout(Duration.ofSeconds(3)));
    long started = System.nanoTime();
    EvalContextResponse response = service.execute(EvalOperation.EVAL_TRACE,
        request(SLEEPER).toString());
    long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
    JsonNode json = json(response.json());
    assertEquals("timeout", json.get("stage").asText());
    assertEquals("TimeoutException", json.at("/error/kind").asText());
    assertTrue(json.has("trace"));
    assertTrue("elapsed " + elapsedMillis, elapsedMillis < 20_000);
  }

  @Test
  public void auditHookSeesEveryRequest() throws Exception {
    List<String> log = new ArrayList<>();
    EvalContextService service = service(EvalContextService.builder().auditHook(new EvalAuditHook() {
      @Override
      public void beforeEvaluation(Event event) {
        log.add("before " + event.operation().wireName() + " " + event.source() + " "
            + event.codeBlockClasses() + " " + event.codeBlocksExecuted());
      }

      @Override
      public void afterEvaluation(String requestJson, Outcome outcome) {
        log.add("after " + (outcome.event() == null) + " " + outcome.response().exitCode() + " "
            + outcome.timedOut());
      }
    }));
    service.evalContext(request("1 + 2").toString());
    service.dispatch("{}");
    ObjectNode code = request(CHECK_DIGITS);
    code.put("operation", "evalTrace");
    service.dispatch(code.toString());
    assertEquals(List.of(
        "before evalContext 1 + 2 [] false",
        "after false 0 false",
        "after true 2 false",
        "before evalTrace " + CHECK_DIGITS + " [CheckDigits] false",
        "after false 5 false"), log);
  }

  @Test
  public void auditHookFailurePropagates() {
    EvalContextService service = service(EvalContextService.builder().auditHook(new EvalAuditHook() {
      @Override
      public void beforeEvaluation(Event event) {
        throw new IllegalStateException("audit log unavailable");
      }
    }));
    try {
      service.evalContext(request("1").toString());
      throw new AssertionError("expected the audit failure");
    } catch (IllegalStateException expected) {
      assertEquals("audit log unavailable", expected.getMessage());
    }
  }

  @Test
  public void traceIsUnavailable() throws Exception {
    JsonNode json = json(service(EvalContextService.builder()).evalTrace(request("1+2").toString()));
    assertTrue(json.get("ok").asBoolean());
    assertTrue(json.get("trace").isNull());
    assertEquals(EvalContextService.TRACE_UNAVAILABLE, json.get("traceUnavailable").asText());
  }

  @Test
  public void formulaInfoContext() throws Exception {
    String document = String.join("\n",
        "calculatorName:first",
        "resultType:float",
        "formula:$x + 1",
        "---END_OF_PART---",
        "calculatorName:second",
        "resultType:string",
        "formula:'a' + 'b'",
        "---END_OF_PART---",
        "");
    ObjectNode request = MAPPER.createObjectNode();
    request.put("document", document);
    ArrayNode variables = request.putArray("variables");
    ObjectNode x = variables.addObject();
    x.put("name", "x");
    x.put("value", 2);
    request.put("operation", "runContext");
    EvalContextResponse response = service(EvalContextService.builder()).dispatch(request.toString());
    JsonNode json = json(response.json());
    assertEquals(response.json(), 0, response.exitCode());
    assertEquals(2, json.get("formulas").size());
    assertEquals("first", json.at("/formulas/0/info/calculatorName").asText());
    assertEquals("3", json.at("/formulas/0/value/value").asText());
    assertEquals("ab", json.at("/formulas/1/value/value").asText());

    ObjectNode broken = MAPPER.createObjectNode();
    broken.put("document", "calculatorName:x\nformula:1 +\n---END_OF_PART---\n");
    JsonNode load = json(service(EvalContextService.builder()).formulaInfoContext(broken.toString()));
    assertEquals("load", load.get("stage").asText());
  }

  @Test
  public void codeBlockScanFollowsTheRustRules() {
    assertEquals(List.of("CheckDigits", "sample.v1.CheckAlphabets"), CodeBlocks.classes(
        "```java:CheckDigits\npublic class CheckDigits{}\n```\r\n"
            + "```java:sample.v1.CheckAlphabets\r\nclass X{ String s = \"```java:Nope\"; }\n```\n"
            + "import CheckDigits#check as c;\n1"));
    assertEquals(List.of(), CodeBlocks.classes(" ```java:A\n```"));
    assertEquals(List.of(), CodeBlocks.classes("```java:A trailing\n```"));
    assertEquals(List.of(), CodeBlocks.classes("```java:a..b\n```"));
    assertEquals(List.of(), CodeBlocks.classes("```java\n```"));
    assertEquals(List.of("A"), CodeBlocks.classes("```java:A\nclass A{}"));
    assertEquals(List.of("A"), CodeBlocks.classes("```java:A\n```java:B\n```\n```java:A\n```"));
    assertEquals("class A{}\n", CodeBlocks.blocks("```java:A\nclass A{}\n```\n1").get("A").body);
  }

  @Test
  public void rustFloatDisplay() {
    assertEquals("1", JsonOut.rustFloatDisplay(1f));
    assertEquals("0.1", JsonOut.rustFloatDisplay(0.1f));
    assertEquals("100000000000000000000", JsonOut.rustFloatDisplay(1e20f));
    assertEquals("-0", JsonOut.rustFloatDisplay(-0f));
    assertEquals("inf", JsonOut.rustFloatDisplay(Float.POSITIVE_INFINITY));
    assertEquals("NaN", JsonOut.rustFloatDisplay(Float.NaN));
    assertEquals("\"a\\\"\\n\\u0001\"", JsonOut.string("a\"\n\u0001"));
  }
}
