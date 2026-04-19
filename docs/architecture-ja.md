# TinyExpression アーキテクチャ

[English version](architecture.md)

## 概要

TinyExpression は **ハイブリッドアーキテクチャ** を採用しています。手書きのレガシーパーサースタックと、自動生成された P4 DSL パーサースタックが共存し、6 つの実行バックエンドのいずれかに接続します。

```
式テキスト
    │
    ├─► レガシーパーサー (unlaxer-common コンビネータ)
    │       └─► ParseTree ──► AST ──► JAVA_CODE / JAVA_CODE_LEGACY / AST_EVALUATOR / DSL_JAVA_CODE
    │
    └─► P4 パーサー (UBNF 生成、型安全)
            └─► P4 ParseTree ──► P4 AST (sealed interface) ──► P4_AST_EVALUATOR / P4_DSL_JAVA_CODE
```

---

## パーサー層

### レガシーパーサー（unlaxer-common）

レガシーパーサーは `unlaxer-common` を基盤とした**パーサーコンビネータ**スタックです。

主要インタフェース:

| インタフェース / クラス | 役割 |
|-----------------------|------|
| `org.unlaxer.parser.Parser` | パーサーのルートインタフェース |
| `org.unlaxer.parser.combinator.BasicCombinator` | コンビネータプリミティブ（`seq`, `choice`, `zeroOrMore` 等） |
| `org.unlaxer.parser.AbstractParser` | 具象パーサーの基底クラス |
| `org.unlaxer.tinyexpression.parser.*` | TinyExpression 固有のパーサー群 |

パース結果はトークンツリー（`ParseTree`）ノードです。レガシースタックは**全言語機能**をカバーします。

### P4 パーサー（UBNF 生成）

P4 パーサーは `docs/ubnf/tinyexpression-p4-draft.ubnf` の UBNF 文法から unlaxer-dsl によって生成されます。

生成成果物:

| 成果物 | 役割 |
|--------|------|
| `TinyExpressionP4Parsers` | エントリーポイントとなる生成パーサー |
| P4 AST（sealed interface 階層） | 型安全な AST ノード |
| `TinyExpressionP4Mapper` | ParseTree → P4 AST マッパー |
| `P4TypedAstEvaluator` | 型安全 AST エバリュエータ（PRIMARY） |

P4 スタックは `instanceof` ベースのディスパッチを提供します。LSP/DAP での正規表現フォールバックはありません。

---

## AST 層

レガシースタックは `ASTCombinator` / `ASTNodeMapping` アノテーションでパースツリー構造を記述します。

P4 スタックは **sealed interface レコード** を出力します:

```java
sealed interface TinyExpressionNode permits IfExpr, MatchExpr, BinaryExpr, ... {}
record IfExpr(TinyExpressionNode condition, TinyExpressionNode then, TinyExpressionNode else_)
    implements TinyExpressionNode {}
```

これにより網羅的な `switch` 式が利用可能になり、実行時キャストエラーが排除されます。

---

## 6 つの実行バックエンド

| バックエンド | クラス | 戦略 |
|------------|-------|------|
| `JAVA_CODE` | `JavaCodeCalculatorV3` | パース → Java ソース生成 → `javac` → ロード → 呼び出し |
| `JAVA_CODE_LEGACY_ASTCREATOR` | `LegacyAstCreatorJavaCodeCalculator` | 上記と同様だがリファクタ前 AST クリエイターを使用（凍結） |
| `AST_EVALUATOR` | `AstEvaluatorCalculator` | パース → AST → ツリーウォーキングインタープリタ。フォールバックチェーン: `generated-ast → token-ast → javacode` |
| `DSL_JAVA_CODE` | `DslJavaCodeCalculator` | ハイブリッド: ネイティブ DSL Java エミッタ + レガシーブリッジフォールバック |
| `P4_AST_EVALUATOR` | `P4AstEvaluatorCalculator` | P4 パース → P4 AST → `P4TypedAstEvaluator`（PRIMARY） |
| `P4_DSL_JAVA_CODE` | `P4DslJavaCodeCalculator` | P4 パース → P4 AST → DSL Java エミッタ |

