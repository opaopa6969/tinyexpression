package org.unlaxer.tinyexpression.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreator;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class McpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final boolean allowJavaCode;

    public McpServer(int port, boolean allowJavaCode) throws IOException {
        this.allowJavaCode = allowJavaCode;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.server.createContext("/mcp", new McpHandler());
        this.server.createContext("/healthz", new HealthHandler());
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
    public int getAddress() { return server.getAddress().getPort(); }

    // ─── /healthz ────────────────────────────────────────────────

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            var body = MAPPER.writeValueAsString(Map.of(
                    "ok", true,
                    "name", McpToolDefs.SERVER_NAME,
                    "version", McpToolDefs.VERSION
            ));
            sendJson(ex, 200, body);
        }
    }

    // ─── /mcp ────────────────────────────────────────────────────

    private class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, MAPPER.writeValueAsString(errorResp(null, -32601, "Method not allowed")));
                return;
            }

            String sessionId = ex.getRequestHeaders().getFirst("Mcp-Session-Id");
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }

            String bodyStr = readBody(ex);
            JsonNode req;
            try {
                req = MAPPER.readTree(bodyStr);
            } catch (Exception e) {
                sendWithSession(ex, 200, MAPPER.writeValueAsString(errorResp(null, -32700, "Parse error")), sessionId);
                return;
            }

            if (req.isArray()) {
                ArrayNode results = MAPPER.createArrayNode();
                for (JsonNode single : req) {
                    results.add(handleSingle(single));
                }
                sendWithSession(ex, 200, MAPPER.writeValueAsString(results), sessionId);
            } else {
                ObjectNode result = handleSingle(req);
                if (!req.has("id") && result == null) {
                    sendWithSession(ex, 202, "", sessionId);
                } else {
                    sendWithSession(ex, 200, MAPPER.writeValueAsString(result), sessionId);
                }
            }
        }

        private ObjectNode handleSingle(JsonNode req) {
            String method = req.path("method").asText("");
            JsonNode id = req.has("id") ? req.get("id") : null;
            JsonNode params = req.path("params");

            try {
                ObjectNode result = switch (method) {
                    case "initialize" -> handleInitialize();
                    case "notifications/initialized" -> null;
                    case "tools/list" -> handleToolsList();
                    case "tools/call" -> handleToolsCall(params);
                    case "resources/list" -> handleResourcesList();
                    case "resources/read" -> handleResourcesRead(params);
                    default -> errorResp(id, -32601, "Method not found: " + method);
                };
                if (result != null) {
                    result.put("jsonrpc", "2.0");
                    if (id != null) {
                        result.set("id", id);
                    } else {
                        return null;
                    }
                }
                return result;
            } catch (Exception e) {
                return errorResp(id, -32603, "Internal error: " + e.getMessage());
            }
        }

        // ─── initialize ─────────────────────────────────────────

        private ObjectNode handleInitialize() {
            ObjectNode resp = MAPPER.createObjectNode();
            ObjectNode resultNode = MAPPER.createObjectNode();
            resultNode.put("protocolVersion", McpToolDefs.PROTOCOL_VERSION);
            ObjectNode caps = MAPPER.createObjectNode();
            caps.putObject("tools").put("listChanged", true);
            caps.putObject("resources").put("listChanged", true);
            resultNode.set("capabilities", caps);
            ObjectNode info = MAPPER.createObjectNode();
            info.put("name", McpToolDefs.SERVER_NAME);
            info.put("version", McpToolDefs.VERSION);
            resultNode.set("serverInfo", info);
            resp.set("result", resultNode);
            return resp;
        }

        // ─── tools/list ──────────────────────────────────────────

        private ObjectNode handleToolsList() {
            ObjectNode resp = MAPPER.createObjectNode();
            ObjectNode result = MAPPER.createObjectNode();
            ArrayNode tools = MAPPER.createArrayNode();
            for (var def : McpToolDefs.tools()) {
                ObjectNode t = MAPPER.createObjectNode();
                t.put("name", def.name());
                t.put("description", def.description());
                t.set("inputSchema", MAPPER.valueToTree(def.inputSchema()));
                ObjectNode annotations = MAPPER.createObjectNode();
                annotations.put("readOnlyHint", true);
                annotations.put("destructiveHint", false);
                annotations.put("idempotentHint", true);
                annotations.put("openWorldHint", false);
                t.set("annotations", annotations);
                tools.add(t);
            }
            result.set("tools", tools);
            resp.set("result", result);
            return resp;
        }

        // ─── tools/call ──────────────────────────────────────────

        private ObjectNode handleToolsCall(JsonNode params) throws Exception {
            String name = params.path("name").asText("");
            JsonNode args = params.path("arguments");

            ObjectNode resp = MAPPER.createObjectNode();
            ObjectNode result = MAPPER.createObjectNode();
            String text;
            try {
                text = switch (name) {
                    case "evaluate" -> handleEvaluate(args);
                    case "validate" -> handleValidate(args);
                    case "execute_batch" -> handleExecuteBatch(args);
                    case "parity_check" -> handleParityCheck(args);
                    case "list_backends" -> handleListBackends();
                    default -> throw new IllegalArgumentException("Unknown tool: " + name);
                };
            } catch (IllegalArgumentException e) {
                return errorResp(null, -32601, e.getMessage());
            }

            ArrayNode content = MAPPER.createArrayNode();
            ObjectNode textItem = MAPPER.createObjectNode();
            textItem.put("type", "text");
            textItem.put("text", text);
            content.add(textItem);
            result.set("content", content);
            resp.set("result", result);
            return resp;
        }

        // ─── evaluate ───────────────────────────────────────────

        private String handleEvaluate(JsonNode args) throws Exception {
            String formula = args.path("formula").asText();
            String backendStr = args.path("backend").asText("AST_EVALUATOR");
            String resultTypeStr = args.path("resultType").asText("float");
            JsonNode variablesNode = args.path("variables");

            ExecutionBackend backend = ExecutionBackend.parse(backendStr)
                    .orElse(ExecutionBackend.AST_EVALUATOR);
            checkBackendAllowed(backend);

            ExpressionTypes resultType = parseResultType(resultTypeStr);
            ExpressionTypes numberType = ExpressionTypes._float;
            SpecifiedExpressionTypes specTypes = new SpecifiedExpressionTypes(resultType, numberType);

            CalculatorCreator creator = CalculatorCreatorRegistry.forBackend(backend);
            Calculator calculator = creator.create(
                    new Source(formula),
                    "McpCalc_" + Integer.toHexString(formula.hashCode()),
                    specTypes,
                    Thread.currentThread().getContextClassLoader());

            CalculationContext ctx = CalculationContext.newConcurrentContext();
            applyVariables(ctx, variablesNode);

            Object evalResult;
            try {
                evalResult = calculator.apply(ctx);
            } catch (Exception e) {
                ObjectNode err = MAPPER.createObjectNode();
                err.put("error", e.getMessage());
                err.put("backend_used", backend.name());
                return MAPPER.writeValueAsString(err);
            }

            ObjectNode out = MAPPER.createObjectNode();
            out.put("backend_used", backend.name());
            out.put("result_type", resultType.name());
            setResultValue(out, "result", evalResult);
            return MAPPER.writeValueAsString(out);
        }

        // ─── validate ───────────────────────────────────────────

        private String handleValidate(JsonNode args) throws Exception {
            String formula = args.path("formula").asText();
            String resultTypeStr = args.path("resultType").asText("float");

            ObjectNode out = MAPPER.createObjectNode();

            Parser parser = Parser.get(FormulaParser.class);
            try (ParseContext pc = new ParseContext(StringSource.createRootSource(formula))) {
                Parsed parsed = parser.parse(pc);
                boolean ok = parsed.isSucceeded() && pc.allConsumed();
                out.put("parse_ok", ok);
                if (!ok) {
                    ArrayNode errors = MAPPER.createArrayNode();
                    ObjectNode err = MAPPER.createObjectNode();
                    String remain = pc.getRemain(org.unlaxer.TokenKind.consumed).toString();
                    err.put("message", "Parse failed. Unconsumed input at position " +
                            (formula.length() - remain.length()) + ": " +
                            (remain.length() > 50 ? remain.substring(0, 50) + "..." : remain));
                    err.put("position", formula.length() - remain.length());
                    errors.add(err);
                    out.set("errors", errors);
                }
                if (ok) {
                    var rootToken = parsed.getRootToken(false);
                    if (rootToken != null) {
                        out.put("ast_node_type", rootToken.getClass().getSimpleName());
                    }
                }
            } catch (Exception e) {
                out.put("parse_ok", false);
                ArrayNode errors = MAPPER.createArrayNode();
                ObjectNode err = MAPPER.createObjectNode();
                err.put("message", e.getMessage());
                errors.add(err);
                out.set("errors", errors);
            }

            return MAPPER.writeValueAsString(out);
        }

        // ─── execute_batch ─────────────────────────────────────

        private String handleExecuteBatch(JsonNode args) throws Exception {
            JsonNode formulasNode = args.path("formulas");
            JsonNode variablesNode = args.path("variables");

            CalculationContext ctx = CalculationContext.newConcurrentContext();
            applyVariables(ctx, variablesNode);

            List<BatchFormula> batchFormulas = new ArrayList<>();
            Map<String, Calculator> calculators = new LinkedHashMap<>();

            for (JsonNode fn : formulasNode) {
                BatchFormula bf = new BatchFormula();
                bf.name = fn.path("name").asText();
                bf.formula = fn.path("formula").asText();
                bf.dependsOn = new ArrayList<>();
                if (fn.has("dependsOn")) {
                    for (JsonNode d : fn.get("dependsOn")) {
                        bf.dependsOn.add(d.asText());
                    }
                }
                String backendStr = fn.path("backend").asText("AST_EVALUATOR");
                String resultTypeStr = fn.path("resultType").asText("float");
                bf.backend = ExecutionBackend.parse(backendStr).orElse(ExecutionBackend.AST_EVALUATOR);
                bf.resultType = parseResultType(resultTypeStr);
                checkBackendAllowed(bf.backend);
                batchFormulas.add(bf);
            }

            for (BatchFormula bf : batchFormulas) {
                SpecifiedExpressionTypes specTypes = new SpecifiedExpressionTypes(bf.resultType, ExpressionTypes._float);
                CalculatorCreator creator = CalculatorCreatorRegistry.forBackend(bf.backend);
                Calculator calc = creator.create(
                        new Source(bf.formula),
                        "McpBatch_" + bf.name + "_" + Integer.toHexString(bf.formula.hashCode()),
                        specTypes,
                        Thread.currentThread().getContextClassLoader());
                for (String dep : bf.dependsOn) {
                    Calculator depCalc = calculators.get(dep);
                    if (depCalc != null) {
                        calc.addDependsOn(depCalc);
                    }
                }
                calculators.put(bf.name, calc);
            }

            List<Calculator> ordered = new ArrayList<>(calculators.values());
            ordered.sort(Comparator.comparingInt(Calculator::dependsOnByNestLevel).reversed());

            ArrayNode results = MAPPER.createArrayNode();
            Map<String, Object> resultByName = new LinkedHashMap<>();
            for (Calculator calc : ordered) {
                calc.before(ctx);
                Object evalResult;
                try {
                    evalResult = calc.apply(ctx);
                    calc.after(ctx);
                } catch (Exception e) {
                    ObjectNode r = MAPPER.createObjectNode();
                    r.put("name", findFormulaName(calc, batchFormulas, calculators));
                    r.put("backend_used", calc.getObject("_tinyExecutionBackend", String.class));
                    results.add(r);
                    continue;
                }

                String formulaName = findFormulaName(calc, batchFormulas, calculators);

                ObjectNode r = MAPPER.createObjectNode();
                r.put("name", formulaName);
                r.put("backend_used", calc.getObject("_tinyExecutionBackend", String.class));
                setResultValue(r, "result", evalResult);
                results.add(r);
                resultByName.put(formulaName, evalResult);

                if (evalResult instanceof Number n) {
                    ctx.set(formulaName, n.floatValue());
                } else if (evalResult instanceof Boolean b) {
                    ctx.set(formulaName, b);
                } else if (evalResult instanceof String s) {
                    ctx.set(formulaName, s);
                }
            }

            ObjectNode out = MAPPER.createObjectNode();
            out.set("results", results);
            ObjectNode varsOut = MAPPER.createObjectNode();
            for (String name : getAllVariableNames(batchFormulas, variablesNode)) {
                var val = ctx.getValue(name);
                if (val.isPresent()) {
                    varsOut.put(name, val.get());
                    continue;
                }
                var sval = ctx.getString(name);
                if (sval.isPresent()) {
                    varsOut.put(name, sval.get());
                    continue;
                }
                var bval = ctx.getBoolean(name);
                if (bval.isPresent()) {
                    varsOut.put(name, bval.get());
                }
            }
            out.set("variables", varsOut);
            return MAPPER.writeValueAsString(out);
        }

        // ─── parity_check ───────────────────────────────────────

        private String handleParityCheck(JsonNode args) throws Exception {
            String formula = args.path("formula").asText();
            String resultTypeStr = args.path("resultType").asText("float");
            JsonNode variablesNode = args.path("variables");

            ExpressionTypes resultType = parseResultType(resultTypeStr);
            SpecifiedExpressionTypes specTypes = new SpecifiedExpressionTypes(resultType, ExpressionTypes._float);

            ExecutionBackend[] allBackends = ExecutionBackend.values();
            ArrayNode backendResults = MAPPER.createArrayNode();
            List<Object> results = new ArrayList<>();
            boolean equalAll = true;
            Object firstResult = null;
            boolean firstSet = false;

            for (ExecutionBackend backend : allBackends) {
                if (!allowJavaCode && isJavaCodeBackend(backend)) {
                    ObjectNode r = MAPPER.createObjectNode();
                    r.put("name", backend.name());
                    r.put("skipped", true);
                    r.put("reason", "JAVA_CODE backend not allowed");
                    backendResults.add(r);
                    continue;
                }

                CalculationContext ctx = CalculationContext.newConcurrentContext();
                applyVariables(ctx, variablesNode);

                try {
                    CalculatorCreator creator = CalculatorCreatorRegistry.forBackend(backend);
                    Calculator calc = creator.create(
                            new Source(formula),
                            "McpParity_" + backend.name() + "_" + Integer.toHexString(formula.hashCode()),
                            specTypes,
                            Thread.currentThread().getContextClassLoader());
                    Object result = calc.apply(ctx);
                    results.add(result);

                    ObjectNode r = MAPPER.createObjectNode();
                    r.put("name", backend.name());
                    setResultValue(r, "result", result);
                    if (firstSet) {
                        boolean equal = resultsEqual(firstResult, result);
                        r.put("equal", equal);
                        if (!equal) equalAll = false;
                    } else {
                        r.put("equal", true);
                        firstResult = result;
                        firstSet = true;
                    }
                    backendResults.add(r);
                } catch (Exception e) {
                    ObjectNode r = MAPPER.createObjectNode();
                    r.put("name", backend.name());
                    r.put("error", e.getMessage());
                    r.put("equal", false);
                    equalAll = false;
                    backendResults.add(r);
                }
            }

            ObjectNode out = MAPPER.createObjectNode();
            out.set("backends", backendResults);
            out.put("equal_all", equalAll);
            return MAPPER.writeValueAsString(out);
        }

        // ─── list_backends ──────────────────────────────────────

        private String handleListBackends() throws Exception {
            ArrayNode arr = MAPPER.createArrayNode();
            for (ExecutionBackend backend : ExecutionBackend.values()) {
                ObjectNode b = MAPPER.createObjectNode();
                b.put("name", backend.name());
                b.put("runtimeMode", backend.runtimeModeMarker());
                b.put("implementation", backend.runtimeImplementationMarker());
                b.put("bridge", backend.bridgeImplementation());
                b.put("allowed", allowJavaCode || !isJavaCodeBackend(backend));
                arr.add(b);
            }
            return MAPPER.writeValueAsString(arr);
        }

        // ─── resources/list ──────────────────────────────────────

        private ObjectNode handleResourcesList() {
            ObjectNode resp = MAPPER.createObjectNode();
            ObjectNode result = MAPPER.createObjectNode();
            ArrayNode resources = MAPPER.createArrayNode();

            resources.add(resourceEntry("tinyexpr://spec", "tinyexpr-spec", "能力仕様 (機械可読)", "application/json"));
            resources.add(resourceEntry("tinyexpr://guide", "tinyexpr-guide", "使い方ガイド", "text/markdown"));
            resources.add(resourceEntry("tinyexpr://language", "tinyexpr-language", "言語仕様概要", "text/markdown"));
            resources.add(resourceEntry("tinyexpr://backends", "tinyexpr-backends", "バックエンド仕様", "application/json"));

            result.set("resources", resources);
            resp.set("result", result);
            return resp;
        }

        private ObjectNode resourceEntry(String uri, String name, String description, String mime) {
            ObjectNode r = MAPPER.createObjectNode();
            r.put("uri", uri);
            r.put("name", name);
            r.put("description", description);
            r.put("mimeType", mime);
            return r;
        }

        // ─── resources/read ──────────────────────────────────────

        private ObjectNode handleResourcesRead(JsonNode params) {
            String uri = params.path("uri").asText("");
            ObjectNode resp = MAPPER.createObjectNode();
            ObjectNode result = MAPPER.createObjectNode();
            ArrayNode contents = MAPPER.createArrayNode();
            ObjectNode item = MAPPER.createObjectNode();

            switch (uri) {
                case "tinyexpr://spec" -> {
                    item.put("uri", uri);
                    item.put("mimeType", "application/json");
                    item.put("text", buildSpecJson());
                }
                case "tinyexpr://guide" -> {
                    item.put("uri", uri);
                    item.put("mimeType", "text/markdown");
                    item.put("text", buildGuideMarkdown());
                }
                case "tinyexpr://language" -> {
                    item.put("uri", uri);
                    item.put("mimeType", "text/markdown");
                    item.put("text", buildLanguageMarkdown());
                }
                case "tinyexpr://backends" -> {
                    item.put("uri", uri);
                    item.put("mimeType", "application/json");
                    item.put("text", buildBackendsJson());
                }
                default -> {
                    return errorResp(null, -32602, "Unknown resource URI: " + uri);
                }
            }

            contents.add(item);
            result.set("contents", contents);
            resp.set("result", result);
            return resp;
        }

        private String buildSpecJson() {
            try {
                ObjectNode spec = MAPPER.createObjectNode();
                spec.put("namespace", McpToolDefs.NAMESPACE);
                spec.put("name", McpToolDefs.SERVER_NAME);
                spec.put("version", McpToolDefs.VERSION);
                spec.put("summary", "Java 組み込み式評価エンジン。ランタイムで式文字列を評価し、複数式を依存関係付きで実行する。6バックエンド（AST/P4/JavaCode系列）をサポート。");

                ArrayNode caps = MAPPER.createArrayNode();
                for (var def : McpToolDefs.tools()) {
                    ObjectNode c = MAPPER.createObjectNode();
                    c.put("kind", "tool");
                    c.put("name", def.name());
                    c.put("summary", def.description());
                    c.put("side_effect", "none");
                    c.put("long_running", false);
                    c.put("dry_run", false);
                    c.put("min_role", "VIEWER");
                    caps.add(c);
                }
                for (String u : List.of("tinyexpr://spec", "tinyexpr://guide", "tinyexpr://language", "tinyexpr://backends")) {
                    ObjectNode c = MAPPER.createObjectNode();
                    c.put("kind", "resource");
                    c.put("uri", u);
                    caps.add(c);
                }
                spec.set("capabilities", caps);

                ArrayNode comps = MAPPER.createArrayNode();
                ObjectNode comp1 = MAPPER.createObjectNode();
                comp1.put("title", "統計データでスコアリング");
                comp1.put("flow", MAPPER.valueToTree(List.of("mstats__population", "tinyexpr__evaluate")));
                comp1.put("note", "人口データを変数に流し込みスコアリング式で評価");
                comps.add(comp1);
                ObjectNode comp2 = MAPPER.createObjectNode();
                comp2.put("title", "着工数×事業所数で地域指標");
                comp2.put("flow", MAPPER.valueToTree(List.of("building_starts__total_count", "estcensus__establishment_count", "tinyexpr__execute_batch")));
                comp2.put("note", "着工数と事業所数を変数に投入し依存関係付き複数式で地域指標を算出");
                comps.add(comp2);
                ObjectNode comp3 = MAPPER.createObjectNode();
                comp3.put("title", "構文チェック→評価のワンストップ");
                comp3.put("flow", MAPPER.valueToTree(List.of("tinyexpr__validate", "tinyexpr__evaluate")));
                comp3.put("note", "構文エラーを先に検出し問題なければ評価");
                comps.add(comp3);
                spec.set("compositions", comps);

                ArrayNode deps = MAPPER.createArrayNode();
                deps.add(MAPPER.valueToTree(Map.of("namespace", "mstats", "capability", "mstats__population")));
                deps.add(MAPPER.valueToTree(Map.of("namespace", "building_starts", "capability", "building_starts__total_count")));
                deps.add(MAPPER.valueToTree(Map.of("namespace", "estcensus", "capability", "estcensus__establishment_count")));
                spec.set("depends_on", deps);

                spec.put("health", "/healthz");
                spec.set("docs", MAPPER.valueToTree(List.of("tinyexpr://guide", "tinyexpr://language")));
                return MAPPER.writeValueAsString(spec);
            } catch (Exception e) {
                return "{}";
            }
        }

        private String buildGuideMarkdown() {
            return """
                    # tinyexpression MCP ガイド

                    ## 概要
                    Java 組み込み式評価エンジン（org.unlaxer:tinyExpression:1.4.11）の MCP サーバ。
                    ランタイムで式文字列を評価し、複数式を依存関係付きで実行する。

                    ## tools

                    ### evaluate
                    式を評価する。
                    入力: `{formula: "1+2", variables: {x: 5}, backend: "AST_EVALUATOR", resultType: "float"}`
                    出力: `{result: 3.0, backend_used: "AST_EVALUATOR", result_type: "_float"}`

                    ### validate
                    式をパースして診断する（評価はしない）。
                    入力: `{formula: "1+2"}`
                    出力: `{parse_ok: true, ast_node_type: "..."}` または `{parse_ok: false, errors: [{position, message}]}`

                    ### execute_batch
                    複数式を依存関係付きで実行する。
                    入力: `{formulas: [{name: "base", formula: "$x * 2"}, {name: "total", formula: "$base + 100", dependsOn: ["base"]}], variables: {x: 5}}`
                    出力: `{results: [{name: "base", result: 10.0, ...}, {name: "total", result: 110.0, ...}], variables: {x: 5.0, base: 10.0, total: 110.0}}`

                    ### parity_check
                    バックエンド間のパリティを比較する。全バックエンドで同じ式を評価し結果が一致するか確認。
                    入力: `{formula: "1+2"}`
                    出力: `{backends: [{name: "AST_EVALUATOR", result: 3.0, equal: true}, ...], equal_all: true}`

                    ### list_backends
                    利用可能な評価バックエンド一覧を返す。
                    出力: `[{name: "AST_EVALUATOR", runtimeMode: "ast-evaluator", implementation: "ast-evaluator", bridge: false, allowed: true}, ...]`

                    ## バックエンド
                    | 名前 | runtimeMode | 安全 | 説明 |
                    |------|-------------|------|------|
                    | AST_EVALUATOR | ast-evaluator | ○ | AST 直接評価（既定・推奨） |
                    | P4_AST_EVALUATOR | p4-ast | ○ | P4 AST 評価 |
                    | JAVA_CODE | javacode | △ | Java コード生成+コンパイル（TINYEXPR_ALLOW_JAVA_CODE=true の時のみ） |
                    | DSL_JAVA_CODE | dsl-javacode | △ | DSL 経由 Java コード生成 |
                    | P4_DSL_JAVA_CODE | p4-dsl-javacode | △ | P4 DSL 経由 Java コード生成 |
                    | JAVA_CODE_LEGACY_ASTCREATOR | legacy-astcreator | △ | レガシー AST 生成器 |

                    ## 変数
                    variables は JSON オブジェクト `{name: value}`。value は:
                    - number → 数値変数
                    - boolean → 真偽値変数
                    - string → 文字列変数

                    式内では `$name` で参照。

                    ## 組み合わせ例
                    1. `mstats__population` → `tinyexpr__evaluate`: 人口を変数に流し込みスコアリング
                    2. `building_starts__total_count` + `estcensus__establishment_count` → `tinyexpr__execute_batch`: 地域指標算出
                    3. `tinyexpr__validate` → `tinyexpr__evaluate`: 構文チェック→評価のワンストップ

                    ## セキュリティ
                    - デフォルトバックエンド: AST_EVALUATOR（コード生成なし）
                    - JAVA_CODE 系は環境変数 `TINYEXPR_ALLOW_JAVA_CODE=true` の時のみ利用可能
                    - Java コードブロック・external Java メソッド呼び出しは無効
                    """;
        }

        private String buildLanguageMarkdown() {
            return """
                    # TinyExpression 言語仕様概要

                    ## リテラル
                    - 数値: `1`, `2.5`, `100`
                    - 文字列: `"hello"`, `'world'`
                    - 真偽値: `true`, `false`

                    ## 変数
                    `$name` で参照。variables で渡した値が入る。

                    ## 演算子
                    - 算術: `+`, `-`, `*`, `/`, `%`
                    - 比較: `==`, `!=`, `>`, `<`, `>=`, `<=`
                    - 論理: `and`, `or`, `not`
                    - 文字列結合: `+`

                    ## 制御構文
                    - if式: `if($x > 0){1}else{0}`
                    - match式: `match($x){case 1: "one", case 2: "two", default: "other"}`
                    - 三項演算子: `$x > 0 ? 1 : 0`

                    ## 組み込み関数
                    - 算術: `abs()`, `ceil()`, `floor()`, `round()`, `max()`, `min()`, `sqrt()`, `pow()`, `exp()`, `log()`, `sin()`, `cos()`, `tan()`, `random()`
                    - 文字列: `length()`, `trim()`, `toUpperCase()`, `toLowerCase()`, `startsWith()`, `endsWith()`, `contains()`, `toNum()`
                    - 検査: `isPresent()`, `len()`
                    - 時間: `dayOfWeek()`, `inTimeRange()`, `inDayTimeRange()`

                    ## 文字列メソッド
                    `.length`, `.trim()`, `.toUpperCase()`, `.toLowerCase()`, `.startsWith()`, `.endsWith()`, `.contains()`

                    ## 変数宣言
                    `number x = 5;` `string name = "opa";` `boolean flag = true;`

                    ## 詳細
                    https://github.com/opaopa6969/tinyexpression の docs/language-guide.md を参照。
                    """;
        }

        private String buildBackendsJson() {
            try {
                ArrayNode arr = MAPPER.createArrayNode();
                for (ExecutionBackend backend : ExecutionBackend.values()) {
                    ObjectNode b = MAPPER.createObjectNode();
                    b.put("name", backend.name());
                    b.put("runtimeMode", backend.runtimeModeMarker());
                    b.put("implementation", backend.runtimeImplementationMarker());
                    b.put("bridge", backend.bridgeImplementation());
                    b.put("allowed_by_default", !isJavaCodeBackend(backend));
                    b.put("requires_java_code_permission", isJavaCodeBackend(backend));
                    arr.add(b);
                }
                return MAPPER.writeValueAsString(arr);
            } catch (Exception e) {
                return "[]";
            }
        }
    }

    // ─── helper methods ──────────────────────────────────────────

    private void checkBackendAllowed(ExecutionBackend backend) {
        if (!allowJavaCode && isJavaCodeBackend(backend)) {
            throw new IllegalArgumentException(
                    "Backend " + backend.name() + " requires TINYEXPR_ALLOW_JAVA_CODE=true");
        }
    }

    private static boolean isJavaCodeBackend(ExecutionBackend backend) {
        return backend == ExecutionBackend.JAVA_CODE
                || backend == ExecutionBackend.DSL_JAVA_CODE
                || backend == ExecutionBackend.P4_DSL_JAVA_CODE
                || backend == ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR;
    }

    private static ExpressionTypes parseResultType(String resultTypeStr) {
        return switch (resultTypeStr.toLowerCase().strip()) {
            case "float", "_float", "number" -> ExpressionTypes._float;
            case "double", "_double" -> ExpressionTypes._double;
            case "int", "_int", "integer" -> ExpressionTypes._int;
            case "long", "_long" -> ExpressionTypes._long;
            case "string", "_string" -> ExpressionTypes.string;
            case "boolean", "_boolean", "bool" -> ExpressionTypes._boolean;
            case "object" -> ExpressionTypes.object;
            case "bigdecimal", "_bigdecimal" -> ExpressionTypes.bigDecimal;
            default -> ExpressionTypes._float;
        };
    }

    private static void applyVariables(CalculationContext ctx, JsonNode variablesNode) {
        if (variablesNode == null || !variablesNode.isObject()) return;
        var fields = variablesNode.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNumber()) {
                ctx.set(name, (float) value.asDouble());
            } else if (value.isBoolean()) {
                ctx.set(name, value.booleanValue());
            } else {
                ctx.set(name, value.asText());
            }
        }
    }

    private static void setResultValue(ObjectNode node, String field, Object result) {
        if (result == null) {
            node.putNull(field);
        } else if (result instanceof Number n) {
            if (result instanceof Float f) {
                node.put(field, f);
            } else if (result instanceof Double d) {
                node.put(field, d);
            } else if (result instanceof Integer i) {
                node.put(field, i);
            } else if (result instanceof Long l) {
                node.put(field, l);
            } else if (result instanceof BigDecimal bd) {
                node.put(field, bd.doubleValue());
            } else {
                node.put(field, n.doubleValue());
            }
        } else if (result instanceof Boolean b) {
            node.put(field, b);
        } else if (result instanceof String s) {
            node.put(field, s);
        } else {
            node.put(field, result.toString());
        }
    }

    private static boolean resultsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number na && b instanceof Number nb) {
            return Math.abs(na.doubleValue() - nb.doubleValue()) < 0.001;
        }
        return a.equals(b);
    }

    private static String findFormulaName(Calculator calc, List<BatchFormula> batchFormulas, Map<String, Calculator> calculators) {
        for (Map.Entry<String, Calculator> entry : calculators.entrySet()) {
            if (entry.getValue() == calc) {
                return entry.getKey();
            }
        }
        return "unknown";
    }

    private static List<String> getAllVariableNames(List<BatchFormula> batchFormulas, JsonNode variablesNode) {
        List<String> names = new ArrayList<>();
        if (variablesNode != null && variablesNode.isObject()) {
            var fields = variablesNode.fields();
            while (fields.hasNext()) {
                names.add(fields.next().getKey());
            }
        }
        for (BatchFormula bf : batchFormulas) {
            names.add(bf.name);
        }
        return names;
    }

    // ─── batch formula holder ────────────────────────────────────

    private static class BatchFormula {
        String name;
        String formula;
        List<String> dependsOn;
        ExecutionBackend backend;
        ExpressionTypes resultType;
    }

    // ─── HTTP helpers ──────────────────────────────────────────

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("content-encoding", "identity");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendWithSession(HttpExchange ex, int status, String body, String sessionId) throws IOException {
        if (sessionId != null) {
            ex.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        }
        if (body.isEmpty() && status == 202) {
            ex.getResponseHeaders().set("content-encoding", "identity");
            ex.sendResponseHeaders(202, -1);
        } else {
            sendJson(ex, status, body);
        }
    }

    private static ObjectNode errorResp(JsonNode id, int code, String message) {
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        ObjectNode err = MAPPER.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        resp.set("error", err);
        if (id != null) {
            resp.set("id", id);
        }
        return resp;
    }

    // ─── main ────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "9237"));
        boolean allowJavaCode = "true".equalsIgnoreCase(System.getenv().getOrDefault("TINYEXPR_ALLOW_JAVA_CODE", "false"));

        var srv = new McpServer(port, allowJavaCode);
        srv.start();
        System.err.println("tinyexpression MCP server listening on port " + port
                + " (allowJavaCode=" + allowJavaCode + ")");
    }
}
