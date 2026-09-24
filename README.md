# TinyExpression

日本語 | [English](README.en.md)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression/badge.svg)](https://central.sonatype.com/artifact/org.unlaxer/tinyExpression)

Java アプリケーションに組み込み可能な式評価エンジン（UDF スタイル）。

- ランタイムでの式評価
- 複数式の依存関係付き実行
- 6 つの実行バックエンド（JavaCode / AST / P4 系列）
- LSP / DAP サポート（VS Code 拡張）

**ドキュメント**: [getting-started](docs/getting-started.md) | [language-guide](docs/language-guide.md) | [backends](docs/backends.md) | [architecture](docs/architecture.md) | [Rust parser frontend](rust/tinyexpression-rs/README.md)

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
- [P4 パーサエンジン（2.0.0〜）](#p4-パーサエンジン200)
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
  <version>2.0.0</version>
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
    Comparator.comparingInt(Calculator::dependsOnByNestLevel).reversed(),
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

### FormulaInfo の保存済みコードに関する信頼境界

`FormulaInfo` の `byteCode`、`hashByByteCode`、`javaCode` は、過去の出力との互換性や
監査のために文書へ含まれることがあります。ただし、これらは式と同じ編集可能な文書に
保存されるため、署名の代わりにはなりません。ローダーは `formula` を必須とし、読込時は
現在の実行ポリシーで式から Calculator を再構築します。保存済み bytecode は実行しません。

再構築にはコンパイルコストが掛かるため、評価ごとに FormulaInfo を読み直さず、生成した
Calculator を再利用してください。Java コードブロックを含む式には、引き続き以下の明示的な
opt-in が必要です。

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

## P4 パーサエンジン（2.0.0〜）

2.0.0 から、P4 文法を使うバックエンド（`AST_EVALUATOR` / `DSL_JAVA_CODE` / `P4_AST_EVALUATOR` /
`P4_DSL_JAVA_CODE`）の**既定パーサは ubnfc 生成パーサ**になった。同じ P4 文法
（`tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf`）から
[ubnfc](https://github.com/opaopa6969/ubnfc) が生成した依存ゼロの Java パーサを
`org.unlaxer.tinyexpression.p4.ubnfc.generated` に同梱している（pin は
`src/main/java/org/unlaxer/tinyexpression/p4/ubnfc/UBNFC_PIN`、再生成は
`scripts/regenerate-ubnfc-parser.sh`）。返す AST・`selectionMode`・span・失敗時の例外とメッセージは
旧経路と同一で、`UbnfcParityTest`（340 件以上）が固定している。

旧 combinator 経路は **`legacy`** として 2.x の間だけ選べる（**3.0 で削除予定**）。

| 指定方法 | 例 | 優先 |
|---|---|---|
| FormulaInfo ブロックのフィールド | `p4Engine:legacy` | 1（最優先） |
| `CalculatorCreatorRegistry.forBackend(backend, engine)` / `P4ParserEngine.with(engine, ...)` | `P4ParserEngine.LEGACY` | 1 |
| システムプロパティ（JVM 全体の非常口） | `-Dtinyexpression.p4.engine=legacy` | 2 |
| 既定 | `ubnfc` | 3 |

値は `ubnfc` / `legacy`（大文字小文字は無視）。それ以外はエラーになる（黙って既定に戻さない）。
構築した Calculator は `_tinyP4ParserEngine` マーカーに使ったエンジンを持つ。
`tinyexpression.p4.memoize` は `legacy` でだけ効く。`tinyexpression.p4.parse.timeout.millis` は
両方で効くが、ubnfc は指数バックトラックを起こさない（packrat + 深さ上限）ので解析の前後でだけ見る。
LSP/DAP（`tools/tinyexpression-p4-lsp-vscode`）の構文診断は 2.0 では旧経路のまま。

性能（ubnfc facade 報告の実測、parse / Java 生成 / javac の合計）: fraud-alert 式 #5 は 2179 → 33 ms、
fraud-alert 5 式の `FormulaInfoList.parse` は 2490 → 364 ms（6.8 倍）。小さい式では javac が支配的で差は小さい。
詳細は [CHANGELOG](CHANGELOG.md) の 2.0.0 と [docs/reports/2026-09-24-ubnfc-default-engine.md](docs/reports/2026-09-24-ubnfc-default-engine.md)。

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
- リッチ診断（TE001〜TE025、カタログ連携、Quick Fix、構造化 `ULX-PARSE-001`）
- 宣言・import・method・`.tecatalog` を使う補完とホバー
- DAP デバッグ（6 バックエンドのパリティ比較）

DAP 0.2.33 は生成AST上の停止・ブレークポイント・実ランタイム評価に加え、
人向けの期待値付き構文診断と、LLM/editor向けの構造化診断dataに対応します。
`launch.json` の `variables` は `CalculationContext` に型付きで注入され、選択バックエンド、
6バックエンド比較、Debug Consoleで共通利用されます。停止中はVariablesビューから値を変更し、
同じコンテキストで再評価できます。詳細は
[VS Code拡張README](tools/tinyexpression-p4-lsp-vscode/README.md) を参照してください。

`formulaInfo.txt`も自動認識し、`calculatorName`で対象式を選択できます。既定の
`runtimeMode: metadata`は各ブロックの`executionBackend`に従い、依存式を先に実行して
Variablesビューへ式ごとの結果を表示します。埋め込みJavaは編集・色付けできますが、実行は
安全のため既定で無効です。

外部リポジトリ: [tinyexpression-group/tinyexpression-ide](https://github.com/tinyexpression-group/tinyexpression-ide)

---

## 開発

```bash
mvn -q test
```

CI は `test-baseline.txt` で既知の失敗を管理し、新規失敗で落とす。運用と更新手順は [docs/test-baseline.md](docs/test-baseline.md) 参照。

ドキュメント一覧: [docs/INDEX.ja.md](docs/INDEX.ja.md)

## エンジンの選び方

2.0.0 の既定は **ubnfc parser**、旧コンビネータ実行系は **unlaxer Classic**（`classic` モード、3.0 で削除）。使い分けの目安: [unlaxer-parser/docs/engine-selection-guide-ja.md](https://github.com/opaopa6969/unlaxer-parser/blob/master/docs/engine-selection-guide-ja.md)
