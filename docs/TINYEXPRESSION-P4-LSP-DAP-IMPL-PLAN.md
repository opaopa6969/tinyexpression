# TinyExpression P4 LSP/DAP 型安全実装計画

Last updated: 2026-02-27
Branch: `feat-java21-p4-lsp-dap`

---

## 1. 概要

`docs/ubnf/tinyexpression-p4-draft.ubnf` を起点として、`unlaxer-dsl` の `CodegenMain` で
型安全な Parser / AST / Mapper / Evaluator / LSP / DAP を自動生成し、
TinyExpression の 5番目・6番目の実行バックエンドとして組み込む。

正規表現・Reflection を一切使わない LSP/DAP 実装。

### 追加するバックエンド（2つ）

| 識別子 | alias | 説明 |
|--------|-------|------|
| `P4_AST_EVALUATOR` | `p4-ast` | UBNF生成Parser + 生成AST Mapper + 生成Evaluator |
| `P4_DSL_JAVA_CODE` | `p4-dsl-javacode` | UBNF生成Parser + 既存 DslJavaCodeCalculator |

### 追加する LSP/DAP

- **LSP**: `TinyExpressionP4LanguageServer` (生成) + TinyExpression 固有拡張
- **DAP**: `TinyExpressionP4DebugAdapter` (生成) + parity probe 拡張
- **VSCode 拡張**: `tools/tinyexpression-p4-lsp-vscode/`

---

## 2. アーキテクチャ

```
docs/ubnf/tinyexpression-p4-draft.ubnf
    │
    ▼  mvn generate-sources (CodegenMain)
target/generated-sources/tinyexpression-p4/
    org/unlaxer/tinyexpression/generated/p4/
    ├── TinyExpressionP4Parsers.java        型安全パーサー群
    ├── TinyExpressionP4AST.java            sealed interface AST 定義
    ├── TinyExpressionP4Mapper.java         Token → AST 変換
    ├── TinyExpressionP4Evaluator.java      AST 評価器
    ├── TinyExpressionP4LanguageServer.java LSP サーバー (基本機能)
    ├── TinyExpressionP4LspLauncher.java    LSP 起動 main
    ├── TinyExpressionP4DebugAdapter.java   DAP アダプター
    └── TinyExpressionP4DapLauncher.java    DAP 起動 main
    │
    ▼  mvn package (shade)
tools/tinyexpression-p4-lsp-vscode/
    server-dist/tinyexpression-p4-lsp-server.jar   fat JAR
    out/extension.js                                TypeScript → JS
    │
    ▼  mvn verify (vsce package)
tinyexpression-p4-lsp-<version>.vsix
```

### バックエンド統合後の全体図

```
ExecutionBackend enum:
  JAVA_CODE              (既存)
  JAVA_CODE_LEGACY_ASTCREATOR (既存)
  AST_EVALUATOR          (既存)
  DSL_JAVA_CODE          (既存)
  P4_AST_EVALUATOR       ← 新規
  P4_DSL_JAVA_CODE       ← 新規
        │
        ▼
CalculatorCreatorRegistry → P4AstEvaluatorCalculator
                          → P4DslJavaCodeCalculator
        │
        ▼
TinyExpressionDapRuntimeBridge (parity.P4_AST_EVALUATOR / parity.P4_DSL_JAVA_CODE 追加)
```

---

## 3. 前提条件

| 条件 | 状態 |
|------|------|
| `unlaxer-dsl:0.1.0-SNAPSHOT` ローカルビルド済み | 確認要 |
| `unlaxer-common:2.4.0` Maven local repository | 確認要 |
| `tinyexpression-p4-draft.ubnf` validate-only OK | 確認済み (TINYEXPRESSION-P4-UBNF-EXTENSION-SPEC.md 参照) |
| Java 21 + `--enable-preview` | 既存環境と同じ |
| Node.js / npm / vsce | tinycalc-vscode と同じ環境 |

---

## 4. 実装フェーズ詳細

### Phase 0: 環境セットアップ [完了]

- ブランチ `feat-java21-p4-lsp-dap` 作成
- 計画書・タスクトラッカー作成

---

### Phase 1: コード生成環境構築

#### 1-1. ディレクトリ構造

