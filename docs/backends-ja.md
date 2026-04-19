# TinyExpression バックエンド

[English version](backends.md)

TinyExpression v1.4.10 は 6 つの実行バックエンドを提供します。本ドキュメントでは各バックエンドの違い、選択契約、フォールバックチェーン、推奨使用法を説明します。

---

## バックエンド一覧

| バックエンド | クラス | ステータス | 戦略 |
|------------|-------|----------|------|
| `JAVA_CODE` | `JavaCodeCalculatorV3` | プロダクション | パース → Java 生成 → `javac` → ロード → 呼び出し |
| `JAVA_CODE_LEGACY_ASTCREATOR` | `LegacyAstCreatorJavaCodeCalculator` | 凍結（リファレンス） | 上記と同様だがリファクタ前 AST クリエイター使用 |
| `AST_EVALUATOR` | `AstEvaluatorCalculator` | プロダクション | パース → AST → ツリーウォーキングインタープリタ |
| `DSL_JAVA_CODE` | `DslJavaCodeCalculator` | 移行ターゲット | ハイブリッド: DSL ネイティブエミッタ + レガシーブリッジ |
| `P4_AST_EVALUATOR` | `P4AstEvaluatorCalculator` | PRIMARY（P4） | UBNF パーサー → P4 AST → 型安全エバリュエータ |
| `P4_DSL_JAVA_CODE` | `P4DslJavaCodeCalculator` | 移行ターゲット（P4） | UBNF パーサー → P4 AST → DSL Java エミッタ |

---

## バックエンド詳細

### JAVA_CODE

現行プロダクション JavaCode ベースライン。

- **クラス**: `JavaCodeCalculatorV3`
- **変更ポリシー**: JavaCode パスへの新機能追加の主要ターゲット
- **動作**: 式を Java ソースコードに変換 → `javax.tools.JavaCompiler` でインメモリコンパイル → `MemoryClassLoader` でクラスをロード → `CalculationContext` で呼び出し
- **利点**: コンパイル後の実行は最速。JIT 最適化の恩恵を受ける
- **欠点**: 初回呼び出し時にコンパイルオーバーヘッドあり。インメモリ `javac` が必要

### JAVA_CODE_LEGACY_ASTCREATOR

リファクタ前の比較ベースライン。

- **クラス**: `LegacyAstCreatorJavaCodeCalculator`, `LegacyOperatorOperandTreeCreator`
- **変更ポリシー**: **凍結** — 最小限の互換性パッチのみ許容
- **用途**: `JAVA_CODE` のリファクタが同一の結果を生成することを検証するリグレッションベースライン

### AST_EVALUATOR

3 段階フォールバックチェーン付きの AST 走査実行。

- **クラス**: `AstEvaluatorCalculator`
- **フォールバックチェーン**:
  ```
  P4TypedAstEvaluator（PRIMARY）
      │ P4 文法のギャップ
      ▼
  GeneratedP4NumberAstEvaluator
      │ 失敗
      ▼
  AstTokenTreeEvaluator（レガシー AST ウォーク）
      │ 失敗
      ▼
  JavaCode フォールバック（JAVA_CODE パス）
  ```
- **変更ポリシー**: 生成 AST カバレッジの拡張が主要ターゲット
- **利点**: コンパイルオーバーヘッドなし。軽量デプロイに適する
- **欠点**: ホットパスでは `JAVA_CODE` より若干遅い。`JAVA_CODE` へのフォールバックはレイテンシ増加

### DSL_JAVA_CODE

ハイブリッド DSL Java エミッタ。

- **クラス**: `DslJavaCodeCalculator`
- **変更ポリシー**: ネイティブ DSL Java エミッタのカバレッジ拡張の移行ターゲット
- **動作**: まずネイティブ DSL Java エミッタを試み、式がカバーされていない場合はレガシーブリッジにフォールバック
- **ランタイムマーカー**:
  - `_tinyDslJavaNativeEmitterUsed = true` → ネイティブパスがヒット
  - `_tinyExecutionImplementation = legacy-javacode-bridge` → フォールバック

### P4_AST_EVALUATOR（P4 の PRIMARY）

型安全な UBNF 生成パーサーと AST 評価。

- **クラス**: `P4AstEvaluatorCalculator`
- **変更ポリシー**: P4 文法カバレッジの拡張。LSP/DAP のリファレンス実装
- **動作**: UBNF 生成の `TinyExpressionP4Parsers` で sealed interface P4 AST を生成 → `P4TypedAstEvaluator` で評価
- **AST_EVALUATOR との違い**: LSP/DAP での正規表現なし。完全に `instanceof` ベースのディスパッチ。コンパイル時の網羅性
- **制限**: すべての言語機能をカバーしていない（P4 文法のギャップはレガシーにフォールバック）

