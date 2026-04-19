# TinyExpression はじめ方

[English version](getting-started.md)

TinyExpression v1.4.10 を Java プロジェクトに組み込む手順を説明します。

---

## 前提条件

- Java 21+
- Maven 3.8+

---

## Maven 設定

`pom.xml` に依存関係を追加します:

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>tinyExpression</artifactId>
  <version>1.4.10</version>
</dependency>
```

テストや実行時に反射アクセスを使う場合は Surefire プラグインに `add-opens` を追加します:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <argLine>
      --add-opens java.base/java.lang=ALL-UNNAMED
      --add-opens java.base/java.util=ALL-UNNAMED
    </argLine>
  </configuration>
</plugin>
```

---

## パターン A: 単発式評価

式がコードと同梱されている場合や固定式の場合に使用します。

### ステップ 1: CalculationContext を作成

```java
CalculationContext context = CalculationContext.newConcurrentContext();
context.set("age", 25);
context.set("gender", "male");
```

`newConcurrentContext()` はスレッドセーフなコンテキストを返します。複数スレッドから安全に共有できます。

### ステップ 2: 式をコンパイル

```java
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
    new Source("if($age >= 20){100}else{0}"),
    "AgeCheckCalc",                          // 生成クラス名（有効な Java 識別子）
    new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
    Thread.currentThread().getContextClassLoader());
```

`JavaCodeCalculatorV3` はコンストラクタ時に式を Java バイトコードにコンパイルします。`calculator` インスタンスは構築後ステートレスなので、複数の `apply()` 呼び出しで再利用できます。

### ステップ 3: 評価

```java
float result = ((Number) calculator.apply(context)).floatValue();
System.out.println(result); // 100.0
```

---

## パターン B: TinyExpressionsExecutor による複数式実行

テナントごとのルール管理、業務担当者による式の更新、依存関係制御付きパイプラインに使用します。

### ステップ 1: 式ディレクトリを作成

```
src/main/resources/formula-root/
  69/formulaInfo.txt
```

### ステップ 2: formulaInfo.txt を記述

```text
tags:NORMAL
description:基本スコア
siteId:69
calculatorName:baseScore
var:baseScore
resultType:float
formula:
if($age >= 20){100}else{0}
---END_OF_PART---

tags:NORMAL
description:ボーナス
siteId:69
calculatorName:bonusScore
dependsOn:baseScore
var:finalScore
resultType:float
formula:
$baseScore + 10
---END_OF_PART---
```

`dependsOn:baseScore` により、`baseScore` が `bonusScore` より先に評価されることが保証されます。

### ステップ 3: FormulaInfoAdditionalFields を設定

```java
FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
    "siteId",                          // パーティションキーフィールド名
    info -> info.calculatorName);      // 名前抽出関数: 式を識別する方法

// グローバルデフォルトバックエンドを設定（省略可能、既定値は JAVA_CODE）
fields.setExecutionBackend(ExecutionBackend.JAVA_CODE);
```

### ステップ 4: キャッシュを構築

```java
FileBaseTinyExpressionInstancesCache cache = new FileBaseTinyExpressionInstancesCache(
    Path.of("src", "main", "resources", "formula-root"),
    fields);
```

キャッシュはテナントごとに遅延ロード・コンパイルします。

### ステップ 5: ResultConsumer を実装

`ResultConsumer` は各式の結果を受け取り、何をするかを決めます。

```java
ResultConsumer resultConsumer = new ResultConsumer() {
  @Override
  public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, Number result) {
    // "var" キーで指定された変数名にコンテキストへ書き込み
    info.getValue("var").ifPresent(name -> c.set(name, result));
  }

  @Override
  public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, String result) {
    info.getValue("var").ifPresent(name -> c.set(name, result));
  }

  @Override
  public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, Boolean result) {
    info.getValue("var").ifPresent(name -> c.set(name, result));
  }

  @Override
  public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, Object result) {
    info.getValue("var").ifPresent(name -> c.setObject(name, result));
  }
};
```

### ステップ 6: 実行

```java
CalculationContext ctx = CalculationContext.newConcurrentContext();
ctx.set("age", 30);

TinyExpressionsExecutor executor = new TinyExpressionsExecutor();
List<CalculationResult> results = executor.execute(
    TenantID.create(69),
    ctx,
    resultConsumer,
    cache,
    Comparator.comparingInt(Calculator::dependsOnByNestLevel), // 依存順
    calculator -> true,                                         // 全式を対象に
    Thread.currentThread().getContextClassLoader());

System.out.println("実行数: " + results.size()); // 2
System.out.println("finalScore: " + ctx.get("finalScore")); // 110.0
```

---

## バックエンドの選択

本番環境の推奨設定:

1. グローバルデフォルトを `JAVA_CODE` に設定（既定値のまま）
2. 必要な場合のみ式ごとに上書き（例: `backend:P4_AST_EVALUATOR`）
3. 本番切り替え前にパリティテストを実行

バックエンドの詳細な比較は [docs/backends.md](backends.md) を参照。

---

## 外部 Java メソッド

式内から Java メソッドを呼び出すには:

1. Java クラスにメソッドを実装

```java
package myapp;
import org.unlaxer.tinyexpression.CalculationContext;

public class RiskChecker {
  public boolean isHighRisk(CalculationContext context, String region) {
    return "HIGH_RISK".equals(region);
  }
}
```

2. 実行前にコンテキストへオブジェクトを登録

```java
context.set(new myapp.RiskChecker());
```

3. 式内で参照

```text
import myapp.RiskChecker#isHighRisk as isHighRisk;
if(external returning as boolean isHighRisk($region)){100}else{0}
```

---

## 次のステップ

- [言語ガイド](language-guide-ja.md) — 完全な言語仕様
- [バックエンド](backends.md) — 6 バックエンドの比較とフォールバックチェーン
- [アーキテクチャ](architecture-ja.md) — パーサー、AST、エバリュエータの内部構造
