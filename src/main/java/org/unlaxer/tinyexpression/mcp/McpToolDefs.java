package org.unlaxer.tinyexpression.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpToolDefs {

    private McpToolDefs() {}

    public static final String NAMESPACE = "tinyexpr";
    public static final String VERSION = "1.4.11-mcp.1";
    public static final String SERVER_NAME = "tinyexpression-mcp";
    public static final String PROTOCOL_VERSION = "2025-03-26";

    public record ToolDef(
            String name,
            String description,
            Map<String, Object> inputSchema
    ) {}

    public static List<ToolDef> tools() {
        return List.of(
                new ToolDef("evaluate",
                        "式を評価する。入力: formula(式文字列), variables(変数名→値のMap), backend(評価バックエンド。省略時AST_EVALUATOR), resultType(結果型。省略時float)。" +
                                "出力: {result, backend_used, result_type}。安全のためJAVA_CODE系バックエンドは環境変数TINYEXPR_ALLOW_JAVA_CODE=trueの時のみ利用可能。",
                        schema("object",
                                Map.of(
                                        "formula", Map.of("type", "string", "description", "評価する式（例: 1+2, if($x>0){1}else{0}）"),
                                        "variables", Map.of("type", "object", "description", "変数名→値のMap（数値・真偽値・文字列を自動判定）", "additionalProperties", true),
                                        "backend", Map.of("type", "string", "description", "評価バックエンド: AST_EVALUATOR(既定) / P4_AST_EVALUATOR / JAVA_CODE / DSL_JAVA_CODE / P4_DSL_JAVA_CODE / JAVA_CODE_LEGACY_ASTCREATOR"),
                                        "resultType", Map.of("type", "string", "description", "結果型: float(既定) / string / boolean / object", "default", "float")
                                ),
                                List.of("formula")
                        )
                ),
                new ToolDef("validate",
                        "式をパースして診断する（評価はしない）。入力: formula, resultType?。出力: {parse_ok, errors?, ast_node_type?}。" +
                                "構文エラーの位置とメッセージを返す。",
                        schema("object",
                                Map.of(
                                        "formula", Map.of("type", "string", "description", "検証する式"),
                                        "resultType", Map.of("type", "string", "description", "期待する結果型（省略時float）", "default", "float")
                                ),
                                List.of("formula")
                        )
                ),
                new ToolDef("execute_batch",
                        "複数式を依存関係付きで実行する。入力: formulas({name, formula, dependsOn?, resultType?, backend?}の配列), variables?。" +
                                "出力: {results: [{name, result, backend_used}], variables}。依存関係はdependsOnで指定（名前参照）。",
                        schema("object",
                                Map.of(
                                        "formulas", Map.of("type", "array", "description", "式定義の配列", "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "name", Map.of("type", "string", "description", "式の名前（依存参照用）"),
                                                        "formula", Map.of("type", "string", "description", "式文字列"),
                                                        "dependsOn", Map.of("type", "array", "items", Map.of("type", "string"), "description", "依存する式の名前配列"),
                                                        "resultType", Map.of("type", "string", "description", "結果型（省略時float）"),
                                                        "backend", Map.of("type", "string", "description", "バックエンド（省略時AST_EVALUATOR）")
                                                ),
                                                "required", List.of("name", "formula")
                                        )),
                                        "variables", Map.of("type", "object", "description", "初期変数（名前→値）", "additionalProperties", true)
                                ),
                                List.of("formulas")
                        )
                ),
                new ToolDef("parity_check",
                        "バックエンド間のパリティを比較する。全バックエンドで同じ式を評価し結果が一致するか確認。" +
                                "入力: formula, variables?, resultType?。出力: {backends: [{name, result, equal}], equal_all}。",
                        schema("object",
                                Map.of(
                                        "formula", Map.of("type", "string", "description", "評価する式"),
                                        "variables", Map.of("type", "object", "description", "変数（名前→値）", "additionalProperties", true),
                                        "resultType", Map.of("type", "string", "description", "結果型（省略時float）", "default", "float")
                                ),
                                List.of("formula")
                        )
                ),
                new ToolDef("list_backends",
                        "利用可能な評価バックエンド一覧を返す。入力なし。" +
                                "出力: [{name, runtimeMode, implementation, bridge}]。6バックエンド（JAVA_CODE系は制限時あり）。",
                        schema("object", Map.of(), List.of())
                )
        );
    }

    private static Map<String, Object> schema(String type, Map<String, Object> properties, List<String> required) {
        var m = new LinkedHashMap<String, Object>();
        m.put("type", type);
        m.put("properties", properties);
        m.put("required", required);
        return m;
    }
}
