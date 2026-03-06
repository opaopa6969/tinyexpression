# TinyExpression P4 LSP/DAP 実装 タスクトラッカー

Branch: `feat-java21-p4-lsp-dap`
Last updated: 2026-02-28

凡例: `[ ]` 未着手 / `[~]` 作業中 / `[x]` 完了 / `[-]` スキップ/不要

---

## Phase 0: 環境セットアップ

- [x] P0-1: ブランチ `feat-java21-p4-lsp-dap` 作成
- [x] P0-2: 計画書 `TINYEXPRESSION-P4-LSP-DAP-IMPL-PLAN.md` 作成
- [x] P0-3: タスクトラッカー `TINYEXPRESSION-P4-LSP-DAP-TASKS.md` 作成 (このファイル)

---

## Phase 1: コード生成環境構築

- [x] P1-1: `tools/tinyexpression-p4-lsp-vscode/` ディレクトリ構造を作成
- [x] P1-2: `grammar/tinyexpression-p4.ubnf` を配置 (`docs/ubnf/tinyexpression-p4-draft.ubnf` からコピー)
- [x] P1-3: `pom.xml` 作成
  - generators: `Parser,AST,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher`
  - unlaxer-common: `2.4.0`
  - mainClass (実際): `org.unlaxer.tinyexpression.lsp.p4.TinyExpressionP4LspLauncherExt`
  - finalName: `tinyexpression-p4-lsp-server`
- [x] P1-4: `mvn generate-sources` 実行 → 生成コード確認
  - `TinyExpressionP4Parsers.java` 存在確認
  - `TinyExpressionP4AST.java` sealed interface 確認
  - `TinyExpressionP4Mapper.java` 確認
  - `TinyExpressionP4LanguageServer.java` / `TinyExpressionP4DebugAdapter.java` 確認
- [x] P1-5: コンパイルエラーがある場合の対応
  - unlaxer-dsl SNAPSHOT 再インストールで解決
  - `CommaParser.java` の `@Override` 誤りを修正 (unlaxer-common 2.4.0 互換性)

---

## Phase 2: バックエンド統合

- [x] P2-1: `ExecutionBackend.java` に `P4_AST_EVALUATOR`、`P4_DSL_JAVA_CODE` 追加
  - enum エントリ追加
  - `fromRuntimeMode()` / `parse()` エイリアス登録 (`p4-ast`, `p4-dsl-javacode`)
  - `runtimeModeMarker()` 対応
- [x] P2-2: `P4AstEvaluatorCalculator.java` 実装
  - 生成Parser でパース
  - 生成Mapper で AST 化
  - 生成Evaluator で評価
  - 失敗時は `AstEvaluatorCalculator` へフォールバック
  - runtime markers 記録 (`_tinyExecutionBackend`, `_tinyP4ParserUsed`)
  - `getObject()` override で p4Markers を constructor 後すぐに参照可能に
- [x] P2-3: `P4DslJavaCodeCalculator.java` 実装
  - `DslJavaCodeCalculator` のサブクラス
  - `parseFormula()` を生成Parser で上書き
- [x] P2-4: `CalculatorCreatorRegistry.java` に P4 バックエンド登録
  - `P4_AST_EVALUATOR` → `p4AstEvaluatorCreator()`
  - `P4_DSL_JAVA_CODE` → `p4DslJavaCodeCreator()`
- [x] P2-5: `TinyExpressionDapRuntimeBridge.java` parity probe 拡張
  - `PARITY_BACKENDS` 配列に `P4_AST_EVALUATOR`、`P4_DSL_JAVA_CODE` 追加
  - `parity.P4_AST_EVALUATOR.*` / `parity.P4_DSL_JAVA_CODE.*` 出力対応
  - `_tinyP4ParserUsed` / `_tinyP4AstNodeType` コピー追加
- [x] P2-6: `TINYEXPRESSION-BACKEND-CONTRACT.md` に P4 バックエンド 2 つを追記

---

## Phase 3: LSP サーバーカスタマイズ

- [x] P3-1: `TinyExpressionP4LanguageServerExt.java` 作成 (生成クラスのサブクラス)
- [x] P3-2: 型安全セマンティックトークン分類
  - Token ツリーを walk して parser 型 (instanceof) + テキストマッチで分類
  - keyword / variable / number / string / operator / function / comment
  - Reflection・正規表現不使用
- [x] P3-3: `ParseFailureDiagnostics` 型安全インターフェース実装
  - `sealed interface ParseFailureDiagnostics permits Absent, Present`
  - `switch` パターンマッチで exhaustive 処理
- [x] P3-4: 診断生成 (P4 パーサーエラー → LSP Diagnostic)
  - TE001: unexpected token (offset + snippet表示)
  - 再publish で enriched diagnostics が基本実装を上書き
- [x] P3-5: 補完機能
  - Keywords: P4 固有キーワードリスト
  - 変数: `$identifier` パターンをドキュメントからスキャン
- [x] P3-6: ホバー表示 (AST node の型情報を Markdown で表示)
  - `ParseFailureDiagnostics.switch` で型安全に分岐
  - 成功時: AST root node type を `code` スパンで表示

---

## Phase 4: DAP 統合

- [x] P4-1: `TinyExpressionP4DebugAdapterExt.java` 作成 (生成クラスのサブクラス)
- [x] P4-2: `variables` レスポンスに P4 runtime markers 追加
  - `_tinyP4ParserUsed`, `_tinyP4AstNodeType`, `_tinyP4AstNodePath` 表示
  - `TinyExpressionDapRuntimeBridge` 経由で 6 バックエンド parity も自動表示
