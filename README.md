# TinyExpression

日本語 | [English](README.en.md)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression)

Java アプリケーションに組み込み可能な式評価エンジン（UDF スタイル）。

- ランタイムでの式評価
- 複数式の依存関係付き実行
- 6 つの実行バックエンド（JavaCode / AST / P4 系列）
- LSP / DAP サポート（VS Code 拡張）

**ドキュメント**: [getting-started](docs/getting-started.md) | [language-guide](docs/language-guide.md) | [backends](docs/backends.md) | [architecture](docs/architecture.md)

**IDE**: [tinyexpression-group/tinyexpression-ide](https://github.com/tinyexpression-group/tinyexpression-ide) — VS Code 拡張（LSP + DAP）

---

## 目次

- [要件](#要件)
- [Maven 依存](#maven-依存)
- [クイックスタート](#クイックスタート)
- [複数式実行](#複数式実行)
- [FormulaInfo 記法](#formulainfo-記法)
- [Java コードブロック（セキュリティ注意）](#java-コードブロックセキュリティ注意)
- [バックエンド設定](#バックエンド設定)
- [言語クイックリファレンス](#言語クイックリファレンス)
- [LSP / DAP](#lsp--dap)
- [開発](#開発)

---

## 要件

- Java 21+
- Maven 3.8+

テスト/ランタイムで反射アクセスを使うため `add-opens` が必要（[`pom.xml`](pom.xml) 設定済み）。

---

## Maven 依存

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>tinyExpression</artifactId>
  <version>1.4.10</version>
</dependency>
```

---

## クイックスタート

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
    context.set("gender", "male");

    String formula = "if($gender=='male'){500}else{1000}";
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        new Source(formula),
        "QuickStartCalculator",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    float v1 = ((Number) calculator.apply(context)).floatValue();
    context.set("gender", "female");
    float v2 = ((Number) calculator.apply(context)).floatValue();

    System.out.println(v1); // 500.0
    System.out.println(v2); // 1000.0
  }
}
```

---

## 複数式実行

`TinyExpressionsExecutor`（複数形）で依存関係付き複数式を実行します。

### ディレクトリ構成

```text
<root>/
  <tenant-id>/formulaInfo.txt
```

### formulaInfo.txt の例

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
backend:AST_EVALUATOR
resultType:float
formula:
$baseScore + 10
---END_OF_PART---
```

### 実行コード

```java
FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
    "siteId",
    info -> info.calculatorName);
fields.setExecutionBackend(ExecutionBackend.JAVA_CODE);

FileBaseTinyExpressionInstancesCache cache = new FileBaseTinyExpressionInstancesCache(
    Path.of("src", "main", "resources", "formula-root"),
    fields);

CalculationContext ctx = CalculationContext.newConcurrentContext();
ctx.set("age", 30);

TinyExpressionsExecutor executor = new TinyExpressionsExecutor();
List<CalculationResult> results = executor.execute(
    TenantID.create(69),
    ctx,
    resultConsumer,
    cache,
    Comparator.comparingInt(Calculator::dependsOnByNestLevel),
    calculator -> true,
    Thread.currentThread().getContextClassLoader());
```

詳細は [docs/getting-started.md](docs/getting-started.md) 参照。

---

## FormulaInfo 記法

各ブロックは `key:value` + `formula` 本文で構成し、`---END_OF_PART---` で区切ります。

| キー | 説明 |
|------|------|
| `calculatorName` | 式 ID |
| `dependsOn` | 依存式名（カンマ区切り） |
| `resultType` | 戻り値型（`string`, `boolean`, `float`, `double`, FQCN 等） |
| `numberType` | 数値演算の既定型 |
| `formula` | 式本文 |
| `executionBackend` / `backend` | バックエンド上書き |
| `var` | `CalculationContext` への書き戻し変数名 |
| `field` | ドメインオブジェクトフィールド名 |
| `checkKind` | スコアマップ等の出力キー |

---

## Java コードブロック（セキュリティ注意）

> **警告**: Java コードブロックは JVM 上で任意コードを実行します。信頼できないユーザーが式を投稿できる環境では **使用しないでください**。

`formula` フィールドに Java クラスを直接埋め込めます。

~~~text
formula:
```java:sample.v1.CheckDigits
package sample.v1;
import org.unlaxer.tinyexpression.CalculationContext;
public class CheckDigits {
  public boolean check(CalculationContext context, String target) {
    return target.matches("\\d+");
  }
}
```
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
~~~

詳細は [docs/language-guide.md#java-コードブロック](docs/language-guide.md) 参照。

---

## バックエンド設定

解決順序:

1. グローバル既定値: `FormulaInfoAdditionalFields.setExecutionBackend(...)` （初期値: `JAVA_CODE`）
2. 式ごとの上書き: `executionBackend` / `backend` キー
3. 実装割り当て: `CalculatorCreatorRegistry.forBackend(...)`

| バックエンド名 | 説明 |
|--------------|------|
| `JAVA_CODE` | 現行プロダクション JavaCode（推奨） |
| `JAVA_CODE_LEGACY_ASTCREATOR` | リファクタ前ベースライン（凍結） |
| `AST_EVALUATOR` | AST 走査実行 |
| `DSL_JAVA_CODE` | DSL JavaCode シーム（ハイブリッド） |
| `P4_AST_EVALUATOR` | UBNF 生成パーサー + AST 評価（PRIMARY）|
| `P4_DSL_JAVA_CODE` | UBNF 生成パーサー + DSL JavaCode |

DAP/ランタイムエイリアス: `token`, `ast`, `dsl-javacode`, `p4-ast`, `p4-dsl-javacode`

詳細は [docs/backends.md](docs/backends.md) 参照。

---

## 言語クイックリファレンス

```text
# 変数
$age  $name  $isMember

# 算術
1 + 2 * 3    (1 + 2) / 3

# 比較・論理
10 >= 3    10 == 3    10 != 3
true | false    true & false    not(false)

# 条件
if($age >= 20){100}else{0}

# match
match{
  $code == 'JP' -> 1,
  default -> 0
}

# 文字列
toUpperCase($name)    $msg.startsWith('hello')    $msg[0:3]

# 変数宣言
variable $gender as string set if not exists 'male' description='性別';

# 外部メソッド
import sample.v1.Checker#check as check;
if(external returning as boolean check($input)){1}else{0}
```

完全仕様は [docs/language-guide.md](docs/language-guide.md) 参照。

---

## LSP / DAP

VS Code 拡張 [tinyexpression-p4-lsp-vscode](tools/tinyexpression-p4-lsp-vscode/README.md) が提供:

- シンタックスハイライト・セマンティックトークン
- 診断（TE001 パースエラー）
- 補完・ホバー
- DAP デバッグ（6 バックエンドのパリティ比較）

外部リポジトリ: [tinyexpression-group/tinyexpression-ide](https://github.com/tinyexpression-group/tinyexpression-ide)

---

## 開発

```bash
mvn -q test
```

ドキュメント一覧: [docs/INDEX.ja.md](docs/INDEX.ja.md)