### フォールバックチェーン（AST_EVALUATOR）

```
P4TypedAstEvaluator（PRIMARY）
    │ 失敗（P4 文法のギャップ）
    ▼
GeneratedP4NumberAstEvaluator
    │ 失敗
    ▼
AstTokenTreeEvaluator（レガシー AST ウォーク）
    │ 失敗
    ▼
JavaCode フォールバック（JAVA_CODE パス）
```

### バックエンド登録

```
ExecutionBackend enum
    │
    ▼
CalculatorCreatorRegistry.forBackend(ExecutionBackend)
    │
    ▼
CalculatorCreator.create(FormulaInfo, ...)
    │
    ▼
具象 Calculator インスタンス
```

---

## 型システム

`ExpressionTypes` enum が式の型と Java の型を対応付けます:

```
_byte → _short → _int → _long → _float → _double
                                   ↑
                              number（エイリアス）
```

型昇格ルールは Java の拡大変換に従います。`SpecifiedExpressionTypes` が式評価型と結果型の両方をパイプライン全体に伝搬します。

型昇格ルールの根拠については [decisions/ADR-002-type-promotion.md](decisions/ADR-002-type-promotion.md) を参照。

---

## インメモリコンパイラ

`JAVA_CODE` 系バックエンドはインメモリ Java コンパイラパイプラインを使用します:

```
Java ソース文字列
    │
    ▼
javax.tools.JavaCompiler（プロセス内）
    │
    ▼
MemoryJavaFileManager → ByteArrayJavaFileObject
    │
    ▼
MemoryClassLoader → Class<Calculator>
    │
    ▼
Calculator.apply(CalculationContext)
```

主要クラス:

- `org.unlaxer.compiler.MemoryClassLoader`
- `org.unlaxer.compiler.MemoryJavaFileManager`
- `org.unlaxer.compiler.CompileContext`

---

## 複数式実行パイプライン

```
FormulaInfo ファイル（テナントごと）
    │
    ▼
FormulaInfoParser → List<FormulaInfo>
    │
    ▼
CalculatorCreatorRegistry → List<Calculator>
    │
    ▼
FileBaseTinyExpressionInstancesCache（TenantID ごとにキャッシュ）
    │
    ▼
TinyExpressionsExecutor
    ├── Calculator.dependsOnByNestLevel() でソート
    ├── Predicate<Calculator> でフィルタ
    └── 順序通りに実行 → ResultConsumer.accept(...)
```

---

## LSP / DAP 統合

P4 LSP サーバー（`tools/tinyexpression-p4-lsp-vscode`）は P4 スタックに接続します:

```
.tinyexp ファイル編集
    │
    ▼
TinyExpressionP4LanguageServerExt（LSP）
    ├── ParseFailureDiagnostics による診断
    ├── P4 AST instanceof によるセマンティックトークン
    └── P4 AST ノード型による補完 / ホバー

.tinyexp デバッグ（F5）
    │
    ▼
TinyExpressionP4DebugAdapterExt（DAP）
    ├── 全 6 バックエンドを実行
    └── デバッガに parity.* 変数を公開
```

外部 IDE リポジトリ: [tinyexpression-group/tinyexpression-ide](https://github.com/tinyexpression-group/tinyexpression-ide)

---

## 関連ドキュメント

- [backends.md](backends.md) — バックエンド比較表とフォールバックチェーン
- [language-guide.md](language-guide.md) — 言語仕様
- [decisions/ADR-001-p4-primary.md](decisions/ADR-001-p4-primary.md) — P4 PRIMARY 化の経緯
- [decisions/ADR-002-type-promotion.md](decisions/ADR-002-type-promotion.md) — 型昇格ルール
- [decisions/ADR-003-java-codeblock-safety.md](decisions/ADR-003-java-codeblock-safety.md) — Java コードブロックのセキュリティモデル
