# TinyExpression

日本語 | [English](README.en.md)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression)

TinyExpression は、アプリケーションに組み込み可能な Java 製の式評価エンジンです。

- ランタイムでの式評価
- 複数式の依存関係付き実行
- JavaCode 生成系と AST 評価系の両対応

ロードマップ: [docs/TINYEXPRESSION-DSL-ROADMAP.md](docs/TINYEXPRESSION-DSL-ROADMAP.md)

## 現在の実行バックエンド (2026-02-26)

1. `JAVA_CODE` (現行の JavaCode 実装)
2. `JAVA_CODE_LEGACY_ASTCREATOR` (リファクタ前比較ベースライン)
3. `AST_EVALUATOR` (AST 走査実行)
4. `DSL_JAVA_CODE` (UnlaxerDSL JavaCode シーム)

詳細契約: [docs/TINYEXPRESSION-BACKEND-CONTRACT.md](docs/TINYEXPRESSION-BACKEND-CONTRACT.md)

## 要件

- Java 21+
- Maven 3.8+

注: テスト/ランタイムで反射アクセスを使うため add-opens が必要です（[`pom.xml`](pom.xml) 側設定済み）。

## Maven 依存

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>tinyExpression</artifactId>
  <version>1.4.10</version>
</dependency>
```

## クイックスタート (単一式)

```java
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class QuickStart {
  public static void main(String[] args) {
    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set("sex", "male");

    String formula = "if($sex=='male'){500}else{1000}";
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        new Source(formula),
        "QuickStartCalculator",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    float v1 = ((Number) calculator.apply(context)).floatValue();
    context.set("sex", "female");
    float v2 = ((Number) calculator.apply(context)).floatValue();

    System.out.println(v1); // 500.0
    System.out.println(v2); // 1000.0
  }
}
```

## 複数式実行 (`TinyExpressionsExecutor`)

クラス名は `TinyExpressionsExecutor` (複数形) です。

`TinyExpressionsExecutor` 自体は backend を選択しません。  
`FormulaInfo` 解析時に backend が解決された `Calculator` 群を実行します。

### 1. 配置構成

`FileBaseTinyExpressionInstancesCache` は次の構成を期待します。

```text
<root>/
  <tenant-id-1>/formulaInfo.txt
  <tenant-id-2>/formulaInfo.txt
```

### 2. `formulaInfo.txt` の最小例

```text
tags:NORMAL
description:base score
siteId:69
calculatorName:baseScore
var:baseScore
resultType:float
formula:
if($age >= 20){100}else{0}
---END_OF_PART---

tags:NORMAL
description:bonus score
siteId:69
calculatorName:bonusScore
dependsOn:baseScore
var:finalScore
backend:AST_EVALUATOR
resultType:float
formula:
$baseScore + 10
---END_OF_PART---
```

### 3. 実行例

```java
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.instances.CalculationResult;
import org.unlaxer.tinyexpression.instances.FileBaseTinyExpressionInstancesCache;
import org.unlaxer.tinyexpression.instances.ResultConsumer;
import org.unlaxer.tinyexpression.instances.TenantID;
import org.unlaxer.tinyexpression.instances.TinyExpressionsExecutor;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class ExecutorExample {
  public static void main(String[] args) {
    FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
        "siteId",
        info -> info.calculatorName);

    // 式側でbackend指定が無い場合のデフォルト
    fields.setExecutionBackend(ExecutionBackend.JAVA_CODE);

    FileBaseTinyExpressionInstancesCache cache = new FileBaseTinyExpressionInstancesCache(
        Path.of("src", "main", "resources", "formula-root"),
        fields);

    CalculationContext ctx = CalculationContext.newConcurrentContext();
    ctx.set("age", 30);

    ResultConsumer resultConsumer = new ResultConsumer() {
      @Override
      public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, Number result) {
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

    TinyExpressionsExecutor executor = new TinyExpressionsExecutor();
    List<CalculationResult> results = executor.execute(
        TenantID.create(69),
        ctx,
        resultConsumer,
        cache,
        Comparator.comparingInt(Calculator::dependsOnByNestLevel),
        calculator -> true,
        Thread.currentThread().getContextClassLoader());

    System.out.println("executed calculators: " + results.size());
  }
}
```

## `FormulaInfo` 記法

各ブロックは `key:value` 形式 + `formula` 本文で構成し、`---END_OF_PART---` で区切ります。

主要キー:

- `calculatorName`: 式ID
- `dependsOn`: 依存する式名（カンマ区切り）
- `resultType`: 戻り値型 (`string`, `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, FQCN)
- `numberType`: 数値演算のデフォルト型
- `formula`: 式本文
- `executionBackend` または `backend`: backend 上書き
- `tags`, `description`: 任意メタ情報
- `var`, `field`, `checkKind`: 実運用でよく使う追加キー（`ResultConsumer` で処理）

`formula` 内で Java コードブロックを埋め込むことも可能です。

~~~text
formula:
```java:sample.v1.CheckDigits
package sample.v1;
import org.unlaxer.tinyexpression.CalculationContext;
public class CheckDigits{
  public boolean check(CalculationContext context, String target){
    return target.matches("\\d+");
  }
}
```
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
~~~

## backend 設定

解決順序:

1. グローバル既定値: `FormulaInfoAdditionalFields.executionBackend`（初期値 `JAVA_CODE`）
2. 式ごとの上書き: `executionBackend` / `backend`
3. 実装割り当て: `CalculatorCreatorRegistry.forBackend(...)`

正式な backend 名:

- `JAVA_CODE`
- `JAVA_CODE_LEGACY_ASTCREATOR`
- `AST_EVALUATOR`
- `DSL_JAVA_CODE`

