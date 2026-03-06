# 実行パイプライン仕様

> ステータス: draft
> 最終更新: 2026-03-01

## スコープ

このドキュメントは各バックエンドの E2E（エンドツーエンド）実行パイプラインを定義する。

このドキュメントが **扱わない** 範囲:
- バックエンドの選択契約（→ [backends.md](backends.md)）

## 関連ドキュメント

- [backends.md](backends.md) — バックエンド一覧と選択契約
- [type-system.md](type-system.md) — 型解決

---

## JAVA_CODE パイプライン

```
式テキスト
  ↓ パース（レガシーパーサー）
Token 木
  ↓ 型解決
型付き Token 木
  ↓ 演算子/オペランド木構築
演算子木
  ↓ Java コード生成
Java ソーステキスト
  ↓ ランタイムコンパイル
Java クラス
  ↓ 実行
結果値
```

### 主要クラス

- `JavaCodeCalculatorV3`: メインの Calculator 実装
- レガシーパーサー群（`org.unlaxer.tinyexpression.parser`）

---

## JAVA_CODE_LEGACY_ASTCREATOR パイプライン

```
式テキスト
  ↓ パース（レガシーパーサー）
Token 木
  ↓ OOTC（旧演算子/オペランド木構築）
レガシー演算子木
  ↓ Java コード生成
Java ソーステキスト
  ↓ ランタイムコンパイル
Java クラス
  ↓ 実行
結果値
```

### 主要クラス

- `LegacyAstCreatorJavaCodeCalculator`
- `LegacyOperatorOperandTreeCreator`

---

## AST_EVALUATOR パイプライン

```
式テキスト
  ↓ パース（レガシーパーサー）
Token 木
  ↓ 生成 AST 構築
AST
  ↓ AST 評価（走査実行）
結果値
  (フォールバック: Token 木 → javacode パス)
```

### ランタイムチェーン

`generated-ast → token-ast → javacode-fallback`

### 主要クラス

- `AstEvaluatorCalculator`

---

## DSL_JAVA_CODE パイプライン

```
式テキスト
  ↓ パース（レガシーパーサー）
Token 木
  ↓ ネイティブ DSL Java エミッタ（対応構文の場合）
Java ソーステキスト
  ↓ ランタイムコンパイル → 実行
結果値

※ 非対応構文の場合:
Token 木
  ↓ レガシー JavaCode ブリッジ
Java ソーステキスト
  ↓ ランタイムコンパイル → 実行
結果値
```

### ハイブリッド動作

- 対応構文: ネイティブ DSL Java エミッタを使用（`dsl-javacode-native`）
- 非対応構文: レガシー JavaCode ブリッジにフォールバック（`legacy-javacode-bridge`）

### 主要クラス

- `DslJavaCodeCalculator`

---

## P4_AST_EVALUATOR パイプライン

```
式テキスト
  ↓ パース（P4 UBNF 生成パーサー）
型安全 Token 木
  ↓ マッピング（P4 生成マッパー）
型安全 AST
  ↓ AST 評価
結果値
  (フォールバック: レガシーパーサー → AST_EVALUATOR チェーン)
```

### 主要クラス

- `P4AstEvaluatorCalculator`
- P4 生成パーサー/AST/マッパー（`org.unlaxer.tinyexpression.generated.p4`）

---

## P4_DSL_JAVA_CODE パイプライン

```
式テキスト
  ↓ パース（P4 UBNF 生成パーサー）
型安全 Token 木
  ↓ DSL_JAVA_CODE チェーン
Java ソーステキスト
  ↓ ランタイムコンパイル → 実行
結果値
  (フォールバック: レガシーパーサー → DSL_JAVA_CODE チェーン)
```

### 主要クラス

- `P4DslJavaCodeCalculator`

---

## 共通: ランタイムコンパイル

Java コード生成系バックエンド（`JAVA_CODE`, `JAVA_CODE_LEGACY_ASTCREATOR`, `DSL_JAVA_CODE`, `P4_DSL_JAVA_CODE`）は以下の共通フローを持つ:

1. 式から Java ソースコードを生成
2. `javax.tools.JavaCompiler` でランタイムコンパイル
3. コンパイル済みクラスをロード
4. `apply(CalculationContext)` を呼び出して結果を取得

---

## フォールバック

### AST_EVALUATOR のフォールバック

生成 AST で処理できない構文の場合:
1. Token 木ベースの AST 評価を試行
2. それも失敗した場合、JavaCode パスにフォールバック

### P4 バックエンドのフォールバック

P4 文法でカバーされていない構文の場合:
- 対応するレガシーバックエンド（AST_EVALUATOR または DSL_JAVA_CODE）にフォールバック

---

## 現在の制限事項

- ランタイムコンパイルは `javax.tools.JavaCompiler` の可用性に依存
- フォールバック時のパフォーマンスオーバーヘッドは未最適化
- P4 バックエンドのカバレッジは段階的に拡張中

## 変更履歴

- 2026-03-01: 初版作成