### P4_DSL_JAVA_CODE

型安全な UBNF 生成パーサーと DSL Java コード生成。

- **クラス**: `P4DslJavaCodeCalculator`
- **変更ポリシー**: 完全生成 DSL エバリュエータへの移行ターゲット
- **動作**: P4 パーサー → P4 AST → DSL Java エミッタ

---

## 選択契約

### 解決順序

1. **グローバル既定値**: `FormulaInfoAdditionalFields.setExecutionBackend(...)` — 初期値は `JAVA_CODE`
2. **式ごとの上書き**: `FormulaInfo` の `executionBackend` または `backend` キー
3. **実装割り当て**: `CalculatorCreatorRegistry.forBackend(ExecutionBackend)`

### ランタイムエイリアス

| エイリアス | バックエンド |
|-----------|------------|
| `token` | `JAVA_CODE` |
| `legacy-astcreator`, `ootc` | `JAVA_CODE_LEGACY_ASTCREATOR` |
| `ast` | `AST_EVALUATOR` |
| `dsl-javacode` | `DSL_JAVA_CODE` |
| `p4-ast`, `p4-ast-evaluator` | `P4_AST_EVALUATOR` |
| `p4-dsl-javacode`, `p4-dsl-java-code` | `P4_DSL_JAVA_CODE` |

---

## パリティ契約

6 つすべてのバックエンドは同一の入力に対して等価な値を返す必要があります。

1. サポートコーパスに対して全 6 バックエンドが等価な値を返す（MUST）
2. `AST_EVALUATOR` はサポート済み式で `javacode-fallback` を回避する（MUST）
3. `P4_AST_EVALUATOR` と `P4_DSL_JAVA_CODE` は他の 4 バックエンドと一致する（MUST）
4. **既知の例外**: P4 文法でカバーされていない構文を使用する式はフォールバックパスを使用

### DAP パリティ変数

DAP デバッグモードで実行すると、以下の変数が公開されます:

| 変数 | 説明 |
|------|------|
| `parity.JAVA_CODE` | `JAVA_CODE` バックエンドの結果 |
| `parity.AST_EVALUATOR` | `AST_EVALUATOR` バックエンドの結果 |
| `parity.P4_AST_EVALUATOR` | `P4_AST_EVALUATOR` バックエンドの結果 |
| `parity.equalAll` | 全 6 バックエンドが一致する場合 `true` |

---

## ランタイムマーカー

すべてのバックエンドが実行後にこれらのコンテキストマーカーを設定します:

| マーカー | 説明 |
|---------|------|
| `_tinyExecutionBackend` | 使用されたバックエンド名 |
| `_tinyExecutionMode` | 実行モード |
| `_tinyExecutionImplementation` | 実装バリアント |
| `_tinyExecutionBridgeImplementation` | ブリッジ実装バリアント（DSL バックエンド） |

---

## ロールアウト戦略

### 推奨される本番アプローチ

1. グローバルデフォルトを `JAVA_CODE` のままにする
2. 特定の式を `P4_AST_EVALUATOR` でテストしてカバレッジを確認
3. 式ごとに上書き: `backend:P4_AST_EVALUATOR`
4. 完全移行前に DAP デバッグで `parity.equalAll` を確認
5. `JAVA_CODE_LEGACY_ASTCREATOR` は変更しない — 不変のリファレンスとして扱う

### バックエンド変更ガイドライン

1. 構文や実行時機能を追加する場合: まず `JAVA_CODE` と `AST_EVALUATOR` を更新
2. `JAVA_CODE_LEGACY_ASTCREATOR` の変更は最小限に
3. バックエンド名を再利用してはならない（MUST NOT）
4. バックエンド動作契約を変更する場合: このドキュメントとパリティテストを同時に更新する（MUST）

---

## 既知の制限事項

- P4 バックエンドはすべての言語機能をカバーしていない（段階的拡張中）
- `JAVA_CODE_LEGACY_ASTCREATOR` は凍結状態
- `BigDecimal` と `BigInteger` の式内演算サポートは限定的

---

## 関連ドキュメント

- [architecture-ja.md](architecture-ja.md) — バックエンドがパーサーおよび AST 層とどう接続するか
- [decisions/ADR-001-p4-primary.md](decisions/ADR-001-p4-primary.md) — P4 を PRIMARY に昇格した理由
