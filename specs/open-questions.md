# 未解決の設計疑問

> 最終更新: 2026-03-01

仕様書作成時に発見された設計上の疑問。各項目は確認後、仕様への反映または却下を行う。

---

## OQ-TINY-001: 権威的な形式文法の不在 — **調査完了**

**対象**: TinyExpression 言語全体

**結論**: P4 UBNF 文法は言語のコア構造をカバーしているが、レガシーパーサーとの差分が大きい。完全版 UBNF を `docs/ubnf/tinyexpression-p4-complete.ubnf` に作成した。

### 現在の UBNF カバレッジ

2つの P4 UBNF が存在する:
- `docs/ubnf/tinyexpression-p4-draft.ubnf`（239行、初期ドラフト）
- `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf`（321行、拡張版）

拡張版は draft に対して CodeBlock、ImportDeclaration、ExternalInvocation、StringComparison 等を追加済み。

### レガシーパーサーとの差分（P4 UBNF に欠けている機能）

| カテゴリ | 欠けている機能 | レガシーパーサー |
|---------|-------------|--------------|
| **論理演算子** | `&&`, `\|\|`, `^`, `not(...)` | `BooleanAndExpressionParser`, `BooleanOrExpressionParser`, `BooleanXorExpressionParser`, `NotBooleanExpressionParser` |
| **文字列演算** | `+`（結合）、スライス `[start:end:step]` | `StringPlusParser`, `SliceParser` |
| **文字列メソッド** | `.length()`, `.toUpperCase()`, `.toLowerCase()`, `.trim()`, `.indexOf()`, `.startsWith()`, `.endsWith()`, `.contains()`, `.in()` | 各種メソッドパーサー |
| **数学関数** | `sin`, `cos`, `tan`, `sqrt`, `min`, `max`, `random` | `SinParser`, `CosParser` 等 |
| **三項演算子** | `condition ? value1 : value2` | `TernaryOperatorParser` |
| **型変換** | `toNum(string, precision)` | `ToNumParser` |
| **null チェック** | `isPresent($var)` | `IsPresentParser` |
| **時間関数** | `inTimeRange()`, `inDayTimeRange()` | `InTimeRangeParser`, `InDayTimeRangeParser` |
| **副作用式** | `@side:Class.method(args)` | `SideEffectStringExpressionParser` |

### 方針

- `docs/ubnf/tinyexpression-p4-complete.ubnf` をレガシーパーサー全機能を網羅した完全版として作成済み
- P4 UBNF が全カバレッジに到達した時点で、それを権威的定義とする
- ツール系の整理完了後、`tools/` の UBNF を完全版で置き換える

### バックログ

1. 完全版 UBNF の正確性をレガシーパーサーのテストスイートで検証
2. ツール系整理後に `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` を完全版で置き換え
3. `tinyexpression-p4-draft.ubnf` を廃止（完全版で代替）

**反映先**: [language.md](language.md)、[p4-grammar.md](p4-grammar.md)

---

## OQ-TINY-002: バックエンド数の将来的整理 — **調査完了**

**対象**: 6つの実行バックエンド

**結論**: 6バックエンド共存は歴史的経緯による段階的リファクタリングの産物。各バックエンドには明確な役割分担がある。

### 現在の構成と役割

| バックエンド | 主クラス | 役割 | 変更ポリシー |
|------------|---------|------|------------|
| `JAVA_CODE` | `JavaCodeCalculatorV3` | 本番ベースライン | 機能追加の第一ターゲット |
| `JAVA_CODE_LEGACY_ASTCREATOR` | `LegacyAstCreatorJavaCodeCalculator` | リファクタ前ベースライン | **凍結**。最小限の互換性パッチのみ |
| `AST_EVALUATOR` | `AstEvaluatorCalculator` | DSL 代替ライン | 生成 AST 拡張のターゲット |
| `DSL_JAVA_CODE` | `DslJavaCodeCalculator` | DSL JavaCode シーム（ネイティブ+レガシーブリッジ混在） | ネイティブエミッタ拡充のターゲット |
| `P4_AST_EVALUATOR` | `P4AstEvaluatorCalculator` | P4 型安全パーサー + AST 評価 | P4 文法拡張。LSP/DAP のリファレンス |
| `P4_DSL_JAVA_CODE` | `P4DslJavaCodeCalculator` | P4 型安全パーサー + DSL Java 生成 | P4 文法拡張。生成 DSL へのブリッジ |

### バックエンド選択の仕組み

- `CalculatorCreatorRegistry.forBackend()` が `ExecutionBackend` enum に基づいてディスパッチ
- 選択優先順位: FormulaInfo メタデータ > 設定デフォルト（`JAVA_CODE`）
- ランタイムエイリアス: `token` → JAVA_CODE, `ast` → AST_EVALUATOR, `p4-ast` → P4_AST_EVALUATOR 等

### P4 バックエンドの特性

- P4 パース成功時: `_tinyP4ParserUsed=true` マーカーを設定し、型安全な AST で処理
- P4 パース失敗時: 非P4バックエンド（AST_EVALUATOR / DSL_JAVA_CODE）にグレースフルフォールバック
- P4 文法カバレッジ外の式（旧構文）は自動的にフォールバックで処理される

### パリティテスト基盤

- `P4BackendParityTest`: 6バックエンド全比較。P4 パース可否マーカーも検証
- `ThreeExecutionBackendParityTest`: 4バックエンド比較。サポートコーパス20件 + リグレッションコーパス30件以上
- `ThreeExecutionBackendExtractedCorpusParityTest`: 実プロダクションの式からの抽出テスト
- `DslJavaCodeGenerationParityTest`: JAVA_CODE と DSL_JAVA_CODE の生成コード等価性検証
- 既知の例外: ネストされた括弧の乗算で DSL_JAVA_CODE / P4_DSL_JAVA_CODE にバグあり

### 疑問への回答

1. **JAVA_CODE_LEGACY_ASTCREATOR の廃止タイムライン**: 明示的なタイムラインなし。凍結状態で比較ベースラインとして維持。P4 バックエンドが全カバレッジに到達し、パリティテストで完全等価が証明された時点で廃止可能
2. **P4 全カバレッジ後の非P4バックエンド**: `JAVA_CODE` は本番ベースラインとして当面残る（フォールバック先）。P4 カバレッジが100%に達した後、段階的に P4 バックエンドをデフォルトに切り替え可能
3. **最終ターゲット状態**: 明示的なビジョンは未定義だが、論理的な収束先は `P4_AST_EVALUATOR`（インタプリタ）+ `P4_DSL_JAVA_CODE`（コンパイラ）の2バックエンド。ただし段階的移行にはフォールバックパスが必要
4. **CI 影響**: パリティテストの6バックエンド比較は入力コーパスあたり6倍の実行だが、式の評価自体は軽量。テスト時間への影響は限定的

### 方針

- 現時点では6バックエンド共存を維持。パリティテストがリグレッション防止に有用
- P4 UBNF の完全版（OQ-TINY-001）が検証完了し、全機能がカバーされた時点で、`JAVA_CODE_LEGACY_ASTCREATOR` の廃止を検討
- 最終的な整理はオーナーの方針決定待ち

**影響**: [backends.md](backends.md)、[pipeline.md](pipeline.md)
