package org.unlaxer.tinyexpression.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.Assert.*;

public class McpServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private McpServer server;
    private int port;
    private HttpClient client;

    @Before
    public void setUp() throws Exception {
        port = findFreePort();
        server = new McpServer(port, false);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop();
    }

    @Test
    public void healthz_returns200() throws Exception {
        HttpResponse<String> resp = httpGet("/healthz");
        assertEquals(200, resp.statusCode());
        JsonNode body = MAPPER.readTree(resp.body());
        assertTrue(body.get("ok").asBoolean());
        assertEquals("tinyexpression-mcp", body.get("name").asText());
    }

    @Test
    public void initialize_returnsCapabilities() throws Exception {
        JsonNode result = rpc("initialize", null);
        assertEquals("2025-03-26", result.get("protocolVersion").asText());
        assertNotNull(result.get("capabilities").get("tools"));
        assertNotNull(result.get("capabilities").get("resources"));
        assertEquals("tinyexpression-mcp", result.get("serverInfo").get("name").asText());
    }

    @Test
    public void toolsList_returnsAllTools() throws Exception {
        JsonNode result = rpc("tools/list", null);
        JsonNode tools = result.get("tools");
        assertTrue(tools.isArray());
        assertEquals(5, tools.size());
        boolean hasEvaluate = false, hasValidate = false, hasBatch = false, hasParity = false, hasListBackends = false;
        for (JsonNode t : tools) {
            String name = t.get("name").asText();
            if ("evaluate".equals(name)) hasEvaluate = true;
            if ("validate".equals(name)) hasValidate = true;
            if ("execute_batch".equals(name)) hasBatch = true;
            if ("parity_check".equals(name)) hasParity = true;
            if ("list_backends".equals(name)) hasListBackends = true;
            assertNotNull("tool " + name + " should have annotations", t.get("annotations"));
        }
        assertTrue(hasEvaluate && hasValidate && hasBatch && hasParity && hasListBackends);
    }

    @Test
    public void toolsCall_evaluate_simple() throws Exception {
        JsonNode result = rpc("tools/call", MAPPER.createObjectNode()
                .put("name", "evaluate")
                .set("arguments", MAPPER.createObjectNode()
                        .put("formula", "1+2")));
        String text = result.get("content").get(0).get("text").asText();
        JsonNode eval = MAPPER.readTree(text);
        assertEquals(3.0, eval.get("result").asDouble(), 0.001);
        assertEquals("AST_EVALUATOR", eval.get("backend_used").asText());
    }

    @Test
    public void toolsCall_evaluate_withVariables() throws Exception {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("formula", "if($x>0){1}else{0}");
        ObjectNode vars = MAPPER.createObjectNode();
        vars.put("x", 5);
        args.set("variables", vars);
        JsonNode result = rpc("tools/call", MAPPER.createObjectNode()
                .put("name", "evaluate")
                .set("arguments", args));
        String text = result.get("content").get(0).get("text").asText();
        JsonNode eval = MAPPER.readTree(text);
        assertEquals(1.0, eval.get("result").asDouble(), 0.001);
    }

    @Test
    public void toolsCall_evaluate_stringResult() throws Exception {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("formula", "if($name==\"opa\"){\"hello\"}else{\"bye\"}");
        ObjectNode vars = MAPPER.createObjectNode();
        vars.put("name", "opa");
        args.set("variables", vars);
        args.put("resultType", "string");
        JsonNode result = rpc("tools/call", MAPPER.createObjectNode()
                .put("name", "evaluate")
                .set("arguments", args));
        String text = result.get("content").get(0).get("text").asText();
        JsonNode eval = MAPPER.readTree(text);
        assertEquals("hello", eval.get("result").asText());
    }

    @Test
    public void toolsCall_validate_ok() throws Exception {
        JsonNode result = rpc("tools/call", MAPPER.createObjectNode()
                .put("name", "validate")
                .set("arguments", MAPPER.createObjectNode()
                        .put("formula", "1+2")));
        String text = result.get("content").get(0).get("text").asText();
        JsonNode v = MAPPER.readTree(text);
        assertTrue(v.get("parse_ok").asBoolean());
    }

    @Test
    public void toolsCall_validate_error() throws Exception {
        JsonNode result = rpc("tools/call", MAPPER.createObjectNode()
                .put("name", "validate")
                .set("arguments", MAPPER.createObjectNode()
                        .put("formula", "1++")));
        String text = result.get("content").get(0).get("text").asText();
        JsonNode v = MAPPER.readTree(text);
        assertFalse(v.get("parse_ok").asBoolean());
        assertTrue(v.has("errors"));
    }

    @Test
    public void toolsCall_execute_batch_withDependency() throws Exception {
        ObjectNode args = MAPPER.createObjectNode();
        ObjectNode f1 = MAPPER.createObjectNode();
        f1.put("name", "base");
        f1.put("formula", "$x * 2");
        ObjectNode f2 = MAPPER.createObjectNode();
        f2.put("name", "total");
        f2.put("formula", "$base + 100");
        f2.put("dependsOn", MAPPER.createArrayNode().add("base"));
        args.set("formulas", MAPPER.createArrayNode().add(f1).add(f2));
        ObjectNode vars = MAPPER.createObjectNode();
        vars.put("x", 5);
        args.set("variables", vars);

        JsonNode result = rpc("tools/call", MAPPER.createObjectNode()
                .put("name", "execute_batch")
                .set("arguments", args));
        String text = result.get("content").get(0).get("text").asText();
        JsonNode batch = MAPPER.readTree(text);
        JsonNode results = batch.get("results");
        assertEquals(2, results.size());
        boolean foundBase = false, foundTotal = false;
        for (JsonNode r : results) {
            if ("base".equals(r.get("name").asText())) {
                assertEquals(10.0, r.get("result").asDouble(), 0.001);
                foundBase = true;
            }
            if ("total".equals(r.get("name").asText())) {
                assertEquals(110.0, r.get("result").asDouble(), 0.001);
                foundTotal = true;
            }
        }
        assertTrue(foundBase && foundTotal);
    }

    @Test
    public void toolsCall_list_backends() throws Exception {
        JsonNode result = rpc("tools/call", MAPPER.createObjectNode()
                .put("name", "list_backends")
                .set("arguments", MAPPER.createObjectNode()));
        String text = result.get("content").get(0).get("text").asText();
        JsonNode backends = MAPPER.readTree(text);
        assertTrue(backends.isArray());
        assertEquals(6, backends.size());
        boolean hasAst = false, hasJavaCode = false;
        for (JsonNode b : backends) {
            if ("AST_EVALUATOR".equals(b.get("name").asText())) {
                hasAst = true;
                assertTrue(b.get("allowed").asBoolean());
            }
            if ("JAVA_CODE".equals(b.get("name").asText())) {
                hasJavaCode = true;
                assertFalse(b.get("allowed").asBoolean());
            }
        }
        assertTrue(hasAst && hasJavaCode);
    }

    @Test
    public void toolsCall_parity_check() throws Exception {
        JsonNode result = rpc("tools/call", MAPPER.createObjectNode()
                .put("name", "parity_check")
                .set("arguments", MAPPER.createObjectNode()
                        .put("formula", "1+2")));
        String text = result.get("content").get(0).get("text").asText();
        JsonNode parity = MAPPER.readTree(text);
        JsonNode backends = parity.get("backends");
        assertTrue(backends.isArray());
        assertTrue(backends.size() >= 2);
    }

    @Test
    public void toolsCall_javaCodeBackend_rejected() throws Exception {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("formula", "1+2");
        args.put("backend", "JAVA_CODE");
        var reqBody = MAPPER.createObjectNode();
        reqBody.put("jsonrpc", "2.0");
        reqBody.put("id", 1);
        reqBody.put("method", "tools/call");
        reqBody.set("params", MAPPER.createObjectNode().put("name", "evaluate").set("arguments", args));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(reqBody)))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode respBody = MAPPER.readTree(resp.body());
        assertNotNull(respBody.get("error"));
        assertTrue(respBody.get("error").get("message").asText().contains("TINYEXPR_ALLOW_JAVA_CODE"));
    }

    @Test
    public void resourcesList_returnsAllResources() throws Exception {
        JsonNode result = rpc("resources/list", null);
        JsonNode resources = result.get("resources");
        assertTrue(resources.isArray());
        assertEquals(4, resources.size());
        boolean hasSpec = false, hasGuide = false, hasLanguage = false, hasBackends = false;
        for (JsonNode r : resources) {
            String uri = r.get("uri").asText();
            if ("tinyexpr://spec".equals(uri)) hasSpec = true;
            if ("tinyexpr://guide".equals(uri)) hasGuide = true;
            if ("tinyexpr://language".equals(uri)) hasLanguage = true;
            if ("tinyexpr://backends".equals(uri)) hasBackends = true;
        }
        assertTrue(hasSpec && hasGuide && hasLanguage && hasBackends);
    }

    @Test
    public void resourcesRead_spec_returnsJson() throws Exception {
        JsonNode result = rpc("resources/read", MAPPER.createObjectNode()
                .put("uri", "tinyexpr://spec"));
        JsonNode contents = result.get("contents");
        assertTrue(contents.isArray() && contents.size() > 0);
        String text = contents.get(0).get("text").asText();
        JsonNode spec = MAPPER.readTree(text);
        assertEquals("tinyexpr", spec.get("namespace").asText());
        assertNotNull(spec.get("capabilities"));
        assertNotNull(spec.get("compositions"));
    }

    @Test
    public void resourcesRead_guide_returnsMarkdown() throws Exception {
        JsonNode result = rpc("resources/read", MAPPER.createObjectNode()
                .put("uri", "tinyexpr://guide"));
        String text = result.get("contents").get(0).get("text").asText();
        assertTrue(text.contains("tinyexpression MCP"));
    }

    @Test
    public void oversizedBody_rejectedWith413() throws Exception {
        long max = McpServer.MAX_REQUEST_BODY_BYTES;
        byte[] padding = new byte[(int) Math.min(max + 1024, Integer.MAX_VALUE - 8)];
        java.util.Arrays.fill(padding, (byte) ' ');
        String body = new String(padding, java.nio.charset.StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(413, resp.statusCode());
    }

    // ─── helpers ──────────────────────────────────────────────────

    private JsonNode rpc(String method, JsonNode params) throws Exception {
        var reqBody = MAPPER.createObjectNode();
        reqBody.put("jsonrpc", "2.0");
        reqBody.put("id", 1);
        reqBody.put("method", method);
        if (params != null) reqBody.set("params", params);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(reqBody)))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals("HTTP response: " + resp.body(), 200, resp.statusCode());
        JsonNode respBody = MAPPER.readTree(resp.body());
        assertNotNull("Response: " + resp.body(), respBody.get("result"));
        return respBody.get("result");
    }

    private HttpResponse<String> httpGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
