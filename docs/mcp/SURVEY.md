# TinyExpression MCP 化調査（Phase 1）

> 調査日: 2026-08-22 | 判定: `library-serve`

## 概要

TinyExpression は **Java アプリケーションに組み込み可能な式評価エンジン（UDF スタイル）** である。ランタイムで式文字列を評価し、複数式を依存関係付きで実行する。Maven Central 公開済み（`org.unlaxer:tinyexpression:1.4.11`）。

6 つの実行バックエンド（JavaCode / AST / P4 系列）を持ち、VS Code 拡張（LSP/DAP）も提供する。純粋な Java ライブラリであり、常駐サーバ・HTTP API・CLI は持たない。

volta カタログには `tinyexpression`（type: library）として登録済みだが、MCP バックエンド・URL は未設定（environments 空）。

## 判定と理由

**判定: `library-serve`**（ライブラリを新規サーバ化して volta 参加）

根拠:

- 式評価エンジンを MCP サーバ化すれば、エージェントが直接「式を評価する」能力を呼べるようになる。入出力が明確（`{formula, variables} → {result}`）で tool に適している。
- `municipality-stats` / `building-starts` / `establishment-census` 等のデータ系ライブラリの値を `CalculationContext` に流し込んで式評価する、複数サービスの組み合わせが描ける。
- `JAVA_CODE` バックエンドは初回コンパイル時に `javac` を呼ぶため起動オーバーヘッドがあり、常駐サーバ化でコンパイルキャッシュが効くメリットがある。
- 起動 1 秒以内で済む軽い処理ではない（JVM 起動 + クラスロード + コンパイル）ため、常駐の価値がある。

## 公開候補

| kind | name | io | 副作用 | 長時間 |
|------|------|----|--------|--------|
| tool | `evaluate` | `{formula, variables, backend?} → {result, backend_used, markers}` | none | false |
| tool | `validate` | `{formula} → {parse_ok, errors?, ast_node_type?}` | none | false |
| tool | `execute_batch` | `{formulas[], variables[]} → {results[], variables}` | none | false |
| tool | `parity_check` | `{formula, variables} → {backends[], equal_all}` | none | false |
| resource | `spec` | `tinyexpr://spec` — 能力の機械可読仕様 | — | — |
| resource | `guide` | `tinyexpr://guide` — 使い方 | — | — |
| resource | `language` | `tinyexpr://language` — 言語仕様 | — | — |
| resource | `backends` | `tinyexpr://backends` — バックエンド仕様 | — | — |
| skill | `write-formula-info` | FormulaInfo 記法の書き方（locality: repo） | — | — |
| skill | `choose-backend` | バックエンド選択の判断基準（locality: repo） | — | — |

## 組み合わせ例

1. **municipality-stats → tinyexpr__evaluate**: 統計データ（人口・世帯数）を変数に流し込み、スコアリング式で評価する
2. **building-starts + establishment-census → tinyexpr__execute_batch**: 建設着工数と事業所数を変数に投入し、依存関係付きの複数式で地域指標を算出する
3. **tinyexpr__validate → tinyexpr__evaluate**: ユーザーが入力した式の構文エラーを先に検出し、問題なければ評価する（安全なワンストップ体験）

## 依存と協調

| 相手 repo | 方向 | 能力 | 現状 | 備考 |
|-----------|------|------|------|------|
| unlaxer-common | depends_on | Parser combinator ランタイム | exists | Maven 依存。MCP 化に影響なし（JAR 同梱） |
| unlaxer-dsl | depends_on | UBNF 文法からのパーサー自動生成 | exists | Maven 依存。MCP 化に影響なし（JAR 同梱） |
| tinyexpression-ide | depends_on | Web ベース IDE（LSP over WebSocket） | exists | カタログ上 retired。MCP サーバ化で評価能力を独立提供可能 |
| municipality-stats | provides_to | tinyexpr__evaluate の変数入力として統計データを供給 | exists | 相手は library（MCP なし）。将来サーバ化で直接連携可能 |
| building-starts | provides_to | tinyexpr__execute_batch の変数入力として建設着工数を供給 | exists | 相手は library（MCP なし）。将来サーバ化で直接連携可能 |

協調が要るものは今のところない（相手側に MCP 入口がないため）。Phase 2 で相手リポジトリのサーバ化が進めば issue-hub で協調する。

## ライブラリのサーバ化

該当する。新規に実装が必要なもの:

| 項目 | 説明 |
|------|------|
| healthz | `/healthz` エンドポイント（200 を返す） |
| PORT | 環境変数でポート指定、`0.0.0.0` に bind |
| volta.service.json | manifest を root に配置 |
| MCP サーバ | Streamable HTTP の `/mcp` エンドポイント、tool 定義（evaluate/validate/execute_batch/parity_check） |
| systemd unit | 常駐起動設定 |
| Java ランタイム | Java 21+ が必要、fat JAR で配布（LSP サーバの shade プラグイン手法を流用可能） |

**runtime**: java | **estimated_effort**: M

## リスク

- **Java コードブロック**: JVM 上で任意コードを実行する（セキュリティリスク）。MCP tool ではデフォルト無効化し、明示的な opt-in フラグでのみ有効にする設計が必要。
- **JAVA_CODE バックエンドの起動オーバーヘッド**: 初回コンパイル時に `javac` を呼ぶ。常駐サーバ化でキャッシュが効くメリットがある一方、メモリ使用量に注意。
- **external Java メソッド呼び出し**: ホストアプリケーションのクラスパスに依存するため、MCP サーバ化時には制約が大きい。サーバ側で事前登録可能なメソッドセットを限定する必要がある。
- **P4 文法のカバレッジ**: 128 機能中 68 が PARITY。未カバー構文はフォールバックパスを使用するため、パリティチェック tool の結果解釈に注意。
- **Java 21+ が必要**: volta の既存 Java サービス（building-hierarchy, nanori-engine 等）と同等のランタイム要件。

## 持ち主への質問

1. MCP tool の `evaluate` で external Java メソッド呼び出しを許可するか？ 許可する場合の安全策（ホワイトリスト・サンドボックス）は？
2. Java コードブロックを MCP 経由で有効にするか？ デフォルトは無効が妥当だが、持ち主の意向を確認したい。
3. namespace は `tinyexpr` でよいか、それとも `tinyexpression` のままがよいか（カタログ上の既存 ID は `tinyexpression`）。
4. `execute_batch` tool で FormulaInfo.txt ファイルを直接受け取る形式と、JSON 配列で受け取る形式のどちらを優先するか。
5. 既存の LSP サーバ（`tinyexpression-p4-lsp-server.jar`）の fat JAR に MCP エンドポイントを追加する形で実装するか、独立した MCP サーバ JAR を新設するか。
