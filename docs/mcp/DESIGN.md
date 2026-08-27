# TinyExpression MCP 化設計（Phase 2）

> 設計日: 2026-08-22 | namespace: `tinyexpr` | port: 9237 | kind: `library-serve`

## 1. namespace と種別

- **namespace**: `tinyexpr`（割当表 #40 指定）
- **種別**: `library-serve`（Java ライブラリを新規 MCP サーバ化）
- **基盤**: Java 21 + `com.sun.net.httpserver.HttpServer`（housing-stock MCP と同パターン）

## 2. tools 表

| name | 目的 | 入力 schema 要点 | 出力の形 | 副作用 | dry-run | job型 | 所要 | min_role |
|------|------|-------------------|----------|--------|---------|-------|------|----------|
| `evaluate` | 式を評価する | `{formula: string, variables?: {name: value}, backend?: string, resultType?: string}` | `{result, backend_used, result_type}` | none | no | no | <1s | VIEWER |
| `validate` | 式をパースして診断する | `{formula: string, resultType?: string}` | `{parse_ok, errors?, ast_node_type?, backend_used?}` | none | no | no | <1s | VIEWER |
| `execute_batch` | 複数式を依存関係付きで実行 | `{formulas: [{name, formula, dependsOn?: string[], resultType?: string, backend?: string}], variables?: {name: value}}` | `{results: [{name, result, backend_used}], variables}` | none | no | no | <5s | VIEWER |
| `parity_check` | バックエンド間のパリティを比較 | `{formula: string, variables?: {name: value}, resultType?: string}` | `{backends: [{name, result, equal}], equal_all}` | none | no | no | <5s | VIEWER |
| `list_backends` | 利用可能なバックエンド一覧 | `{}` | `[{name, runtimeMode, implementation, bridge}]` | none | no | no | <0.1s | VIEWER |

### 設計判断

- **デフォルトバックエンド**: `AST_EVALUATOR`（Java コード生成・コンパイル不要。セキュリティリスクなし。`--add-opens` 不要）
- **JAVA_CODE 系バックエンド**: `allow_java_code` 環境変数が `true` の場合のみ許可。デフォルトは無効（セキュリティ）
- **external Java メソッド呼び出し**: MCP 経由では無効（ホワイトリストなし）
- **Java コードブロック**: MCP 経由では無効（`JavaCodeBlockPolicy` デフォルト無効）
- **resultType**: デフォルト `_float`（数式の場合）。`string`/`boolean`/`object` も指定可能
- **variables**: JSON オブジェクト `{name: value}`。value は number/boolean/string に自動判定
- **execute_batch**: JSON 配列で受ける（FormulaInfo.txt 形式は support しない。Phase 1 open_question #4 の回答: JSON 配列を優先）
- **全 tool readOnlyHint**: 破壊的操作なし

## 3. resources 表

| uri | 内容 | mime |
|-----|------|------|
| `tinyexpr://spec` | 能力仕様（機械可読 JSON） | application/json |
| `tinyexpr://guide` | 使い方ガイド | text/markdown |
| `tinyexpr://language` | 言語仕様概要 | text/markdown |
| `tinyexpr://backends` | バックエンド仕様 | application/json |

## 4. prompts / skills

| name | 用途 | locality |
|------|------|----------|
| `write-formula-info` | FormulaInfo 記法の書き方 | repo (`docs/skills/write-formula-info/SKILL.md`) |
| `choose-backend` | バックエンド選択の判断基準 | repo (`docs/skills/choose-backend/SKILL.md`) |

## 5. 組み合わせ例

1. **mstats__population → tinyexpr__evaluate**: 人口データを変数に流し込みスコアリング式で評価
   - `mstats__population(lgCode=13101, year=2020)` → `{value: 59500}` → `tinyexpr__evaluate(formula="$population * 0.01 + 100", variables={population: 59500})`