```
tools/tinyexpression-p4-lsp-vscode/
├── grammar/
│   └── tinyexpression-p4.ubnf        (docs/ubnf/ からコピー)
├── src/
│   └── extension.ts                  (TypeScript VSCode クライアント)
├── syntaxes/
│   └── tinyexpression.tmLanguage.json (既存 calculator-lsp-vscode から流用)
├── package.json
├── tsconfig.json
├── language-configuration.json
├── server-dist/                      (ビルド後に fat JAR が配置される)
└── pom.xml
```

#### 1-2. pom.xml のポイント

- `--generators Parser,AST,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher`
- `--grammar grammar/tinyexpression-p4.ubnf`
- `unlaxer-common: 2.4.0` (tinyexpression 本体と揃える)
- mainClass: `org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4LspLauncher`
- finalName: `tinyexpression-p4-lsp-server`

#### 1-3. 生成後の確認ポイント

- `TinyExpressionP4Parsers.getRootParser()` が存在するか
- `TinyExpressionP4AST.java` に sealed interface が生成されているか
- `TinyExpressionP4Mapper.java` が各 rule をマッピングしているか
- `TinyExpressionP4LanguageServer.java` が `LanguageServer` を implements しているか
- `TinyExpressionP4DebugAdapter.java` が `IDebugProtocolServer` を implements しているか

---

### Phase 2: バックエンド統合

#### 2-A: ExecutionBackend enum 拡張

ファイル: `src/main/java/org/unlaxer/tinyexpression/runtime/ExecutionBackend.java`

```java
// 追加
P4_AST_EVALUATOR("p4-ast", false),
P4_DSL_JAVA_CODE("p4-dsl-javacode", true),
```

`fromRuntimeMode()` / `parse()` のエイリアスマッピングにも追加。
`runtimeModeMarker()` の実装を確認し、新バックエンドに対応させる。

#### 2-B: P4AstEvaluatorCalculator 実装

場所: `src/main/java/org/unlaxer/tinyexpression/evaluator/p4/P4AstEvaluatorCalculator.java`

```java
// 設計方針
class P4AstEvaluatorCalculator extends AbstractCalculator {
    // 1. TinyExpressionP4Parsers.getRootParser() でパース (型安全)
    // 2. TinyExpressionP4Mapper.map(token) で AST 化 (Reflection 不使用)
    // 3. TinyExpressionP4Evaluator.evaluate(ast) で評価
    // 4. 失敗時は AstEvaluatorCalculator へフォールバック
    // 5. runtime markers を setObject() で記録
    //    _tinyExecutionBackend = "P4_AST_EVALUATOR"
    //    _tinyP4ParserUsed = "true" / "false"
}
```

#### 2-C: P4DslJavaCodeCalculator 実装

場所: `src/main/java/org/unlaxer/tinyexpression/evaluator/p4/P4DslJavaCodeCalculator.java`

```java
// 設計方針: DslJavaCodeCalculator のサブクラス
class P4DslJavaCodeCalculator extends DslJavaCodeCalculator {
    @Override
    protected Parsed parseFormula(Source source) {
        // 手書きパーサーの代わりに生成パーサーを使用
        return TinyExpressionP4Parsers.getRootParser().parse(context);
    }
    // Java code generation は親クラス (DslJavaCodeCalculator) に委譲
}
```

#### 2-D: CalculatorCreatorRegistry 拡張

```java
case P4_AST_EVALUATOR   -> p4AstEvaluatorCreator();
case P4_DSL_JAVA_CODE   -> p4DslJavaCodeCreator();
```

#### 2-E: TinyExpressionDapRuntimeBridge 拡張

```java
// parity probe に P4 バックエンドを追加
private static final ExecutionBackend[] PARITY_BACKENDS = {
    JAVA_CODE, JAVA_CODE_LEGACY_ASTCREATOR,
    AST_EVALUATOR, DSL_JAVA_CODE,
    P4_AST_EVALUATOR, P4_DSL_JAVA_CODE   // 追加
};
```

---

### Phase 3: LSP サーバーカスタマイズ

生成コードはサブクラス化して拡張し、生成ファイル自体には触れない。

```java
// 新規: tools/.../server/src/main/java/.../TinyExpressionP4LanguageServerExt.java
class TinyExpressionP4LanguageServerExt extends TinyExpressionP4LanguageServer {
    // TinyExpression 固有の機能を型安全に追加
}
```

