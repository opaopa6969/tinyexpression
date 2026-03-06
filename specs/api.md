# 公開 API 仕様

> ステータス: draft
> 最終更新: 2026-03-01

## スコープ

このドキュメントは TinyExpression の公開 API 仕様を定義する。CalculationContext、Calculator、JavaCodeCalculatorV3、TinyExpressionsExecutor、ResultConsumer のインタフェースを含む。

このドキュメントが **扱わない** 範囲:
- 内部実装の詳細（パーサー、コード生成器）
- FormulaInfo ファイル形式（→ [formula-info.md](formula-info.md)）

## 関連ドキュメント

- [formula-info.md](formula-info.md) — FormulaInfo 仕様
- [backends.md](backends.md) — バックエンド選択契約

---

## CalculationContext

**クラス**: `org.unlaxer.tinyexpression.CalculationContext`

### 概要

式の評価に使用されるコンテキスト。変数の値を保持し、式内の `$variable` 参照を解決する。

### ファクトリメソッド

| メソッド | 説明 |
|---------|------|
| `CalculationContext.newConcurrentContext()` | スレッドセーフなコンテキストを生成 |

### スレッドセーフティ

- `newConcurrentContext()` で生成されたコンテキストはスレッドセーフ（MUST）
- 複数スレッドから同時にアクセス可能

### 主要メソッド

| メソッド | 説明 |
|---------|------|
| `set(String name, Object value)` | 変数に値を設定 |
| `set(Object instance)` | 外部 Java オブジェクトを登録（クラス名ベースで参照） |
| `setObject(String name, Object value)` | Object 型として値を設定 |
| `get(String name)` | 変数の値を取得 |

---

## Calculator

**インタフェース**: `org.unlaxer.tinyexpression.Calculator`

### 概要

式の評価を行うインタフェース。すべてのバックエンドの Calculator 実装がこのインタフェースを実装する。

### 主要メソッド

| メソッド | 説明 |
|---------|------|
| `apply(CalculationContext)` | コンテキストで式を評価し、結果を返す |
| `dependsOnByNestLevel()` | 依存ネストレベルを返す（実行順序の決定に使用） |

---

## PreConstructedCalculator

**クラス**: `org.unlaxer.tinyexpression.PreConstructedCalculator`

### 概要

事前構築済みの Calculator。式のコンパイルをコンストラクタ時に行う。

---

## JavaCodeCalculatorV3

**クラス**: `org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3`

### 概要

`JAVA_CODE` バックエンドの主要な Calculator 実装。

### コンストラクタ

```java
new JavaCodeCalculatorV3(
    Source source,              // 式テキスト
    String className,           // 生成クラス名
    SpecifiedExpressionTypes types,  // 型指定
    ClassLoader classLoader     // クラスローダー
)
```

### 使用例

```java
CalculationContext context = CalculationContext.newConcurrentContext();
context.set("age", 25);

PreConstructedCalculator calc = new JavaCodeCalculatorV3(
    new Source("if($age >= 20){100}else{0}"),
    "MyCalc",
    new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
    Thread.currentThread().getContextClassLoader()
);

float result = ((Number) calc.apply(context)).floatValue();
```

---

## TinyExpressionsExecutor

**クラス**: `org.unlaxer.tinyexpression.instances.TinyExpressionsExecutor`

### 概要

複数の Calculator を依存関係順に実行するエグゼキュータ。クラス名は複数形（`Expressions`）。

### execute メソッド

```java
List<CalculationResult> execute(
    TenantID tenantId,
    CalculationContext context,
    ResultConsumer resultConsumer,
    FileBaseTinyExpressionInstancesCache cache,
    Comparator<Calculator> sortOrder,
    Predicate<Calculator> filter,
    ClassLoader classLoader
)
```

| パラメータ | 説明 |
|-----------|------|
| `tenantId` | テナント ID |
| `context` | 計算コンテキスト |
| `resultConsumer` | 結果を受け取るコンシューマ |
| `cache` | FormulaInfo キャッシュ |
| `sortOrder` | Calculator の実行順序（通常: `dependsOnByNestLevel` 昇順） |
| `filter` | 実行対象の Calculator フィルタ |
| `classLoader` | クラスローダー |

---

## ResultConsumer

**インタフェース**: `org.unlaxer.tinyexpression.instances.ResultConsumer`

### 概要

Calculator の結果を受け取るコンシューマインタフェース。結果の型に応じたオーバーロードメソッドを提供する。

### メソッド

| メソッド | 結果型 |
|---------|--------|
| `accept(CalculationContext, Calculator, FormulaInfo, Number)` | 数値結果 |
| `accept(CalculationContext, Calculator, FormulaInfo, String)` | 文字列結果 |
| `accept(CalculationContext, Calculator, FormulaInfo, Boolean)` | 真偽値結果 |
| `accept(CalculationContext, Calculator, FormulaInfo, Object)` | オブジェクト結果 |

### 典型的な実装パターン

```java
ResultConsumer consumer = new ResultConsumer() {
    @Override
    public void accept(CalculationContext c, Calculator calc, FormulaInfo info, Number result) {
        info.getValue("var").ifPresent(name -> c.set(name, result));
    }
    // ... 他の型も同様
};
```

---

## FileBaseTinyExpressionInstancesCache

**クラス**: `org.unlaxer.tinyexpression.instances.FileBaseTinyExpressionInstancesCache`

### 概要

ファイルシステムベースの FormulaInfo キャッシュ。テナントごとの `formulaInfo.txt` をロードしてキャッシュする。

### コンストラクタ

```java
new FileBaseTinyExpressionInstancesCache(
    Path rootDir,                          // ルートディレクトリ
    FormulaInfoAdditionalFields fields     // 追加フィールド設定
)
```

---

## CalculationResult

**クラス**: `org.unlaxer.tinyexpression.instances.CalculationResult`

### 概要

式の計算結果を保持する。Calculator、FormulaInfo、および結果値への参照を含む。

---

## 現在の制限事項

- `CalculationContext` の実装詳細（内部データ構造）は公開 API には含まれない
- エラーハンドリングの標準化は進行中

## 変更履歴

- 2026-03-01: 初版作成