2. **building_starts__total_count + estcensus__establishment_count → tinyexpr__execute_batch**: 着工数と事業所数を変数に投入し依存関係付き複数式で地域指標を算出
   - `building_starts__total_count(lgCode=13101, year=2024)` → `{count: 500}` / `estcensus__establishment_count(lgCode=13101)` → `{count: 12000}` → `tinyexpr__execute_batch(formulas=[{name:"density", formula:"$buildings / $establishments * 1000"}, {name:"score", formula:"if($density > 50){$density * 2}else{$density}", dependsOn:["density"]}])`
3. **tinyexpr__validate → tinyexpr__evaluate**: 構文エラーを先に検出し問題なければ評価
   - `tinyexpr__validate(formula="if($x>0){1}else{0}")` → `{parse_ok: true}` → `tinyexpr__evaluate(formula="if($x>0){1}else{0}", variables={x: 5})`

## 6. 依存と協調

| 相手 repo | 方向 | 能力 | 現状 | 備考 |
|-----------|------|------|------|------|
| municipality-stats | provides_to | `tinyexpr__evaluate` の変数入力として統計データを供給 | 相手は MCP あり(`mstats`) | 組み合わせ可能 |
| building-starts | provides_to | `tinyexpr__execute_batch` の変数入力として建設着工数を供給 | 相手は MCP あり(`building_starts`) | 組み合わせ可能 |
| establishment-census | provides_to | `tinyexpr__execute_batch` の変数入力として事業所数を供給 | 相手は MCP あり(`estcensus`) | 組み合わせ可能 |

協調が要るものは今のところない（相手側に MCP 入口が既にあるため。Phase 1 survey の「将来サーバ化で直接連携可能」が既に実現している）。

## 7. 非対応にした候補と理由

- **Java コードブロック機能**: JVM 上で任意コードを実行するセキュリティリスクのため無効
- **external Java メソッド呼び出し**: クラスパス依存の制約が大きいため無効
- **FormulaInfo.txt ファイル形式の直接入力**: JSON 配列形式を優先（Phase 1 open_question #4）
- **LSP/DAP 機能**: MCP サーバとは別モジュール。本 Phase 2 では評価能力のみ提供

## 8. 参加方法

| 項目 | 値 |
|------|-----|
| id | `tinyexpression` |
| hostname | `tinyexpr.unlaxer.org` |
| port | 9237 |
| host | 192.168.1.50 (prod) |
| runtime | systemd (user unit) |
| type | java |
| auth | minRole:MEMBER |
| health_check | /healthz |
| exec_start | /home/opa/tinyexpression/run.sh |
| mcp.namespace | tinyexpr |
| mcp.min_role | MEMBER |
| mcp.timeoutMs | 110000 |

## 9. テスト方針

e2e テスト（housing-stock MCP と同パターン）:
1. `/healthz` が 200 を返す
2. `initialize` → protocolVersion / capabilities / serverInfo
3. `tools/list` → 5 tools (evaluate / validate / execute_batch / parity_check / list_backends)
4. `tools/call evaluate` → `1+2` で `3.0`
5. `tools/call evaluate` → 変数付き `if($x>0){1}else{0}` で `1.0`
6. `tools/call validate` → 正常系 `1+2` で `parse_ok: true`
7. `tools/call validate` → 異常系 `1+` で `parse_ok: false`, errors あり
8. `tools/call execute_batch` → 依存関係付き 2 式
9. `tools/call list_backends` → 6 バックエンド
10. `resources/list` → spec / guide / language / backends
11. `resources/read tinyexpr://spec` → JSON 仕様
12. `resources/read tinyexpr://guide` → Markdown ガイド

## 10. セキュリティ設計

- **デフォルトバックエンド**: `AST_EVALUATOR`（コード生成・コンパイルなし）
- **JAVA_CODE 系**: 環境変数 `TINYEXPR_ALLOW_JAVA_CODE=true` の時のみ許可
- **Java コードブロック**: 常に無効
- **external Java メソッド**: 常に無効
- **JVM オプション**: `--add-opens=java.base/java.lang=ALL-UNNAMED`（AST_EVALUATOR のリフレクション用。安全のため付与）