DAP/runtime alias (`runtimeMode`):

- `token` -> `JAVA_CODE`
- `legacy-astcreator` / `ootc` -> `JAVA_CODE_LEGACY_ASTCREATOR`
- `ast` -> `AST_EVALUATOR`
- `dsl-javacode` -> `DSL_JAVA_CODE`

関連コード:

- [src/main/java/org/unlaxer/tinyexpression/loader/FormulaInfoAdditionalFields.java](src/main/java/org/unlaxer/tinyexpression/loader/FormulaInfoAdditionalFields.java)
- [src/main/java/org/unlaxer/tinyexpression/loader/FormulaInfoParser.java](src/main/java/org/unlaxer/tinyexpression/loader/FormulaInfoParser.java)
- [src/main/java/org/unlaxer/tinyexpression/loader/model/CalculatorCreatorRegistry.java](src/main/java/org/unlaxer/tinyexpression/loader/model/CalculatorCreatorRegistry.java)
- [src/main/java/org/unlaxer/tinyexpression/runtime/ExecutionBackend.java](src/main/java/org/unlaxer/tinyexpression/runtime/ExecutionBackend.java)

## TinyExpression 言語クイックリファレンス

完全仕様ではなく、実装ベースの実用記法です。

### 値と変数

```text
123
3.14
'text'
"text"
true
false
$age
$name
```

### 数値/真偽演算

```text
1 + 2 * 3
(1 + 2) / 3
10 >= 3
10 == 3
10 != 3
true | false
true & false
true ^ false
not(false)
```

### 条件分岐

```text
if($age >= 20){100}else{0}

match{
  $countryCode == 'JP' -> 1,
  default -> 0
}
```

### 文字列ユーティリティ

```text
toUpperCase($name)
toLowerCase($name)
$message.startsWith('hello')
$message.endsWith('world')
$message.contains('abc')
$message[0:3]
```

### 変数宣言

```text
variable $sex as string set if not exists 'man' description='sex';
variable $age as number set 18 description='age';
variable $isMember as boolean description='member flag';
```

### 外部メソッド呼び出し

```text
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
```

実行時は `CalculationContext` に object を登録します。

```java
context.set(new sample.v1.CheckDigits());
```

### ユーザー定義メソッド (上級)

```text
float main(){
  match{
    $age < 18 -> 500,
    default -> call feeBySex($sex)
  }
}

float feeBySex($sex as string){
  match{
    $sex == 'woman' -> 1000,
    default -> 1800
  }
}
```

### コメント

- `FormulaInfo` メタデータ: `#` 行コメント
- 式本文: 空白と `/* ... */` コメントを許容

## ユーザーシステムへの組み込み指針

### パターンA: サービス内で単発評価

固定式やデプロイ同梱式向け。

1. リクエスト/ドメイン値から `CalculationContext` を作る
2. `JavaCodeCalculatorV3` などで式をコンパイル
3. `calculator.apply(context)` の結果を業務モデルへ反映

### パターンB: ルールリポジトリ + `TinyExpressionsExecutor`

テナントごとに式を運用する場合向け。

1. `formulaInfo.txt`（または同等データソース）で式を管理
2. `TinyExpressionInstancesCache` 実装で tenant 単位にキャッシュ
3. `TinyExpressionsExecutor` でまとめて実行
4. `ResultConsumer` で `var` / `field` / `checkKind` を処理
5. 外部関数用 object は `CalculationContext` に事前登録

### 式名の抽出戦略 (`FormulaInfoAdditionalFields`)

`calculatorName` の有無や運用規約に合わせて名前解決を外出しできます。

```java
FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
    "siteId",
    formulaInfo -> {
      String checkKind = formulaInfo.extraValueByKey.get("checkKind");
      return formulaInfo.calculatorName != null ? formulaInfo.calculatorName : checkKind;
    });
```

### 結果処理の外出し (`ResultConsumer`)

`TinyExpressionsExecutor` は結果処理を `ResultConsumer` に委譲します。

- `CalculationContext` への書き戻し
- ドメインオブジェクトのフィールド更新
- ログ/メトリクス/通知/キュー/DB 連携

### `resultType` と `numberType`

1. `resultType`: 式の最終戻り値型
2. `numberType`: 式中の数値リテラル/演算の既定型

大きな整数演算や型整合が必要なケースでこの2つを分けて使います。

## 追加ドキュメント

- backend contract: [docs/TINYEXPRESSION-BACKEND-CONTRACT.md](docs/TINYEXPRESSION-BACKEND-CONTRACT.md)
- UnlaxerDSL handbook: [docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md](docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md)
- migration guide: [docs/TINYEXPRESSION-UNLAXERDSL-MIGRATION-GUIDE.md](docs/TINYEXPRESSION-UNLAXERDSL-MIGRATION-GUIDE.md)
- DAP dual-evaluator plan: [docs/TINYEXPRESSION-DUAL-EVALUATOR-DAP-PLAN.md](docs/TINYEXPRESSION-DUAL-EVALUATOR-DAP-PLAN.md)
- final gap audit: [docs/TINYEXPRESSION-FINAL-GAP-AUDIT.md](docs/TINYEXPRESSION-FINAL-GAP-AUDIT.md)

## 開発

```bash
mvn -q test
```

履歴:

- [docs/TINYEXPRESSION-DSL-ROADMAP.md](docs/TINYEXPRESSION-DSL-ROADMAP.md)
- [docs/TINYEXPRESSION-DSL-HANDOVER-2026-02-20.md](docs/TINYEXPRESSION-DSL-HANDOVER-2026-02-20.md)