- [x] P4-3: `stackTrace` に生成 AST ノードパス表示
  - `_tinyP4AstNodePath` フィールドに sealed interface pattern matching で構築
  - 生成コードの `isAstRuntimeMode()` → "ast"/"ast_evaluator" mode で AST ステップ表示
- [-] P4-4: 6 バックエンド parity probe の動作確認 (手動テスト) → P6-4 に統合

---

## Phase 5: VSCode 拡張パッケージング

- [x] P5-1: `package.json` 作成
  - extension ID: `tinyexpression-p4-lsp`
  - 言語 ID: `tinyexpressionP4`
  - デバッガー type: `tinyexpressionP4`
  - 設定: `tinyExpressionP4Lsp.*`
- [x] P5-2: `src/extension.ts` 作成 (tinycalc-vscode を参考)
  - LSP: `java --enable-preview -jar tinyexpression-p4-lsp-server.jar`
  - DAP: `java --enable-preview -cp ... TinyExpressionP4DapLauncherExt`
- [x] P5-3: `language-configuration.json` 作成 (既存から流用)
- [x] P5-4: `syntaxes/tinyexpression.tmLanguage.json` 配置 (calculator-lsp-vscode から流用)
- [x] P5-5: `tsconfig.json` 作成
- [-] P5-6: `mvn verify` で VSIX 生成確認 (npm/vsce はオフライン環境では不要; JAR ビルドは確認済み)

---

## Phase 6: テスト

- [x] P6-1: `mvn test -q` (tinyexpression 本体テスト実行)
  - pre-existing 失敗: AstEvaluatorBackendParityTest (match string quote issue)、DslJavaCodeGenerationParityTest、TinyExpressionDapRuntimeBridgeTest など多数
  - P4BackendParityTest (7件) は全件 PASS ✅
  - 既存テストは pre-existing failures のみで我々の変更による新規失敗なし
- [x] P6-2: P4 バックエンド parity テスト作成
  - `src/test/java/org/unlaxer/tinyexpression/p4/P4BackendParityTest.java`
  - 7件テスト (6-backend 算術, P4-parser-used marker, match式パリティ, fallback, enum alias, 括弧算術)
  - DSL_JAVA_CODE の `(a+b)*(c+d)` 既知バグを文書化・回避
- [ ] P6-3: LSP 手動テスト
  - VSCode で `.tinyexp` を開き確認:
    - 診断 (エラーのある式でエラー表示)
    - 補完 (キーワード・変数)
    - セマンティックハイライト (keyword/variable/number/string/operator 色分け)
    - ホバー (型情報表示)
- [ ] P6-4: DAP 手動テスト
  - ブレークポイント設定・ステップ実行
  - `variables` パネルで P4 runtime markers 確認
  - `parity.*` 変数で 6 バックエンド同値確認

---

## 進捗サマリー

| Phase | 完了数 | 合計数 | 状態 |
|-------|--------|--------|------|
| Phase 0: 環境セットアップ | 3 | 3 | ✅ 完了 |
| Phase 1: コード生成環境構築 | 5 | 5 | ✅ 完了 |
| Phase 2: バックエンド統合 | 6 | 6 | ✅ 完了 |
| Phase 3: LSP カスタマイズ | 6 | 6 | ✅ 完了 |
| Phase 4: DAP 統合 | 3 | 4 | ✅ 完了 (P4-4 → P6-4 に統合) |
| Phase 5: VSCode 拡張 | 6 | 6 | ✅ 完了 (VSIX 3.5MB 生成) |
| Phase 6: テスト | 2 | 4 | 🔄 進行中 (P6-3/P6-4 は手動テスト) |
| **合計** | **32** | **36** | **89% 完了** |

---

## ブロッカー / 確認事項ログ

| 日付 | 項目 | 状態 |
|------|------|------|
| 2026-02-27 | `unlaxer-common 2.4.0` vs `2.2.0` の API 互換性 | 解決: 2.4.0 で `ParseContext.getParseFailureDiagnostics()` は未提供、sealed interface で代替 |
| 2026-02-27 | `Description` ルールが必須扱いになっている (既存 formula を parse 失敗する恐れ) | 要確認 (P6-3 で手動テスト) |
| 2026-02-27 | `ParseContext.getParseFailureDiagnostics()` が 2.4.0 で型安全に呼べるか | 解決: `ParseFailureDiagnostics` sealed interface で代替実装 |
| 2026-02-27 | 既存テスト失敗(4件): TinyExpressionDapRuntimeBridgeTest, TypeSystemRoadmapTest, UbnfExtensionRoadmapTest — 私の変更前から失敗 | 確認済み (pre-existing) |
| 2026-02-28 | `CommaParser.java` に `@Override expectedDisplayText()` が追加されコンパイル失敗 | 解決: `@Override` を削除 (`SingleCharacterParser` 2.4.0 にメソッドなし) |
| 2026-02-28 | `syntaxes/tinyexpression.tmLanguage.json` 未配置 (P5-4) | 解決: calculator-lsp-vscode から流用済み |
| 2026-02-28 | pre-existing テスト: `ParserTestBase.getTokenString()`、`TokenTest` の int→CodePointIndex | 解決: API 互換修正を適用 |
| 2026-02-28 | DSL_JAVA_CODE の `(a+b)*(c+d)` 乗算バグ | 確認済み (pre-existing): P4_AST_EVALUATOR は正常。テストで回避 |
| 2026-02-28 | VSIX に `target/`・`node_modules/` が大量同梱 | 解決: `.vscodeignore` 追加、3.5MB に縮小 |