#### 追加機能

| 機能 | 実装方針 |
|------|---------|
| セマンティックトークン | `TinyExpressionP4AST` の node 種別 → LSP token type (Reflection 不使用) |
| 診断 (Diagnostics) | P4パーサーの失敗情報 → `ParseFailureDiagnostics` 型安全インターフェース |
| 補完 (Completion) | Keywords (AST から自動抽出) + 既存 `RuntimeCatalogProvider` 流用 |
| ホバー (Hover) | AST node の型情報を Markdown で表示 |
| 変数カタログ | 既存 `TinyExpressionVariableCatalog` を再利用 |

#### ParseFailureDiagnostics 型安全インターフェース

元の計画 (LSP/DAP 型安全リライト計画 Idea A) の sealed interface を採用。
P4 では `ParseContext.getParseFailureDiagnostics()` が 2.4.0 で利用可能かを確認し、
利用可能であれば Reflection を排除した実装にする。

```java
// tools/.../ParseFailureDiagnostics.java
public sealed interface ParseFailureDiagnostics
    permits ParseFailureDiagnostics.Null, ParseFailureDiagnostics.Typed {
    boolean hasFailureCandidate();
    int getFarthestOffset();
    List<String> getExpectedParsers();
    // ...
    record Null() implements ParseFailureDiagnostics { ... }
    record Typed(org.unlaxer.context.ParseFailureDiagnostics raw)
        implements ParseFailureDiagnostics { ... }
}
```

---

### Phase 4: DAP 統合

生成 `TinyExpressionP4DebugAdapter` のカスタマイズ:

- `variables` レスポンス: P4 runtime markers を含める
- `stackTrace`: 生成 AST のノードパスを表示
- `evaluate` コマンド: 6バックエンド全てで評価し parity を返す

---

### Phase 5: VSCode 拡張パッケージング

`package.json`:
- extension ID: `tinyexpression-p4-lsp`
- 言語 ID: `tinyexpressionP4` (既存 `tinyexpression` と共存)
- ファイル拡張子: `.tinyexp` (既存と共有または `.te4` で分離)
- デバッガー type: `tinyexpressionP4`
- 設定: `tinyExpressionP4Lsp.*`

`src/extension.ts`:
```typescript
// LSP
java --enable-preview -jar tinyexpression-p4-lsp-server.jar
// DAP
java --enable-preview -cp tinyexpression-p4-lsp-server.jar \
  org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4DapLauncher
```

---

### Phase 6: テスト

1. `mvn test -q` (tinyexpression 本体の既存テスト全パス)
2. P4 バックエンド parity テスト:
   - `src/test/java/org/unlaxer/tinyexpression/p4/P4BackendParityTest.java`
   - 6バックエンド同値確認 (既存 `TinyExpressionDapRuntimeBridgeTest` 拡張)
3. LSP 手動テスト: VSCode で `.tinyexp` を開き診断・補完・セマンティックハイライトを確認
4. DAP 手動テスト: ブレークポイント・変数表示・ステップ実行

---

## 5. ファイル変更一覧

### 新規作成

| ファイル | 説明 |
|---------|------|
| `tools/tinyexpression-p4-lsp-vscode/` | 新ツールディレクトリ全体 |
| `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` | UBNF 文法 |
| `tools/tinyexpression-p4-lsp-vscode/pom.xml` | Maven ビルド |
| `tools/tinyexpression-p4-lsp-vscode/package.json` | VSCode 拡張メタデータ |
| `tools/tinyexpression-p4-lsp-vscode/src/extension.ts` | TypeScript クライアント |
| `tools/tinyexpression-p4-lsp-vscode/language-configuration.json` | 言語設定 |
| `src/main/java/org/unlaxer/tinyexpression/evaluator/p4/P4AstEvaluatorCalculator.java` | P4 AST 評価器 |
| `src/main/java/org/unlaxer/tinyexpression/evaluator/p4/P4DslJavaCodeCalculator.java` | P4 DSL Java code 評価器 |
| `docs/TINYEXPRESSION-P4-LSP-DAP-IMPL-PLAN.md` | この計画書 |
| `docs/TINYEXPRESSION-P4-LSP-DAP-TASKS.md` | タスクトラッカー |

### 修正

