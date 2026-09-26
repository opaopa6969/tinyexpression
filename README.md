# TinyExpression

日本語 | [English](README.en.md)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression/badge.svg)](https://central.sonatype.com/artifact/org.unlaxer/tinyExpression)

Java アプリケーションに組み込み可能な式評価エンジン（UDF スタイル）。

- ランタイムでの式評価
- 複数式の依存関係付き実行
- 6 つの実行バックエンド（JavaCode / AST / P4 系列）
- LSP / DAP サポート（VS Code 拡張）
- **Playground**: <https://opaopa6969.github.io/tinyexpression/> — ブラウザだけで式を書き、CalculationContext を設定して評価（wasm、JVM 不要）

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
- [JVM 不要（Rust 版: CLI・埋め込み・wasm）](#jvm-不要rust-版-cli埋め込みwasm)
- [Playground と言語カタログ](#playground-と言語カタログ)
- [サーバ評価（EvalContextService）](#サーバ評価evalcontextservice)
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

旧 combinator 経路は **`classic`**（unlaxer Classic）として 2.x の間だけ選べる（**3.0 で削除予定**）。
`legacy` という id は **非推奨のエイリアス**として `classic` を指す（使うと一度だけ警告ログが出る）。
エンジン本体と一緒に 3.0 で削除される。

| 指定方法 | 例 | 優先 |
|---|---|---|
| FormulaInfo ブロックのフィールド | `p4Engine:classic` | 1（最優先） |
| `CalculatorCreatorRegistry.forBackend(backend, engine)` / `P4ParserEngine.with(engine, ...)` | `P4ParserEngine.CLASSIC` | 1 |
| システムプロパティ（JVM 全体の非常口） | `-Dtinyexpression.p4.engine=classic` | 2 |
| 既定 | `ubnfc` | 3 |

値は `ubnfc` / `classic`（大文字小文字は無視。非推奨エイリアス `legacy` も `classic` として解釈される）。
それ以外はエラーになる（黙って既定に戻さない）。
構築した Calculator は `_tinyP4ParserEngine` マーカーに使ったエンジンを持つ。
`tinyexpression.p4.memoize` は `classic` でだけ効く。`tinyexpression.p4.parse.timeout.millis` は
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

## JVM 不要（Rust 版: CLI・埋め込み・wasm）

[`rust/`](rust/README.md) に同じ P4 文法・同じ意味論の Rust 実装がある（Java との差分 golden で恒久検査、
issue #177）。version は Java 版と共通（2.0.0）。JVM を起動しないので、1 式だけ評価するプロセスの
起動〜初回評価が桁で速い（下表）。

**CLI**

```bash
cargo build --release --manifest-path rust/Cargo.toml -p tinyexpression-rs
printf '%s' '(1 + 2) * 3' | rust/target/release/tinyexpression eval -
rust/target/release/tinyexpression run formulaInfo.fi      # FormulaInfo を読み込んで全式を評価
rust/target/release/tinyexpression --help                   # parse/check/eval/load/run と exit code
```

**埋め込み**

- Rust: crate `tinyexpression-rs`（`tinyexpression_rs::runtime::Program` が Java の
  `P4_AST_EVALUATOR` と同じ意味論、変数・外部呼出しは host trait）。
- C / 他言語: `rust/tinyexpression-ffi`（`libtinyexpression.so` + `include/tinyexpression.h`）。
  `te_eval(src, len, &json)` のように UTF-8 を渡して CLI と同一の JSON を受け取る。安定面は JSON 契約。

**wasm**

`cargo build --profile release-small --target wasm32-unknown-unknown --manifest-path rust/Cargo.toml -p tinyexpression-ffi`
で import ゼロの `tinyexpression.wasm` ができる。ブラウザ demo と node 用 binding は
[`rust/examples/wasm/`](rust/examples/wasm/)。

**起動〜初回評価**（プロセス起動込み、FormulaInfo 1 式 `if(3 > 2){(1 + 2) * 3 + 4.5}else{0}` を load して 1 回評価、
hyperfine 15 回の中央値。2026-09-25、WSL2 32 core、他ジョブで load average 9〜20 の共有機なので絶対値は参考）

| 経路 | 中央値 | 最小 |
|---|---:|---:|
| Rust CLI `tinyexpression eval`（式のみ） | 2.9 ms | 2.2 ms |
| Rust CLI `tinyexpression run`（FormulaInfo load + 評価） | 3.5 ms | 1.6 ms |
| wasm（node 20 で 2 MB module を compile・instantiate + `run`。`node -e 0` 単体が約 105 ms） | 1104 ms | 926 ms |
| Java 21 `FormulaInfoList.parse` + `apply`、`P4_AST_EVALUATOR` | 3501 ms | 3120 ms |
| Java 21 同上、`JAVA_CODE`（bytecode 生成込み） | 5541 ms | 1309 ms |

Java は `java -jar` ではなく `target/classes` + 依存 jar の classpath で起動した（fat jar が無いため。起動コストは同等）。
計測 driver は FormulaInfo を読み、先頭の式の calculator を `CalculationContext.newContext()` で 1 回 apply するだけのもの。

配布（GitHub Release の CLI / C ライブラリ / wasm、crates.io）は [rust/README.md](rust/README.md#配布経路)。

## Playground と言語カタログ

**<https://opaopa6969.github.io/tinyexpression/>**（[`playground/`](playground/README.md)、GitHub Pages、issue #201）

- CodeMirror 6 のエディタで式を書き、`tinyexpression.wasm` で parse / check / 評価（JVM もサーバも不要）
- CalculationContext パネル: 変数（型・値）、`nowHour` / `nowDayOfWeek`、結果型・`numberType`、`external` のスタブ
- 結果（値と型）とエラー（TE コード・カタログの説明・修正のヒント・エラー位置）、FormulaInfo の読み込みと実行
- 補完・hover・診断文言は **言語カタログ** [`catalog/tinyexpression-catalog.json`](catalog/README.md) から。
  VSIX の LSP サーバも同じカタログを読む（設定 `tinyExpressionP4Lsp.catalog.overridePath` で差し替え・追加可）

- **評価トレース（Trace / Step）**: 部分式ごとの値と型を木で表示（クリックでエディタの範囲を強調）、評価順に
  1 ステップずつ進めて途中の値のスタックを見る。失敗したステップは TE コード / 例外名とカタログの修正のヒント付き。
  wasm / C ABI の `te_eval_trace`、CLI `tinyexpression eval --trace`（[rust/README.md](rust/README.md#評価-traceissue-201-段階-3)）
- **カタログ編集**: 説明（ja / en）・例・修正のヒントの編集、変数・関数・エラーコードと variant の追加、スキーマ検証、
  全体 JSON / override JSON / unified diff / JSON Patch の書き出し、GitHub への PR 作成（token はメモリだけ）。
  編集は playground の補完・hover・診断にすぐ反映
- **VSIX への同梱**: コマンド「TinyExpression: Open playground」が同じ build を webview で開き（アクティブな式と
  カタログを読み込む）、「TinyExpression: Import catalog from playground export」が書き出したカタログを
  `.vscode/tinyexpression-catalog.override.json` に置いて `catalog.overridePath` に設定する（ステータスバーに有効なカタログ）

評価経路は Java 差分 golden（#179）の全行と node で照合している（`playground/scripts/parity-smoke.mjs`、CI。
trace 付きの経路も同じ全行で一致を確認）。カタログの書き出しは `playground/scripts/catalog-roundtrip.mjs` が
「無編集で書き出すとバイト単位で同一・派生ファイルも同一」を CI で検査する。

---

## サーバ評価（EvalContextService）

playground の wasm は Java コードブロックを実行せず、`external` は仮の値（スタブ）で代用する。社内向けサーバ
などで **本物の Java 評価** を行うための共通部品が `org.unlaxer.tinyexpression.service.EvalContextService`
（issue #221）。Rust の `te_eval_context` / `te_eval_trace` / `te_formula_info_context`
（[rust/README.md](rust/README.md#calculationcontext-付き評価issue-201)）と **同じリクエスト/応答 JSON** を
Java（`P4_AST_EVALUATOR`）で処理する。入力 JSON 文字列 → 応答 JSON 文字列で、HTTP サーバには依存しない。
Java 17 版（`tinyExpression-jdk17`）にも入る。

```java
EvalContextService service = EvalContextService.builder()
    .codeBlockPolicy(CodeBlockExecutionPolicy.DENY)   // 既定。ALLOW / FOLLOW_GLOBAL / 独自の判定
    .timeout(Duration.ofSeconds(5))                   // 既定 5 秒。0 以下 = 無制限（呼び出しスレッドで実行）
    .auditHook(new EvalAuditHook() {                  // 既定は何もしない
      @Override public void afterEvaluation(String requestJson, Outcome outcome) {
        auditLog.info("playground eval {} -> {}", requestJson, outcome.response().json());
      }
    })
    .build();

String json = service.evalContext(requestJson);          // te_eval_context
String traced = service.evalTrace(requestJson);          // te_eval_trace（"trace":null、下記）
String run = service.formulaInfoContext(requestJson);    // te_formula_info_context
EvalContextResponse r = service.dispatch(requestJson);   // "operation": evalContext | evalTrace | runContext
```

規則:

- **external**: リクエストの `externals[]` のスタブだけで解決する（Rust と同じ。スタブに無いクラスは
  `Class.forName` 失敗）。ホストの classpath のクラスはリクエストから呼べない。
- **Java コードブロック**: 実行するかはホストが渡す `CodeBlockExecutionPolicy` が決める。既定は `DENY`
  （`JavaCodeBlockPolicy` と同じく secure by default）。不許可ならブロックはクラスを宣言するだけで、呼び出しは
  スタブで解決する（Rust / playground と同じ。スタブが無ければ案内付きのエラー）。許可なら各 ```` ```java ````
  ブロックをメモリ上でコンパイルして実行し、**同名のスタブより本物を優先** する（要 JDK）。
  `FOLLOW_GLOBAL` はリクエストごとに `JavaCodeBlockPolicy.isEnabled()` に従う。
  > **Warning**: Java code blocks compile and execute arbitrary code on the JVM. Only use this feature when
  > formula authors are fully trusted. Do not expose this capability to untrusted users.
  > （[ADR-003](docs/decisions/ADR-003-java-codeblock-safety.md)）
- **タイムアウト**: 1 リクエスト（コードブロックのコンパイル込み）の上限。超えたら
  `{"ok":false,"stage":"timeout","error":{"kind":"TimeoutException",...}}`。作業スレッドには割り込みを
  かけるが、割り込みを無視する計算は終わるまでスレッドを占有するので、**同時実行数はホスト側で絞る**。
- **監査 hook**: `beforeEvaluation(Event)`（評価前: 操作・リクエスト・式・コードブロックのクラス・実行可否）と
  `afterEvaluation(requestJson, Outcome)`（応答・所要時間・タイムアウト。不正リクエストでも呼ぶ）。
  hook の例外は呼び出し側に伝わる（監査できない評価は答えない）。
- 応答には Rust に無い `"evaluator":"java"` と、コードブロックがあれば `"codeBlocks":{"classes":[...],"executed":bool}` が付く。

Rust との契約一致: `src/test/resources/eval-context-contract/requests.tsv` の 64 リクエスト（算術・型・変数の各 map・
文字列・match・角度・`nowHour`・parse 失敗・評価失敗・スタブの各失敗・不許可のコードブロック・リクエスト誤り・trace・
FormulaInfo）で、Rust の応答（`rust-responses.tsv`、`cargo test --test eval_context_contract` が最新であることを検査）と
Java の応答の ok / stage / 終了コード / 値の型とビット（数値）または値 / `text` / 例外名 / リクエスト誤りのメッセージ /
FormulaInfo の各フィールドと結果が一致することを `EvalContextContractTest` が検査する。わかっている差:

| 項目 | Rust / wasm | Java（EvalContextService） |
|---|---|---|
| コードブロックを実行した結果 | 実行しない（スタブ必須） | 許可時は本物を実行（スタブより優先）。契約テストの対象外 |
| 評価失敗・parse 失敗の `error.message` | Rust の文言 | Java の例外メッセージ（例外名 `error.kind` は一致） |
| parse 失敗の `diagnostic` | あり | なし（playground は wasm の `te_check` で補う） |
| `te_eval_trace` の `trace` | 記録する | `"trace":null` と `"traceUnavailable"`（Java の評価器は trace を持たない） |
| `random()` と `seed` | `seed` で決まる | `seed` は検査のみ、`Math.random()` |
| 不正 JSON のメッセージ | 自前 reader | Jackson の文言 |
| FormulaInfo `info` | `formulaSpan`・`declaredHash` あり、既定 backend `JAVA_CODE` | その 2 つは無し、読み込みは `P4_AST_EVALUATOR` 既定（`executionBackend` の表示が異なる） |
| FormulaInfo load 失敗の `error.kind` | 細分類（`unknown_type` 等）と `span` | `syntax` / `formula` / `load` の 3 種、`span` なし（`javaException` は一致） |

### playground の評価先切り替え

playground は起動時に `../api/playground/eval`（playground のページからの相対 URL。ビルド時の環境変数
`VITE_TE_SERVER_EVAL_URL` で変更、`off` で無効）へ `{"operation":"evalContext","formula":"1"}` を POST し、
`"evaluator":"java"` が返ったときだけ「評価先: wasm（仮の値） / サーバ（本物の Java）」を表示する。
GitHub Pages や `vite dev` では何も表示されず従来どおり。サーバ評価中の結果には「Java（本物）」が付き、
サーバに届かないときは「wasm（仮の値）で評価し直す」で戻せる。診断・補完は常に wasm、trace はサーバ評価では出ない。
リクエストは同一オリジンの cookie（`credentials: 'same-origin'`）・JSON 本文・`X-Requested-With: tinyexpression-playground`
付きで送り、**認証・CSRF 対策はホスト側** が行う（playground はトークンを持たない）。

### ホストへの組み込み（例: fraud-alert の Grizzly）

fraud-alert への実装は fraud-alert 側で行う（本リポジトリは部品と手順だけ）。想定: 社内向けサーバの manage API と
同じ Google 認証の内側、feature flag（既定 off）、まず dev/stg のみ。

1. `playground/` を `npm run build:wasm && npm run build` し、`dist/` をホストの静的リソース（例: classpath の
   `playground/`）に置く。Vite の `base: './'` なので `/s/playground/` の下でそのまま動き、既定の評価 URL は
   `/s/api/playground/eval` になる。
2. 静的ファイルと評価 endpoint を、flag が on で環境が dev/stg のときだけ登録する:

```java
if (featureFlags.isEnabled("tinyexpression.playground") && env.isDevOrStg()) {   // 既定 off
  ServerConfiguration config = httpServer.getServerConfiguration();
  config.addHttpHandler(new CLStaticHttpHandler(Main.class.getClassLoader(), "/playground/"),
      "/s/playground/");
  config.addHttpHandler(new HttpHandler() {
    @Override public void service(Request request, Response response) throws Exception {
      if (!Method.POST.equals(request.getMethod())) { response.sendError(405); return; }
      if (!googleAuth.isAuthenticated(request)) { response.sendError(401); return; }  // manage API と同じ認証
      if (!"tinyexpression-playground".equals(request.getHeader("X-Requested-With"))) {
        response.sendError(403); return;                                             // CSRF: 独自ヘッダ必須
      }
      String body = readAtMost(request.getInputStream(), 256 * 1024);               // 本文サイズの上限
      EvalContextResponse result = service.dispatch(body);                         // 成否は JSON の "ok"
      response.setContentType("application/json; charset=utf-8");
      response.getWriter().write(result.json());
    }
  }, "/s/api/playground/eval");
}
```

- `service` は上の builder で 1 つ作って共有する（スレッドセーフ）。コードブロックは既定の `DENY` のまま始め、
  許可するなら式の作者が全員信頼できる環境に限る（ADR-003）。同時実行数（セマフォ等）と監査ログはホストで持つ。
- 独自ヘッダ付きの JSON POST は別オリジンからは preflight が要るので、CORS を許可しなければ CSRF はこれで防げる。
  cookie の `SameSite` と `Origin` の検査を併用してよい。

---

## 開発

```bash
mvn -q test
```

CI は `test-baseline.txt` で既知の失敗を管理し、新規失敗で落とす。運用と更新手順は [docs/test-baseline.md](docs/test-baseline.md) 参照。

Java の CI は unlaxer の**公開版**（Maven Central）と**開発版**（unlaxer-parser master）の両方で走る。
公開版は各ジョブが依存解決に使う既定経路（必須）。開発版は `unlaxer-dev` ジョブが
`opaopa6969/unlaxer-parser` の master を job-local な isolated Maven repo へ install し、
`-Dunlaxer.version=<そのビルドの pom revision>` でテストを走らせる。unlaxer-parser master は
tinyexpression の Rust 側ピン（`rust/ubnfc-pin.txt` 等）より先行し得るため `continue-on-error: true`
で非ブロッキング（issue #173）。落ちても merge は妨げないが、step summary に記録された revision を
手がかりに unlaxer-parser 側の regression を早期発見する目的。

ドキュメント一覧: [docs/INDEX.ja.md](docs/INDEX.ja.md)

### Maven Central への公開

このマシンには Central 資格情報がないため、公開は GitHub Actions
(`.github/workflows/release-central.yml`, `workflow_dispatch`) 経由で行う。
月次上限は unlaxer-parser の `release/central-release-queue.yml`（org 共通の
リリーストレイン台帳）から取得する。

- **必要な repo secrets**（environment `release`）: `MAVEN_CENTRAL_USERNAME` /
  `MAVEN_CENTRAL_PASSWORD` / `MAVEN_GPG_PRIVATE_KEY` / `MAVEN_GPG_PASSPHRASE`。
  `guard` job はどれも使わず、`publish` job は不足時に `test -n` で即失敗する。
- **ドライラン**（ガードの確認レポートのみ、公開しない）:
  ```
  gh workflow run release-central.yml -f version=2.0.0 -f confirm=org.unlaxer/2026-09 -f dry_run=true
  ```
- **本番公開**（枠に空きがあるとき）:
  ```
  gh workflow run release-central.yml -f version=2.0.0 -f confirm=org.unlaxer/2026-09 -f dry_run=false
  ```
  `version` は pom の `version` と完全一致、`confirm` は現在の UTC 月の
  `org.unlaxer/YYYY-MM` と完全一致させる（`scripts/release-central.sh` と同じ規約）。
- 既に Central に存在するバージョンなら `publish` job はアップロードを
  スキップする（タグ付けは常に行う）。テストは `master` へのマージ時点で
  CI (`.github/workflows/ci.yml`) が既にゲートしているため、deploy 時は
  `-DskipTests` を明示する。
- Central 資格情報を手元に持つ場合は `scripts/release-central.sh` がローカル代替手段。

## エンジンの選び方

2.0.0 の既定は **ubnfc parser**、旧コンビネータ実行系は **unlaxer Classic**（`classic` モード、3.0 で削除）。使い分けの目安: [unlaxer-parser/docs/engine-selection-guide-ja.md](https://github.com/opaopa6969/unlaxer-parser/blob/master/docs/engine-selection-guide-ja.md)