| ファイル | 変更内容 |
|---------|---------|
| `src/main/java/org/unlaxer/tinyexpression/runtime/ExecutionBackend.java` | `P4_AST_EVALUATOR`, `P4_DSL_JAVA_CODE` 追加 |
| `src/main/java/org/unlaxer/tinyexpression/loader/model/CalculatorCreatorRegistry.java` | P4 バックエンドのファクトリ登録 |
| `src/main/java/org/unlaxer/tinyexpression/dap/TinyExpressionDapRuntimeBridge.java` | P4 parity probe 追加 |
| `docs/TINYEXPRESSION-BACKEND-CONTRACT.md` | P4 バックエンド 2 つを追記 |

---

## 6. 依存バージョン確認事項

| 依存 | tinyexpression 現在 | tinycalc-vscode 参考 | P4 で使用 |
|------|--------------------|--------------------|----------|
| unlaxer-common | 2.4.0 | 2.2.0 | **2.4.0** |
| unlaxer-dsl | - | 0.1.0-SNAPSHOT | 0.1.0-SNAPSHOT |
| lsp4j | 0.23.1 (server) | 0.23.1 | **0.23.1** |
| Java | 21 | 21 | **21** |

`unlaxer-common 2.4.0` と `2.2.0` の API 差異で生成コードがコンパイルエラーになる場合は、
`unlaxer-dsl` のジェネレーターが `2.2.0` を想定している可能性があるため、
`unlaxer-dsl` 側の対応を `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md` に記録する。

---

## 7. 既知の UBNF 文法 GAP

現在の `tinyexpression-p4-draft.ubnf` でカバーされていない TinyExpression 構文:

| 構文 | 優先度 | Phase 1 の方針 |
|------|--------|---------------|
| `import X as Y ;` | 中 | Phase 3 以降で UBNF 拡張 |
| `external call ...` / `internal call ...` | 中 | Phase 3 以降 |
| `not exists` 条件 | 低 | Phase 3 以降 |
| `returning` キーワード | 低 | Phase 3 以降 |
| `[TE001]` コードタグ | 低 | LSP 側で正規表現補助処理 |
| fenced Java ブロック (`[[[...]]]`) | 低 | P4 スコープ外 (DSL_JAVA_CODE 担当) |
| `Description` の省略可能性 | 確認要 | 現 UBNF は必須扱い |

→ Phase 1 では文法 GAP を Known Gaps として記録しつつ、コアサブセットで parity 確認を先行する。

---

## 8. リスク

| リスク | 対策 |
|--------|------|
| unlaxer-common 2.4.0 と 2.2.0 の API 差異で生成コードがコンパイルエラー | pom.xml で 2.4.0 を指定し、エラーが出れば unlaxer-dsl ジェネレーターを修正 |
| 生成コードの手動拡張が再生成で上書きされる | 生成クラスをサブクラス化し、生成ファイルには手を入れない |
| P4 パーサーと手書きパーサーのパリティ差 | UbnfExtensionRoadmapTest で確認、Known Gaps に記録 |
| Description ルールが必須のため既存 formula を parse 失敗する | UBNF で optional 化するか LSP 側で前処理 |

---

## 9. 検証コマンド

```bash
# unlaxer-dsl ローカルインストール
cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests install

# UBNF 文法バリデーション
cd /mnt/c/var/unlaxer-temp/unlaxer-dsl
mvn -q exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain \
  -Dexec.args="--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.ubnf --validate-only"

# P4 LSP ビルド (Phase 1 完了後)
cd /mnt/c/var/unlaxer-temp/tinyexpression/tools/tinyexpression-p4-lsp-vscode
mvn package -q

# tinyexpression 本体テスト
cd /mnt/c/var/unlaxer-temp/tinyexpression && mvn test -q
```

---

## 10. 関連ドキュメント

- `docs/ubnf/tinyexpression-p4-draft.ubnf` — UBNF 文法ドラフト
- `docs/TINYEXPRESSION-P4-UBNF-EXTENSION-SPEC.md` — P4 設計仕様
- `docs/TINYEXPRESSION-BACKEND-CONTRACT.md` — バックエンドパリティ契約
- `docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md` — UnlaxerDSL 実装ハンドブック
- `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md` — 依存拡張記録
- `/mnt/c/var/unlaxer-temp/unlaxer-dsl/tinycalc-vscode/` — 参考実装 (tinycalc)
