# TinyExpression 仕様書

**バージョン**: v1.4.11  
**ステータス**: Accepted  
**最終更新**: 2026-04-19  
**対象リポジトリ**: opaopa6969/tinyexpression

---

## 目次

1. [概要](#1-概要)
2. [機能仕様](#2-機能仕様)
3. [データ永続化層](#3-データ永続化層)
4. [ステートマシン](#4-ステートマシン)
5. [ビジネスロジック](#5-ビジネスロジック)
6. [API / 外部境界](#6-api--外部境界)
7. [UI](#7-ui)
8. [設定](#8-設定)
9. [依存](#9-依存)
10. [非機能要件](#10-非機能要件)
11. [テスト戦略](#11-テスト戦略)
12. [デプロイ / 運用](#12-デプロイ--運用)

---

## 1. 概要

### 1.1 プロジェクト概要

TinyExpression は **Java アプリケーションに組み込み可能な式評価エンジン（UDF スタイル）**である。ランタイムで式文字列を評価し、複数の式を依存関係付きで実行する機能を提供する。

**コアバリュープロポジション**:

- 外部ファイル（`formulaInfo.txt`）に式を定義し、Java コードを変更せずにビジネスロジックを変更できる
- テナントごと・ルールごとに異なる式を持てる（マルチテナント対応）
- Java 開発者に親しみやすい式言語（Java の演算子・型昇格規則を踏襲）
- 6 つのバックエンドにより、パフォーマンスと互換性のトレードオフを式単位で制御できる

**主要機能**:

- ランタイムでの式評価（変数参照、演算子、関数、制御構造）
- 複数式の依存関係付き実行（`TinyExpressionsExecutor`）
- 6 つの実行バックエンド（JavaCode コンパイル系 / AST 評価系 / P4 型安全系）
- 外部 Java メソッドの呼び出し（`external` キーワード）
- ユーザー定義メソッド（`call` キーワード）
- Java コードブロック埋め込み（triple-backtick フェンス）
- LSP / DAP サポート（VS Code 拡張、別リポジトリ: `tinyexpression-ide`）

### 1.2 アーキテクチャ概要

TinyExpression は **ハイブリッドアーキテクチャ** を採用する。手書きのレガシーパーサースタックと、UBNF 文法から自動生成された P4 パースタックが共存し、両スタックが 6 つの実行バックエンドのいずれかに接続される。

```
式テキスト
    │
    ├─► レガシーパーサー (unlaxer-common パーサーコンビネータ、手書き)
    │       └─► ParseTree ──► AST
    │                           ├─► JAVA_CODE (javac コンパイル)
    │                           ├─► JAVA_CODE_LEGACY_ASTCREATOR (旧 OOTC)
    │                           ├─► AST_EVALUATOR (ツリー走査)
    │                           └─► DSL_JAVA_CODE (ハイブリッド)
    │
    └─► P4 パーサー (UBNF 自動生成、型安全)
            └─► P4 ParseTree ──► P4 AST (sealed interface)
                                    ├─► P4_AST_EVALUATOR (型安全評価)
                                    └─► P4_DSL_JAVA_CODE (DSL Java エミッタ)
```

### 1.3 6 つの実行バックエンド

| バックエンド | 実装クラス | 戦略 | ステータス |
|------------|----------|------|----------|
| `JAVA_CODE` | `JavaCodeCalculatorV3` | Parse → Java ソース生成 → javac → ロード → 実行 | 本番（プロダクションベースライン） |
| `JAVA_CODE_LEGACY_ASTCREATOR` | `LegacyAstCreatorJavaCodeCalculator` | 旧 AST クリエータ使用の同様パイプライン | **凍結**（リグレッション比較用） |
| `AST_EVALUATOR` | `AstEvaluatorCalculator` | Parse → AST → ツリー走査評価（4 段フォールバックチェーン） | 本番 |
| `DSL_JAVA_CODE` | `DslJavaCodeCalculator` | ハイブリッド: ネイティブ DSL Java エミッタ + レガシーブリッジ | マイグレーションターゲット |
| `P4_AST_EVALUATOR` | `P4AstEvaluatorCalculator` | UBNF パーサー → P4 AST → 型安全評価 | **PRIMARY (P4)**、LSP/DAP の参照実装 |
| `P4_DSL_JAVA_CODE` | `P4DslJavaCodeCalculator` | UBNF パーサー → P4 AST → DSL Java エミッタ | マイグレーションターゲット (P4) |

### 1.4 パーサー層の構成

#### レガシーパーサー（unlaxer-common）

手書きの **パーサーコンビネータ** スタック。全言語機能をカバーする。

| インタフェース / クラス | 役割 |
|----------------------|------|
| `org.unlaxer.parser.Parser` | ルートパーサーインタフェース |
| `org.unlaxer.parser.combinator.BasicCombinator` | コンビネータプリミティブ（`seq`, `choice`, `zeroOrMore` 等） |
| `org.unlaxer.parser.AbstractParser` | 具体パーサーの基底クラス |
| `org.unlaxer.tinyexpression.parser.*` | TinyExpression 固有のパーサー実装群 |

#### P4 パーサー（UBNF 自動生成）

`tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf`（321 行）から `unlaxer-dsl` により生成される。

| 生成物 | 役割 |
|--------|------|
| `TinyExpressionP4Parsers` | エントリポイントの生成パーサー |
| P4 AST（sealed interface 階層） | 型安全な AST ノード群 |
| `TinyExpressionP4Mapper` | ParseTree → P4 AST マッパー |
| `P4TypedAstEvaluator` | 型安全 AST 評価器（PRIMARY） |

P4 スタックは `instanceof` ベースのディスパッチを使用し、コンパイル時の網羅性チェックを提供する。

### 1.5 実行環境要件

| 要件 | 値 |
|------|-----|
| Java | 21+ |
| Maven | 3.8+ |
| `add-opens` | `java.base/java.lang`, `java.base/java.util` （テスト・リフレクション使用時） |
| OSSRH | GPG 署名必須（Maven Central 公開時） |

---

## 2. 機能仕様

### 2.1 リテラル

#### 2.1.1 数値リテラル

| 表記 | 説明 |
|------|------|
| `123` | 整数 |
| `3.14` | 小数 |
| `-42` | 負数（符号プレフィックス） |
| `+100` | 正数（符号プレフィックス） |
| `1.5e3` | 指数表記（= 1500.0）、`e` / `E` どちらも有効 |
| `42L` | Long サフィックス |
| `3.14f` | Float サフィックス |
| `3.14d` | Double サフィックス |

- 符号（`+`, `-`）プレフィックスをサポート
- デフォルト型は `FormulaInfo` の `numberType` で制御（省略時: `float`）
- 具体的な型キャスト構文（`int:42`, `long:100L` 等）はレガシーパーサーでサポート

#### 2.1.2 文字列リテラル

```text
'hello'    シングルクォート
"hello"    ダブルクォート
```

- シングルクォート・ダブルクォートは互換
- 基本 Java エスケープシーケンス（`\n`, `\t`, `\\`, `\"`, `\'` 等）に準拠
- 文字列リテラル内の完全なエスケープシーケンス仕様は実装ベース（公式形式定義は未確定）

#### 2.1.3 真偽値リテラル

```text
true
false
```

### 2.2 変数

変数名は `$` プレフィックスで始まる（MUST）。

```text
$age
$name
$isMember
$totalAmount
```

- 値は `CalculationContext` から取得
- 存在しない変数の参照は `null` を返す
- 変数名は大文字小文字を区別する

**型付き変数参照の変形**（レガシーパーサー）:

| 構文形式 | 説明 |
|---------|------|
| `$name` | 型推論あり（裸の変数参照） |
| `number:$name` | 数値型プレフィックス |
| `string:$name` | 文字列型プレフィックス |
| `$name as number` | 数値型サフィックス |
| `$name as string` | 文字列型サフィックス |
| `$name as boolean` | 真偽値型サフィックス |

P4 パーサーでは `VariableRef` として単一の多態ルールで処理される。

### 2.3 算術演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `+` | 加算（文字列の場合は連結） | `1 + 2`, `$a + 'x'` |
| `-` | 減算 | `5 - 3` |
| `*` | 乗算 | `2 * 3` |
| `/` | 除算 | `10 / 3` |

**注意**: ネストした括弧を含む乗算（例: `(10-2)*(7-3)`）は v1.4.11 で `DSL_JAVA_CODE` / `P4_DSL_JAVA_CODE` バックエンドのバグが修正された。

### 2.4 比較演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `==` | 等価 | `$age == 20` |
| `!=` | 非等価 | `$age != 20` |
| `>` | より大きい | `$age > 18` |
| `>=` | 以上 | `$age >= 18` |
| `<` | より小さい | `$age < 65` |
| `<=` | 以下 | `$age <= 65` |

- 文字列の `==` / `!=` は `String.equals()` を内部的に使用する
- すべての比較演算子は結果として常に `boolean` を返す

### 2.5 論理演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `&` | 論理 AND | `$a & $b` |
| `\|` | 論理 OR | `$a \| $b` |
| `^` | 論理 XOR | `$a ^ $b` |
| `not()` | 論理否定 | `not($flag)` |

### 2.6 演算子の優先順位（高い順）

1. `()` — 括弧
2. `not()` — 否定
3. `*`, `/` — 乗除算
4. `+`, `-` — 加減算
5. `>`, `>=`, `<`, `<=` — 比較
6. `==`, `!=` — 等価比較
7. `^` — XOR
8. `&` — AND
9. `|` — OR（最低）

### 2.7 条件式

#### 2.7.1 if / else

```text
if(condition){thenValue}else{elseValue}
```

- `condition` は真偽値式（MUST）
- `else` 節は必須（MUST）
- 両分岐の値を返す
- `if` とパーレン / ブロックの間に空白やブロックコメントが入っても有効

例:

```text
if($age >= 20){100}else{0}
if   (true){1}else{2}
/*head*/if(true){1}else{2}
if/*c*/(true){1}else{2}
```

#### 2.7.2 三項演算子（ternary）

```text
condition ? thenValue : elseValue
```

`if`/`else` と等価な条件式。P4 バックエンドでサポート。LSP CodeAction により `if` / ternary の双方向変換が可能（IDE 側機能）。

#### 2.7.3 match 式

```text
match{
  condition1 -> value1,
  condition2 -> value2,
  default -> defaultValue
}
```

- 上から順に条件を評価し、最初に真となった条件の値を返す
- `default` 節を強く推奨（SHOULD）
- 各ケースはカンマで区切る
- 数値 / 文字列 / 真偽値 / オブジェクト の各型の結果をサポート

例（数値結果）:

```text
match{
  $countryCode == 'JP' -> 1,
  $countryCode == 'US' -> 2,
  default -> 0
}
```

例（文字列結果）:

```text
match{
  1 == 1 -> 'A',
  default -> 'B'
}
```

例（真偽値結果）:

```text
match{
  1 == 0 -> false,
  default -> true
}
```

### 2.8 文字列操作

#### 2.8.1 組み込み文字列関数（関数呼び出し形式）

| 関数 | 説明 | 例 |
|------|------|-----|
| `toUpperCase(s)` | 大文字変換 | `toUpperCase($name)` |
| `toLowerCase(s)` | 小文字変換 | `toLowerCase($name)` |
| `trim(s)` | 空白除去 | `trim($input)` |
| `length(s)` | 文字列長 | `length($name)` |
| `toNum(s)` | 数値変換 | `toNum($numStr)` |
| `toNum(s, default)` | デフォルト付き数値変換 | `toNum($numStr, 0)` |

#### 2.8.2 ドット形式の文字列メソッド

| メソッド | 説明 | 例 |
|---------|------|-----|
| `.startsWith(str)` | 前方一致 | `$msg.startsWith('hello')` |
| `.endsWith(str)` | 後方一致 | `$msg.endsWith('world')` |
| `.contains(str)` | 部分一致 | `$msg.contains('abc')` |
| `.isPresent()` | null / 空文字でない | `$name.isPresent()` |
| `.in(str, str, ...)` | いずれかに一致 | `$code.in('JP', 'US')` |
| `.toUpperCase()` | 大文字変換（P4 UBNF のみ） | `$name.toUpperCase()` |
| `.toLowerCase()` | 小文字変換（P4 UBNF のみ） | `$name.toLowerCase()` |
| `.trim()` | 空白除去（P4 UBNF のみ） | `$name.trim()` |
| `.length()` | 文字列長（P4 UBNF のみ） | `$name.length()` |

**注意**: `.toUpperCase()`, `.toLowerCase()`, `.trim()`, `.length()` のドット形式は P4 UBNF のみ（`UBNF-ONLY`）。レガシーパーサーでは関数形式を使用する。

#### 2.8.3 文字列スライス

```text
$message[0:3]    インデックス 0, 1, 2（末端排他）
```

- Python スタイルのスライス構文
- `SliceParser` / `slice(...)` 関数（レガシーパーサー）でサポート
- v1.4.9 で P4 バックエンドでのパリティを達成（最後の機能ギャップを閉じた）

#### 2.8.4 文字列連結

```text
$firstName + ' ' + $lastName
'prefix' + $name + 'suffix'
```

`+` 演算子はいずれかのオペランドが文字列の場合、連結を行う。数値オペランドは `String.valueOf()` で変換される。

### 2.9 組み込み数学関数

| 関数 | 説明 | 例 |
|------|------|-----|
| `min(a, b, ...)` | 最小値（2 引数以上） | `min($a, $b, 0)` |
| `max(a, b, ...)` | 最大値（2 引数以上） | `max($a, $b, 100)` |
| `abs(n)` | 絶対値 | `abs($score)` |
| `floor(n)` | 切り捨て | `floor($value)` |
| `ceil(n)` | 切り上げ | `ceil($value)` |
| `round(n)` | 四捨五入 | `round($value)` |
| `sin(n)` | 正弦 | `sin(30)` |
| `cos(n)` | 余弦 | `cos(60)` |
| `tan(n)` | 正接 | `tan(45)` |
| `sqrt(n)` | 平方根 | `sqrt(16)` |
| `pow(x, y)` | 累乗 | `pow(2, 10)` |
| `log(n)` | 自然対数 | `log($val)` |
| `exp(n)` | 自然指数関数 | `exp(1)` |
| `random()` | 0.0〜1.0 の乱数 | `random()` |

上記 14 関数はすべてレガシーパーサーと P4 UBNF でパリティが達成されている（`PARITY`）。

### 2.10 組み込み日時関数

| 関数 | 説明 |
|------|------|
| `inTimeRange($ts, start, end)` | タイムスタンプが範囲内か |
| `inDayTimeRange($ts, start, end)` | 時刻（時分）が範囲内か |

- `inTimeRange` / `inDayTimeRange` はレガシーパーサーでサポート（`HAND-ONLY`）
- `DayOfWeek` enum（`MONDAY`〜`SUNDAY`）を引数に使用可能
- v1.4.9 でサポート追加

### 2.11 変数宣言

```text
variable $name as type set [if not exists] defaultValue description='説明';
var $name as type set defaultValue description='説明';
```

| パーツ | 説明 | 必須 |
|--------|------|------|
| `variable` / `var` | 変数宣言キーワード | MUST |
| `$name` | 変数名（`$` プレフィックス必須） | MUST |
| `as type` | 型ヒント（`number`, `string`, `boolean`, `object`, `float`） | SHOULD |
| `set` | デフォルト値の設定 | OPTIONAL |
| `if not exists` | コンテキストに値が存在しない場合のみ設定 | OPTIONAL |
| `description='...'` | 説明（LSP ホバーで使用） | OPTIONAL |

デフォルト値には式を使用できる:

```text
variable $price as number set if not exists match{1==1->3,default->5} description='price';
```

例:

```text
variable $gender as string set if not exists 'male' description='gender';
variable $age as number set 18 description='age in years';
variable $isMember as boolean description='membership flag';
var $amount as number set if not exists 100 description='amount';
```

### 2.12 ユーザー定義メソッド

フォーミュラ内に複数の名前付きメソッドを定義できる。

```text
returnType methodName($param as type, ...){
  body
}
```

- エントリポイントのメソッド名は `main`（複数メソッド使用時）
- メソッド呼び出しは `call methodName(args)` 構文
- `call internal methodName(args)` でも呼び出し可能
- サポートされる戻り値型: `float`, `string`, `boolean`, `object`
- メソッドパラメータは型ヒントを持てる（`$param as number` 等）
- メソッドスコープ: レキシカルスコープ（パラメータ名はコンテキスト変数をシャドウする）

例（数値メソッド）:

```text
float main(){
  match{
    $age < 18 -> 500,
    default -> call feeByGender($gender)
  }
}

float feeByGender($gender as string){
  match{
    $gender == 'female' -> 1000,
    default -> 1800
  }
}
```

例（オブジェクトメソッド）:

```text
call identity('payload')
object identity($payload as object){
  $payload
}
```

例（引数なしメソッド）:

```text
call provide()
object provide(){
  'ok'
}
```

### 2.13 外部 Java メソッド呼び出し

ホストアプリケーションの Java メソッドを式内から呼び出す機能。

#### 2.13.1 import 宣言

```text
import package.ClassName#methodName as alias;
```

#### 2.13.2 呼び出し構文

```text
external returning as returnType alias(arg1, arg2, ...)
```

`returnType` には `number`, `string`, `boolean`, `object` を指定できる。

#### 2.13.3 例

式（FormulaInfo の formula フィールド）:

```text
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
```

Java セットアップ:

```java
// 式実行前にコンテキストにオブジェクトを登録
context.set(new sample.v1.CheckDigits());
```

Java クラス要件:

```java
package sample.v1;
import org.unlaxer.tinyexpression.CalculationContext;

public class CheckDigits {
  // 第1引数は必ず CalculationContext
  public boolean check(CalculationContext context, String target) {
    return target.matches("\\d+");
  }
}
```

#### 2.13.4 レガシーサイドエフェクト構文（手書きパーサーのみ）

```text
call with side effect returning as boolean : JavaClass#method($arg)
call external returning as string : JavaClass#method($arg)
```

この構文はレガシーパーサーのみサポート（`HAND-ONLY`）。P4 バックエンドでは `external returning as` 構文を使用する。

### 2.14 Java コードブロック

> **セキュリティ警告**: Java コードブロックは式評価時に任意の Java コードを JVM 上でコンパイル・実行する。式作成者が完全に信頼される環境でのみ有効にすること。信頼されないユーザーにこの機能を公開してはならない（MUST NOT）。

式の `formula` フィールド内に triple-backtick 構文で Java クラスを直接埋め込める:

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

**動作**:

- バッククォート 3 つ + `java:完全修飾クラス名` でブロック開始
- バッククォート 3 つでブロック終了
- クラスは `javax.tools.JavaCompiler` でインメモリコンパイル
- コンパイル済みバイトコードはディスクに永続化されない（`MemoryClassLoader` スコープ）
- `JavaCodeBlockPolicy.setEnabled(false)` で無効化可能（v1.4.11 以降）

**関連クラス**:

- `org.unlaxer.tinyexpression.parser.javalang.TripleBackTickParser`
- `org.unlaxer.tinyexpression.parser.javalang.CodeParser`
- `org.unlaxer.tinyexpression.parser.javalang.CodeStartParser`
- `org.unlaxer.tinyexpression.parser.javalang.CodeEndParser`
- `org.unlaxer.compiler.MemoryClassLoader`

### 2.15 コメント

| 種類 | 構文 | コンテキスト |
|------|------|------------|
| 行コメント | `# comment` | FormulaInfo メタデータ行（`#` 始まり） |
| ブロックコメント | `/* comment */` | 式本文内の任意の位置（制御構造の前後も含む） |

ブロックコメントは制御構造の前後に入っても有効:

```text
/*head*/if(true){1}else{2}
if/*c*/(true){1}else{2}
```

### 2.16 アノテーション（式内アノテーション）

式本文内に `@name(key=value, ...)` 形式のアノテーションを記述できる。

```text
@ruleVersion(version=2, since='2026-01')
if($age >= 20){100}else{0}
```

アノテーションはパーサーに処理され、メタデータとして FormulaInfo に付加される。LSP ホバーで表示される。

### 2.17 機能パリティマトリクス（レガシー vs P4 UBNF）

レガシーパーサーと P4 UBNF の機能カバレッジ統計（`docs/feature-parity-diff.md` より、2026-03-27 時点）:

| ステータス | 機能数 |
|-----------|-------|
| PARITY（両方でカバー） | 68 |
| HAND-ONLY（レガシーのみ） | 30 |
| UBNF-ONLY（P4 のみ） | 16 |
| PARTIAL（部分的） | 14 |
| **合計** | **128** |

**主要な HAND-ONLY ギャップ**（P4 UBNF への移行優先度高）:

1. サイドエフェクト式（`call with side effect returning as ...`）— 6 パーサー
2. 文字列述語（`.in()`, `.startsWith()`, `.endsWith()`, `.contains()`）— 4 機能
3. ドメイン関数（`inTimeRange`, `inDayTimeRange`, `isPresent`, `DayOfWeek`）— 4 機能
4. 数値具体型（int/long/float/double/BigDecimal キャスト、プレフィックス、サフィックス）— 6+ パーサーファミリ
5. 文字列連結（`+` 演算子）— 1 機能
6. スライス（Python スタイル文字列スライス）— 1 機能

---

## 3. データ永続化層

### 3.1 N/A

TinyExpression はデータベースや永続ストレージを直接管理しない。

式定義は以下の手段で管理される:

| 手段 | 詳細 |
|------|------|
| ファイルシステム | `formulaInfo.txt` ファイル（ディレクトリ構成によるテナント分離） |
| インメモリキャッシュ | `FileBaseTinyExpressionInstancesCache`（テナント ID をキーに保持） |
| ランタイムクラスキャッシュ | `MemoryClassLoader` で保持（GC まで有効） |

### 3.2 formulaInfo.txt の永続化モデル

```
<formula-root>/
  <tenant-id-1>/formulaInfo.txt    ← テナント 1 の式定義群
  <tenant-id-2>/formulaInfo.txt    ← テナント 2 の式定義群
  <tenant-id-N>/formulaInfo.txt
```

- 式の CRUD や履歴管理は宿主アプリケーションの責務
- `FileBaseTinyExpressionInstancesCache` はファイルをロードするが、変更検知・再ロードは宿主アプリケーションが管理する
- コンパイル済み Java クラスのバイトコードは、`MemoryClassLoader` のガベージコレクション時に破棄される

### 3.3 インメモリキャッシュ

`FileBaseTinyExpressionInstancesCache` はテナントごとに `List<Calculator>` をキャッシュする。初回アクセス時にファイルをロードし、以降はキャッシュから返す（遅延ロード）。

キャッシュキー: `TenantID`

---

## 4. ステートマシン

### 4.1 Evaluator フォールバックチェーン（AST_EVALUATOR）

`AstEvaluatorCalculator.apply()` は以下の 4 段フォールバックチェーンで動作する（ADR-001 により P4TypedAstEvaluator が PRIMARY に昇格、v1.4.10 以降）:

```
【段階 1】P4TypedAstEvaluator  ← PRIMARY（型安全 sealed switch）
              │
              │ 失敗: UnsupportedOperationException（P4 文法ギャップ）
              ▼
【段階 2】GeneratedP4NumberAstEvaluator  ← リフレクション経由の生成 AST 評価
              │
              │ 失敗: Optional.empty()
              ▼
【段階 3】AstTokenTreeEvaluator  ← レガシー AST ツリー走査（手書き）
              │
              │ 失敗: Optional.empty()
              ▼
【段階 4】JavaCode fallback (JAVA_CODE パス)  ← 最終安全網
```

**設計原則**:

- フォールバックチェーンは **安全網（safety net）** であり、通常の実行パスではない
- 段階 4 まで到達することは設計上の問題を意味する（監視が必要）
- フォールバック発生は `_tinyExecutionImplementation` マーカーで観測可能

**段階 1（P4TypedAstEvaluator）で native に処理されるノードタイプ（v1.4.10+）**:

```java
IfExpr                        // if(...){...}else{...}
MatchExpr                     // match{...}
BinaryExpr                    // 四則演算、比較
ComparisonExpr                // ==, !=, >, >=, <, <=
MethodInvocationExpr          // call identity(1) 等の UDF 呼び出し
ExternalBooleanInvocationExpr // external returning as boolean ...
ExternalNumberInvocationExpr  // external returning as number ...
ExternalStringInvocationExpr  // external returning as string ...
ExternalObjectInvocationExpr  // external returning as object ...
StringConcatExpr              // $firstName + ' ' + $lastName
InTimeRangeExpr               // inTimeRange(9, 17)
InDayTimeRangeExpr            // inDayTimeRange(MONDAY, 9, FRIDAY, 17)
```

### 4.2 P4 バックエンドのフォールバック

`P4_AST_EVALUATOR` および `P4_DSL_JAVA_CODE` は:

1. P4 UBNF パーサーでパースを試行
2. **成功**: `_tinyP4ParserUsed=true` を設定し、型安全 P4 AST で処理
3. **失敗**（P4 文法ギャップ）: `_tinyP4ParserUsed=false` を設定し、対応する非 P4 バックエンドへグレースフルフォールバック

```
P4_AST_EVALUATOR のフォールバック:
  P4 パース成功 → P4TypedAstEvaluator ベースの評価
  P4 パース失敗 → AST_EVALUATOR チェーン（段階 1-4）

P4_DSL_JAVA_CODE のフォールバック:
  P4 パース成功 → DSL Java エミッタ → javac → 実行
  P4 パース失敗 → DSL_JAVA_CODE チェーン
```

### 4.3 DSL_JAVA_CODE のハイブリッド状態遷移

```
式テキスト入力
    │
    ├── ネイティブ DSL Java エミッタ対応構文 ──►
    │     _tinyDslJavaNativeEmitterUsed = true
    │     _tinyExecutionImplementation = dsl-javacode-native
    │     → Java ソース生成 → javac → 実行
    │
    └── 非対応構文 ────────────────────────────►
          _tinyExecutionImplementation = legacy-javacode-bridge
          → レガシー JavaCode ブリッジ → javac → 実行
```

### 4.4 マルチフォーミュラ実行パイプライン

```
formulaInfo.txt ファイル群（テナントごと）
    │
    ▼
FormulaInfoParser → List<FormulaInfo>
    │ キー検証、バックエンド名検証
    ▼
CalculatorCreatorRegistry → List<Calculator>
    │ バックエンドごとに Calculator インスタンスを生成（コンパイルも実行）
    ▼
FileBaseTinyExpressionInstancesCache（TenantID をキーにキャッシュ）
    │
    ▼
TinyExpressionsExecutor.execute()
    │
    ├── dependsOnByNestLevel() 昇順でソート（依存関係グラフのネストレベル）
    ├── Predicate<Calculator> でフィルタリング
    └── 順次実行
          Calculator.apply(context)
              │
              ▼
          ResultConsumer.accept(context, calculator, formulaInfo, result)
              │ 結果を CalculationContext に書き戻す（典型的な実装）
              ▼
          次の Calculator は前の結果を変数として参照可能
```

### 4.5 インメモリ Java コンパイルパイプライン

`JAVA_CODE` 系バックエンドのコンパイルフロー:

```
式テキスト
    │ 解析・AST 構築
    ▼
Java ソース文字列
    │ javax.tools.JavaCompiler（インプロセス）
    ▼
MemoryJavaFileManager
    │
    ▼
ByteArrayJavaFileObject（バイトコード in-memory）
    │
    ▼
MemoryClassLoader → Class<? extends PreConstructedCalculator>
    │
    ▼
インスタンス生成 → Calculator.apply(CalculationContext) で実行
```

**関連クラス**:

- `org.unlaxer.compiler.MemoryClassLoader`
- `org.unlaxer.compiler.MemoryJavaFileManager`
- `org.unlaxer.compiler.CompileContext`

### 4.6 ランタイムマーカー

すべてのバックエンドは実行後に以下のコンテキストマーカーを設定する。宿主アプリケーションやツールはこれらで実行経路を観測できる。

**共通マーカー（全バックエンド）**:

| マーカー | 説明 | 例 |
|---------|------|-----|
| `_tinyExecutionBackend` | 使用されたバックエンド名 | `JAVA_CODE` |
| `_tinyExecutionMode` | 実行モード | `token`, `ast-evaluator` |
| `_tinyExecutionImplementation` | 実装種別 | `dsl-javacode-native`, `legacy-javacode-bridge` |
| `_tinyExecutionBridgeImplementation` | ブリッジ実装種別（DSL バックエンド） | |
| `_tinyExecutionNonBridgeImplementation` | 非ブリッジ実装種別 | |

**DSL バックエンド追加マーカー**:

| マーカー | 説明 |
|---------|------|
| `_tinyDslJavaEmitterMode` | DSL Java エミッタモード |
| `_tinyDslJavaNativeEmitterUsed` | ネイティブエミッタ使用フラグ（`true`/`false`） |

**P4 バックエンド追加マーカー**:

| マーカー | 説明 |
|---------|------|
| `_tinyP4ParserUsed` | P4 パーサー使用フラグ（`true`/`false`） |

---

## 5. ビジネスロジック

### 5.1 型システム（ExpressionTypes）

**enum**: `org.unlaxer.tinyexpression.parser.ExpressionTypes`

| 値 | Java ラッパー型 | Java プリミティブ型 | リテラルサフィックス | 用途 |
|----|-------------|--------------------|-------------------|------|
| `_byte` | `Byte` | `byte` | — | |
| `_short` | `Short` | `short` | — | |
| `_int` | `Integer` | `int` | — | |
| `_long` | `Long` | `long` | `"L"` | |
| `_float` | `Float` | `float` | `"f"` | **デフォルト数値型** |
| `_double` | `Double` | `double` | `"d"` | |
| `number` | `Float` | `float` | — | `_float` のエイリアス |
| `string` | `String` | — | — | |
| `_boolean` | `Boolean` | `boolean` | — | |
| `object` | `Object` | — | — | |
| `bigDecimal` | `BigDecimal` | — | — | 限定サポート |
| `bigInteger` | `BigInteger` | — | — | 限定サポート |
| `timestamp` | `Timestamp` | — | — | 特殊用途 |
| `_void` | `Void` | `void` | — | 式の戻り値には使用しない |

**`number` 型**: `float` のエイリアスとして扱われる（`Float.class` にマッピング）。`FormulaInfo` の `resultType:number` や変数宣言の `as number` で使用可能。

### 5.2 型昇格ルール（ADR-002）

2 つの数値オペランドの型が異なる場合、Java の拡大変換ルール（widening primitive conversion）に従い、より広い型に昇格される。

**型昇格の梯子**:

```
double > float > long > int > short > byte
```

**詳細ルール**:

| 左辺 | 右辺 | 結果型 |
|------|------|--------|
| `double` | any numeric | `double` |
| `float` | `long` 以下 | `float` |
| `long` | `int` 以下 | `long` |
| `int` | `short` / `byte` | `int` |
| `number`（`float` エイリアス） | any | `float` と同じ |

**文字列昇格**:

- `+` 演算子はいずれかのオペランドが `string` の場合、文字列連結を行う
- 数値オペランドは `String.valueOf()` で文字列化される

**比較結果型**:

- すべての比較演算子（`==`, `!=`, `>`, `>=`, `<`, `<=`）はオペランド型によらず常に `boolean` を返す

**設計根拠**:

- Java の `JAVA_CODE` バックエンドが javac の型ルールに自動的に従うため、AST 評価系との一貫性を保つために Java 拡大変換ルールを採用した
- `SpecifiedExpressionTypes` による式レベルの型指定と矛盾しない

**制限**:

- `BigDecimal`, `BigInteger` は標準の型昇格に参加しない
- これらの型を使った混合算術は限定的サポートで、予期しない結果を生じる可能性がある

### 5.3 null ハンドリング

| 状況 | 動作 |
|------|------|
| `CalculationContext` に存在しない変数の参照 | `null` を返す |
| `null` を返した変数を算術演算に使用 | バックエンド依存（定義しない） |
| 文字列比較で片方が `null` | バックエンド依存 |
| `.isPresent()` でのチェック | null / 空文字列の場合 `false` を返す |

`$var.isPresent()` は null チェックの推奨パターンである。

### 5.4 Java コードブロックポリシー（ADR-003、v1.4.11 以降）

**クラス**: `org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeBlockPolicy`

```java
// アプリケーション起動時（Calculator インスタンス生成前）に一度設定
JavaCodeBlockPolicy.setEnabled(false);

// テスト後のリセット
JavaCodeBlockPolicy.reset();
```

| メソッド | 説明 |
|---------|------|
| `isEnabled()` | 現在のポリシーを返す（デフォルト: `true`） |
| `setEnabled(boolean)` | コードブロック実行の有効 / 無効を設定 |
| `reset()` | デフォルト状態（有効）に戻す（テスト用） |

**セキュリティポリシー（ADR-003）**:

Java コードブロックは JVM プロセスと同じ権限で実行される。サンドボックスはない。コードは以下を実行できる:

- ファイルシステムの読み書き
- ネットワーク接続
- `System.exit()` の呼び出し
- 環境変数・システムプロパティへのアクセス
- クラスパス上の任意クラスのインスタンス化
- スレッドの生成

このため:

1. **信頼された作成者専用**: フォーミュラ作成者がアプリケーション開発者と同等の信頼を持つ環境でのみ有効化する（MUST）
2. **エンジンはサンドボックスを提供しない**: サンドボックスが必要な場合は JVM レベルで宿主アプリケーションが実装する
3. **明示的なドキュメント化**: Java コードブロックに言及するすべてのドキュメントにセキュリティ警告を含める（MUST）

### 5.5 依存解決アルゴリズム

`TinyExpressionsExecutor` が複数フォーミュラを依存関係付きで実行する際のアルゴリズム:

1. `formulaInfo.txt` からすべての式定義を読み込む
2. `dependsOn` フィールドで宣言された依存関係をグラフとして構築
3. `Calculator.dependsOnByNestLevel()` でネストレベルを計算（依存の深さ）
4. ネストレベル昇順でソートして実行順序を決定
5. 各 `Calculator` を順次実行し、`ResultConsumer` に結果を渡す
6. `ResultConsumer` が結果を `CalculationContext` の変数として書き込む
7. 後続の `Calculator` は先行の結果を変数として参照できる

**循環依存**: 検出機能あり（エラーメッセージは限定的）。

**依存宣言の例**:

```text
calculatorName:baseScore
formula:
if($age >= 20){100}else{0}
---END_OF_PART---

calculatorName:bonusScore
dependsOn:baseScore          ← baseScore の実行後に実行される
formula:
$baseScore + 10
---END_OF_PART---
```

### 5.6 SpecifiedExpressionTypes

式の評価型と結果型を明示的に指定する。

```java
new SpecifiedExpressionTypes(
    ExpressionTypes._float,   // 式の評価型
    ExpressionTypes._float    // 結果型
)
```

- 第 1 引数: 式全体の評価型（算術演算の基本型）
- 第 2 引数: `Calculator.apply()` が返す結果型

`FormulaInfo` の `resultType` キーはこの指定に対応する。

---

## 6. API / 外部境界

### 6.1 TinyExpression 公開 API

#### 6.1.1 CalculationContext

**クラス**: `org.unlaxer.tinyexpression.CalculationContext`

**主要ファクトリメソッド**:

| メソッド | 説明 |
|---------|------|
| `CalculationContext.newConcurrentContext()` | スレッドセーフなコンテキストを生成（MUST use for concurrent access） |

**主要メソッド**:

| メソッド | 説明 |
|---------|------|
| `set(String name, Object value)` | 変数に値を設定 |
| `set(Object instance)` | 外部 Java オブジェクトを登録（クラス名ベースで参照） |
| `setObject(String name, Object value)` | `Object` 型として値を設定 |
| `get(String name)` | 変数の値を取得 |

**スレッドセーフティ**: `newConcurrentContext()` で生成されたコンテキストはスレッドセーフ（MUST）。複数スレッドから同時にアクセス可能。

#### 6.1.2 Calculator インタフェース

**インタフェース**: `org.unlaxer.tinyexpression.Calculator`

| メソッド | 説明 |
|---------|------|
| `apply(CalculationContext)` | 式を評価して結果（`Number`, `String`, `Boolean`, `Object`）を返す |
| `dependsOnByNestLevel()` | 依存ネストレベル（実行順序決定に使用） |

**スレッドセーフティ**: すべての `Calculator` 実装は `apply()` がステートレスであること（MUST）。同一インスタンスを複数スレッドから並行して呼び出せる。

#### 6.1.3 PreConstructedCalculator

**クラス**: `org.unlaxer.tinyexpression.PreConstructedCalculator`

事前構築済みの `Calculator`。コンストラクタ時に式のコンパイルを行う。`JAVA_CODE` バックエンドではコンストラクタ内で javac コンパイルが実行される。構築コストが高いため、インスタンスの再利用を推奨する。

#### 6.1.4 JavaCodeCalculatorV3

**クラス**: `org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3`

```java
new JavaCodeCalculatorV3(
    Source source,                     // 式テキスト（new Source(formulaString)）
    String className,                  // 生成クラス名（有効な Java 識別子、MUST）
    SpecifiedExpressionTypes types,    // 型指定
    ClassLoader classLoader            // クラスローダー
)
```

**使用例**:

```java
CalculationContext context = CalculationContext.newConcurrentContext();
context.set("age", 25);
context.set("gender", "male");

String formula = "if($gender=='male'){500}else{1000}";
PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
    new Source(formula),
    "FeeCalc",
    new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
    Thread.currentThread().getContextClassLoader());

float v1 = ((Number) calculator.apply(context)).floatValue(); // 500.0
context.set("gender", "female");
float v2 = ((Number) calculator.apply(context)).floatValue(); // 1000.0
```

#### 6.1.5 TinyExpressionsExecutor

**クラス**: `org.unlaxer.tinyexpression.instances.TinyExpressionsExecutor`

クラス名は複数形（`Expressions`）であることに注意（`TinyExpressionExecutor` ではない）。

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
| `tenantId` | テナント ID（`TenantID.create(long id)` で生成） |
| `context` | 計算コンテキスト（変数の入出力） |
| `resultConsumer` | 各式の結果を受け取るコンシューマ |
| `cache` | FormulaInfo キャッシュ |
| `sortOrder` | 実行順序（通常: `Comparator.comparingInt(Calculator::dependsOnByNestLevel)`） |
| `filter` | 実行対象のフィルタ（`calculator -> true` ですべて実行） |
| `classLoader` | クラスローダー |

**フル使用例**:

```java
FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
    "siteId",
    info -> info.calculatorName);

FileBaseTinyExpressionInstancesCache cache =
    new FileBaseTinyExpressionInstancesCache(
        Path.of("src", "main", "resources", "formula-root"),
        fields);

ResultConsumer resultConsumer = new ResultConsumer() {
    @Override
    public void accept(CalculationContext c, Calculator calc, FormulaInfo info, Number result) {
        info.getValue("var").ifPresent(name -> c.set(name, result));
    }
    @Override
    public void accept(CalculationContext c, Calculator calc, FormulaInfo info, String result) {
        info.getValue("var").ifPresent(name -> c.set(name, result));
    }
    @Override
    public void accept(CalculationContext c, Calculator calc, FormulaInfo info, Boolean result) {
        info.getValue("var").ifPresent(name -> c.set(name, result));
    }
    @Override
    public void accept(CalculationContext c, Calculator calc, FormulaInfo info, Object result) {
        info.getValue("var").ifPresent(name -> c.setObject(name, result));
    }
};

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

System.out.println("executed: " + results.size());
System.out.println("finalScore: " + ctx.get("finalScore"));
```

#### 6.1.6 ResultConsumer

**インタフェース**: `org.unlaxer.tinyexpression.instances.ResultConsumer`

| メソッド | 結果型 |
|---------|--------|
| `accept(CalculationContext, Calculator, FormulaInfo, Number)` | 数値結果 |
| `accept(CalculationContext, Calculator, FormulaInfo, String)` | 文字列結果 |
| `accept(CalculationContext, Calculator, FormulaInfo, Boolean)` | 真偽値結果 |
| `accept(CalculationContext, Calculator, FormulaInfo, Object)` | オブジェクト結果 |

#### 6.1.7 FileBaseTinyExpressionInstancesCache

**クラス**: `org.unlaxer.tinyexpression.instances.FileBaseTinyExpressionInstancesCache`

```java
new FileBaseTinyExpressionInstancesCache(
    Path rootDir,                          // ルートディレクトリ
    FormulaInfoAdditionalFields fields     // 追加フィールド設定
)
```

テナントごとの `formulaInfo.txt` をロードし、テナント ID をキーにキャッシュする。

#### 6.1.8 CalculationResult

**クラス**: `org.unlaxer.tinyexpression.instances.CalculationResult`

`TinyExpressionsExecutor.execute()` の戻り値。各式の実行結果（`Calculator`, `FormulaInfo`, 結果値）への参照を保持する。

### 6.2 FormulaInfo DSL

FormulaInfo は `formulaInfo.txt` ファイルに記述する。

#### 6.2.1 ディレクトリ構成

```
<root>/
  <tenant-id-1>/formulaInfo.txt
  <tenant-id-2>/formulaInfo.txt
```

`FileBaseTinyExpressionInstancesCache` がこのディレクトリ構成を期待する。

#### 6.2.2 ブロック構造

各式定義は `---END_OF_PART---` で区切る（MUST）。

```text
key1:value1
key2:value2
# コメント行（# で始まる）
formula:
式テキスト（複数行可）
---END_OF_PART---
```

- キーと値はコロン `:` で区切る
- `#` で始まる行はコメント
- `formula:` 行の次行から次の区切り行までが式本文

#### 6.2.3 FormulaInfo キー仕様

**必須キー**:

| キー | 型 | 説明 |
|------|-----|------|
| `calculatorName` | string | 式の一意識別子（MUST） |
| `formula` | string（複数行） | 式本文（MUST） |

**バックエンド設定**:

| キー | 型 | 説明 |
|------|-----|------|
| `executionBackend` | string | 実行バックエンド名（正式名またはエイリアス） |
| `backend` | string | `executionBackend` のエイリアス |

**型設定**:

| キー | 型 | 説明 |
|------|-----|------|
| `resultType` | string | 戻り値型: `string`, `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, または FQCN |
| `numberType` | string | 数値演算のデフォルト型 |

**依存関係**:

| キー | 型 | 説明 |
|------|-----|------|
| `dependsOn` | string（カンマ区切り） | 依存する `calculatorName` の列挙 |

**変数バインディング**:

| キー | 型 | 説明 |
|------|-----|------|
| `var` | string | 計算結果を格納する変数名（`ResultConsumer` で参照） |

**メタ情報**:

| キー | 型 | 説明 |
|------|-----|------|
| `tags` | string（カンマ区切り） | タグ（例: `NORMAL`, `PROMOTION`） |
| `description` | string | 式の説明 |
| `field` | string | フィールド名（実運用向け） |
| `checkKind` | string | チェック種別（実運用向け） |
| `siteId` | string/long | サイト ID（テナント識別に使用） |

#### 6.2.4 バックエンドエイリアス

| エイリアス | バックエンド |
|-----------|------------|
| `token` | `JAVA_CODE` |
| `legacy-astcreator`, `ootc` | `JAVA_CODE_LEGACY_ASTCREATOR` |
| `ast` | `AST_EVALUATOR` |
| `dsl-javacode` | `DSL_JAVA_CODE` |
| `p4-ast`, `p4-ast-evaluator` | `P4_AST_EVALUATOR` |
| `p4-dsl-javacode`, `p4-dsl-java-code` | `P4_DSL_JAVA_CODE` |

#### 6.2.5 formulaInfo.txt の例（マルチフォーミュラ）

```text
tags:NORMAL
description:base score — age check
siteId:69
calculatorName:baseScore
var:baseScore
resultType:float
formula:
if($age >= 20){100}else{0}
---END_OF_PART---

tags:NORMAL
description:bonus — gender dependent
siteId:69
calculatorName:bonusScore
dependsOn:baseScore
var:bonusScore
resultType:float
formula:
match{
  $gender == 'female' -> 500,
  default -> 200
}
---END_OF_PART---

tags:NORMAL
description:final score with bonus
siteId:69
calculatorName:finalScore
dependsOn:baseScore,bonusScore
var:finalScore
resultType:float
formula:
$baseScore + $bonusScore
---END_OF_PART---
```

#### 6.2.6 バックエンド選択の解決順序

1. **式レベルオーバーライド**: `FormulaInfo` の `executionBackend` または `backend` フィールド
2. **グローバルデフォルト**: `FormulaInfoAdditionalFields.setExecutionBackend()`（デフォルト: `JAVA_CODE`）
3. **実装割り当て**: `CalculatorCreatorRegistry.forBackend(ExecutionBackend)`

**関連クラス**:

| クラス | パッケージ | 役割 |
|--------|---------|------|
| `ExecutionBackend` | `org.unlaxer.tinyexpression.runtime` | バックエンド enum 定義 |
| `CalculatorCreatorRegistry` | `org.unlaxer.tinyexpression.loader.model` | バックエンド → Calculator 生成器のレジストリ |
| `FormulaInfoParser` | `org.unlaxer.tinyexpression.loader` | FormulaInfo のパースとバックエンドフィールド解析 |

### 6.3 パリティ契約

すべての 6 バックエンドは同じ入力に対して等価な値を返す（MUST）。

| 要件 | 強度 |
|------|------|
| 6 バックエンドがサポートコーパスに対して等価な値を返す | MUST |
| `AST_EVALUATOR` がサポート済み式で `javacode-fallback` を回避する | MUST |
| `P4_AST_EVALUATOR`, `P4_DSL_JAVA_CODE` が他 4 バックエンドと等価な値を返す | MUST |
| P4 文法ギャップの式はフォールバックパスを使用する | 既知例外 |
| バックエンド名を再利用してはならない | MUST NOT |

**DAP パリティ変数**（デバッグモード時、`parity.*` として公開）:

| 変数 | 説明 |
|------|------|
| `parity.JAVA_CODE` | `JAVA_CODE` バックエンドの結果 |
| `parity.JAVA_CODE_LEGACY_ASTCREATOR` | `JAVA_CODE_LEGACY_ASTCREATOR` バックエンドの結果 |
| `parity.AST_EVALUATOR` | `AST_EVALUATOR` バックエンドの結果 |
| `parity.DSL_JAVA_CODE` | `DSL_JAVA_CODE` バックエンドの結果 |
| `parity.P4_AST_EVALUATOR` | `P4_AST_EVALUATOR` バックエンドの結果 |
| `parity.P4_DSL_JAVA_CODE` | `P4_DSL_JAVA_CODE` バックエンドの結果 |
| `parity.equalAll` | 6 バックエンドすべてが一致する場合 `true` |

### 6.4 バックエンド変更ガイドライン

1. **構文 / ランタイム拡張時**: まず `JAVA_CODE` と `AST_EVALUATOR` を更新する
2. `JAVA_CODE_LEGACY_ASTCREATOR` の変更は最小限に留める（凍結状態）
3. **バックエンド名を再利用してはならない**（MUST NOT）
4. **バックエンド動作契約が変更される場合**: `TINYEXPRESSION-BACKEND-CONTRACT.md` とパリティテストを同時に更新する（MUST）

---

## 7. UI

### 7.1 N/A

TinyExpression 本体は UI を持たない。

### 7.2 tinyexpression-ide（別リポジトリ）

IDE / エディタサポートは以下の別リポジトリで提供される:

- **リポジトリ**: [tinyexpression-group/tinyexpression-ide](https://github.com/tinyexpression-group/tinyexpression-ide)
- **プラットフォーム**: VS Code 拡張（LSP + DAP）

**LSP 機能**:

- `.tinyexp` ファイルのシンタックスハイライト
- パース失敗の診断（`ParseFailureDiagnostics`）
- セマンティックトークン（P4 AST `instanceof` ベースディスパッチ）
- 補完（P4 AST ノード型に基づく）
- ホバー（変数の `description` フィールド、型情報）
- CodeAction: `if` ↔ ternary 双方向変換（v1.4.7 以降）
- FormulaInfo LSP: `dependsOn` バリデーション、メタデータ補完（v1.4.7-1.4.9 以降）

**DAP 機能**:

- `.tinyexp` ファイルのデバッグ（F5 起動）
- 変数インスペクション
- ブレークポイント
- 6 バックエンド同時実行と `parity.*` 変数の公開
- `_tinyExecutionImplementation` 等のランタイムマーカーの可視化

**LSP/DAP 接続フロー**（参考）:

```
.tinyexp ファイル編集
    │
    ▼
TinyExpressionP4LanguageServerExt（LSP サーバー）
    ├── diagnostics（ParseFailureDiagnostics）
    ├── セマンティックトークン（P4 AST instanceof ディスパッチ）
    └── 補完 / ホバー（P4 AST ノード型）

.tinyexp デバッグ（F5）
    │
    ▼
TinyExpressionP4DebugAdapterExt（DAP アダプタ）
    ├── 6 バックエンド全実行
    └── debugger に parity.* 変数を公開
```

**なぜ P4 スタックが LSP/DAP の参照実装か**:

- P4 AST の `instanceof` ベースのディスパッチにより、コンパイル時の網羅性チェックが可能
- 正規表現ベースのアドホックなマッチングを使用しない
- 新しい AST ノード型追加時、`switch` 式でコンパイルエラーが発生し、サイレントな実行時失敗を防げる

---

## 8. 設定

### 8.1 JavaCodeBlockPolicy によるオプトアウト（v1.4.11 以降）

**クラス**: `org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeBlockPolicy`

```java
// アプリケーション起動時（Calculator インスタンス生成前）に一度設定
JavaCodeBlockPolicy.setEnabled(false);

// テスト後のリセット
JavaCodeBlockPolicy.reset();
```

| メソッド | 説明 |
|---------|------|
| `isEnabled()` | 現在のポリシーを返す（デフォルト: `true`） |
| `setEnabled(boolean)` | コードブロック実行の有効 / 無効を設定 |
| `reset()` | デフォルト状態（有効）に戻す（テスト用） |

**実装詳細**:

- `AtomicBoolean` を使用したスレッドセーフな実装
- `false` に設定した場合、`createJavaFromCodedBlock` は空リストを返す
- コードブロック以外の式評価（演算、変数参照、if/match 等）は影響を受けない
- `setEnabled(false)` 以前に構築済みの `Calculator` インスタンスは影響を受けない

### 8.2 グローバルデフォルトバックエンドの設定

`FormulaInfoAdditionalFields.setExecutionBackend()` でグローバルデフォルトを設定する。

```java
FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
    "siteId",
    info -> info.calculatorName);

// デフォルト（JAVA_CODE）
fields.setExecutionBackend(ExecutionBackend.JAVA_CODE);

// P4 を優先する場合
fields.setExecutionBackend(ExecutionBackend.P4_AST_EVALUATOR);
```

### 8.3 per-formula バックエンドオーバーライド

`formulaInfo.txt` の `executionBackend` または `backend` キーで式ごとに上書きできる。

```text
calculatorName:myFormula
executionBackend:P4_AST_EVALUATOR
formula:
$a + $b
---END_OF_PART---

calculatorName:legacyFormula
backend:token
formula:
if($x > 0){$x}else{0}
---END_OF_PART---
```

### 8.4 add-opens 設定

テスト / ランタイムでリフレクションを使う場合、Maven Surefire プラグインに `add-opens` の追加が必要。`pom.xml` には設定済み。

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

または `$MAVEN_OPTS`:

```bash
export MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"
```

### 8.5 推奨プロダクション設定

1. グローバルデフォルトバックエンドは `JAVA_CODE` のまま維持（高スループット、JIT 最適化）
2. `P4_AST_EVALUATOR` を特定の式で試験し、`_tinyP4ParserUsed=true` でカバレッジを確認
3. `parity.equalAll` を DAP デバッグで確認後、per-formula で `P4_AST_EVALUATOR` に切り替え
4. `JAVA_CODE_LEGACY_ASTCREATOR` は変更しない（不変のリグレッション基準として扱う）
5. 信頼できないソースから式を受け取る場合、`JavaCodeBlockPolicy.setEnabled(false)` を設定

---

## 9. 依存

### 9.1 実行時依存（pom.xml より）

| グループ ID | アーティファクト ID | バージョン | 用途 |
|------------|-------------------|----------|------|
| `org.unlaxer` | `unlaxer-common` | **3.0.2** | パーサーコンビネータランタイム（レガシーパーサーのベース） |
| `org.unlaxer` | `unlaxer-dsl` | **3.0.2** | UBNF 文法からのパーサー自動生成（P4 スタック） |
| `org.jetbrains` | `annotations` | `24.0.1` | `@NotNull` 等のアノテーション |
| `net.arnx` | `jsonic` | `1.3.10` | JSON シリアライゼーション |

### 9.2 テスト依存

| グループ ID | アーティファクト ID | バージョン |
|------------|-------------------|----------|
| `junit` | `junit` | `4.13.2` |

### 9.3 unlaxer-common との関係

unlaxer-common はレガシーパーサースタックのランタイムライブラリである。

| 機能 | unlaxer-common での提供 | TinyExpression での使用 |
|------|----------------------|------------------------|
| パーサーコンビネータ API | `org.unlaxer.parser.Parser` | `TinyExpressionParser` 等の基底 |
| コンビネータプリミティブ | `BasicCombinator` (`seq`, `choice`, `zeroOrMore` 等) | 各パーサーの実装 |
| ParseTree ノード | `TokenNode`, `Token` | AST 評価の入力 |
| `NoneChildCollectingParser` | v3.0.2 で移行済み API | v1.4.10 で対応 |

**バージョン移行履歴**:

- v1.4.10: unlaxer-common 2.8.0 対応（`NoneChildCollectingParser` 移行）
- v1.4.11 / 9b166fc: unlaxer-common/dsl **3.0.2** に移行（`StringSource` API 対応 + `validateWithWarnings`）

### 9.4 unlaxer-dsl との関係

unlaxer-dsl は P4 パーサースタックの生成ツールである。

| 機能 | unlaxer-dsl での提供 | TinyExpression での使用 |
|------|---------------------|------------------------|
| UBNF 文法からのパーサー生成 | コード生成 | `TinyExpressionP4Parsers` 生成 |
| P4 AST（sealed interface 階層） | 型安全 AST ノード群生成 | `TinyExpressionP4Mapper` が生成 |
| P4 AST マッパー | マッパー生成 | `ParseTree → P4 AST` 変換 |
| 文法バリデーション | `--validate`、`--validate-parser-ir` | CI でのバリデーション |
| Railroad 図生成 | `--railroad` | `docs/railroad/` への出力 |

**P4 文法ファイル**:

| ファイル | 行数 | 説明 |
|---------|------|------|
| `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` | 321 行 | 拡張版（アクティブ） |
| `docs/ubnf/tinyexpression-p4-draft.ubnf` | 239 行 | 初期ドラフト |

**P4 生成物の配置**:

```
target/generated-sources/tinyexpression-p4/runtime/
    org/unlaxer/tinyexpression/generated/p4/
        TinyExpressionP4Parsers.java
        TinyExpressionP4Mapper.java
        TinyExpressionNode.java （sealed interface）
        IfExpr.java, BinaryExpr.java, ... （具体ノード）
```

### 9.5 Maven Central への依存追加

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>tinyExpression</artifactId>
  <version>1.4.11</version>
</dependency>
```

---

## 10. 非機能要件

### 10.1 パースキャッシュ

| キャッシュ | 詳細 |
|-----------|------|
| `FileBaseTinyExpressionInstancesCache` | テナント ID をキーに `List<Calculator>` をキャッシュ。初回アクセス時にロード（遅延ロード） |
| `IncrementalParseCache` | LSP インクリメンタルパースキャッシュ（v1.4.9 以降）。エディタ編集中のリパース最適化 |
| `MemoryClassLoader` | javac でコンパイル済みのクラスを保持。GC まで有効 |

**推奨プラクティス**:

- `JavaCodeCalculatorV3`（`JAVA_CODE` バックエンド）は構築時に javac を実行するため、コスト高。インスタンスの再利用が必須
- `Calculator.apply()` はステートレスで軽量。スレッド間で共有可能
- `FileBaseTinyExpressionInstancesCache` は 1 アプリケーションインスタンスで 1 回構築し、再利用する

### 10.2 評価レイテンシ特性

| バックエンド | 初回レイテンシ | 2 回目以降 | 特記 |
|------------|------------|-----------|------|
| `JAVA_CODE` | 高（javac コンパイル） | 低（JIT 最適化後は最速） | `10^9 tx/month` 規模の本番ワークロードに適合 |
| `JAVA_CODE_LEGACY_ASTCREATOR` | 高 | 低 | 凍結状態。新規使用推奨せず |
| `AST_EVALUATOR` | 低 | 中 | コンパイルオーバーヘッドなし。軽量デプロイ向け |
| `DSL_JAVA_CODE` | 中〜高 | 中〜低 | ネイティブエミッタ使用時は JAVA_CODE に近い |
| `P4_AST_EVALUATOR` | 低 | 中 | P4 文法ギャップ時はフォールバックで追加レイテンシ |
| `P4_DSL_JAVA_CODE` | 中〜高 | 中〜低 | P4 パース + DSL Java 生成 + javac |

### 10.3 スレッドセーフティ

| クラス / メソッド | スレッドセーフティ | 根拠 |
|-----------------|----------------|------|
| `CalculationContext.newConcurrentContext()` | スレッドセーフ（MUST） | `ConcurrentHashMap` ベース |
| `Calculator.apply(context)` | スレッドセーフ（MUST） | ステートレス |
| `FileBaseTinyExpressionInstancesCache` | スレッドセーフ | 読み取りキャッシュ |
| `JavaCodeBlockPolicy` | スレッドセーフ | `AtomicBoolean` |
| `JavaCodeCalculatorV3` の構築 | スレッド非安全（concurrent 推奨せず） | javac 呼び出しを含む |

### 10.4 メモリ管理

- コンパイル済みバイトコードは `MemoryClassLoader` の `ByteArrayJavaFileObject` として保持される
- `MemoryClassLoader` がガベージコレクションされると、コンパイル済みクラスも破棄される
- ディスクへの永続化は行われない（Java コードブロックを含む全クラス）
- `FileBaseTinyExpressionInstancesCache` はテナントごとに `Calculator` リストを保持するため、テナント数に比例したメモリを消費する

### 10.5 P4 文法カバレッジとパフォーマンス

P4 文法ギャップが多い場合、`P4_AST_EVALUATOR` のフォールバック率が高くなりパフォーマンスが悪化する。`_tinyP4ParserUsed` マーカーを監視してフォールバック率を測定し、P4 UBNF の拡張計画を立てることを推奨する。

---

## 11. テスト戦略

### 11.1 パリティテスト（6 バックエンド）

パリティテストは 6 バックエンドの等価性を保証する中核的なテスト戦略である。同一の式・コンテキストに対してすべてのバックエンドが等価な結果を返すことを継続的に検証する。

| テストクラス | パッケージ | 説明 |
|------------|---------|------|
| `P4BackendParityTest` | `...tinyexpression.p4` | 6 バックエンド全比較。P4 パース可否マーカーも検証 |
| `ThreeExecutionBackendParityTest` | `...evaluator.ast` | 主要 4 バックエンド比較。サポートコーパス 20 件以上 + リグレッションコーパス 30 件以上 |
| `ThreeExecutionBackendExtractedCorpusParityTest` | `...evaluator.ast` | プロダクション式からの抽出テスト |
| `AstEvaluatorBackendParityTest` | `...evaluator.ast` | `AST_EVALUATOR` 単体のバックエンド比較 |
| `DslJavaCodeGenerationParityTest` | `...evaluator.javacode` | `JAVA_CODE` と `DSL_JAVA_CODE` の生成 Java コード等価性検証 |
| `DslJavaCodeGenerationExtractedParityTest` | `...evaluator.javacode` | プロダクション式での生成コード等価性検証 |

### 11.2 テスト構造

```
src/test/java/
    org/unlaxer/tinyexpression/
        p4/
            P4BackendParityTest.java
        evaluator/
            ast/
                ThreeExecutionBackendParityTest.java
                ThreeExecutionBackendExtractedCorpusParityTest.java
                AstEvaluatorBackendParityTest.java
                AstEvaluatorTokenLiteralFallbackTest.java
                AstEvaluatorStringGeneratedPathTest.java
                GeneratedP4NumberAstEvaluatorVariableTest.java
                BackendSpeedComparisonTest.java
            javacode/
                DslJavaCodeGenerationParityTest.java
                DslJavaCodeGenerationExtractedParityTest.java
        parser/
            FormulaParserTest.java
            NumberExpressionParserTest.java
            StringExpressionParserTest.java
            BooleanExpressionParserTest.java
            IfExpressionParserTest.java
            NumberMatchExpressionParserTest.java
            StringMatchExpressionParserTest.java
            BooleanMatchExpressionParserTest.java
            VariableParserTest.java
            NakedVariableParserTest.java
            ExclusiveNakedVariableParserTest.java
            MethodsParserTest.java
            MethodInvocationParserTest.java
            javalang/
                TripleBackTickParserTest.java
                CodeParserTest.java
                AnnotationsParserTest.java
                ...
        instances/
            TinyExpressionsExecutorTest.java
            ComparatorTest.java
        loader/
            FormulaInfoParserTest.java
            FormulaInfoExecutionBackendSelectionTest.java
            model/
                FormulaInfoListTest.java
        formatter/
            FormatterTest.java
```

### 11.3 ParityTest の設計方針

#### P4BackendParityTest の構造

```java
// P4 文法でパース可能なフォーミュラ
private static final List<String> P4_PARSEABLE_NUMBER_FORMULAS = List.of(
    "1",
    "42",
    "1+2",
    "3*4-5",
    "(10-2)*(7-3)",  // v1.4.11 でバグ修正済み
    "sin(30)",
    "max(3,7)"
);

// P4 文法でパースできないフォーミュラ（フォールバック検証）
private static final List<String> NON_P4_PARSEABLE_FORMULAS = List.of(
    // P4 UBNF でカバーされていない構文
);
```

**検証内容**:

1. 6 バックエンドが等価な値を返すこと
2. `_tinyP4ParserUsed=true` が P4 パース可能なフォーミュラに設定されること
3. `_tinyP4ParserUsed=false` が P4 非対応フォーミュラに設定され、グレースフルフォールバックが機能すること

#### ThreeExecutionBackendParityTest のサポートコーパス（代表例）

```text
1+(8/4)
3*4-5
(10-2)*(7-3)
if   (true){1}else{2}
/*head*/if(true){1}else{2}
if/*c*/(true){1}else{2}
if(true){'ok'}else{'ng'}
match{1==1->3,default->5}
match{1==1->'A',default->'B'}
match{1==0->false,default->true}
'payload'
$payload
call provide()\nobject provide(){\n'ok'\n}
call identity('payload')\nobject identity($payload as object){\n$payload\n}
call identity(1)\nfloat identity($amount as number){\n$amount\n}
var $amount as number set if not exists 100 description='amount';\n...
var $name as string set if not exists 'neo' description='name';\n$name
$firstName + ' ' + $lastName
$message[0:3]
```

### 11.4 パリティテスト方針

1. **新機能追加時**: `JAVA_CODE` と `AST_EVALUATOR` を先に更新し、パリティテストのコーパスに追加する（MUST）
2. **バックエンド動作変更時**: パリティテストを同時に更新する（MUST）
3. **P4 文法拡張時**: `P4BackendParityTest` に P4 パース可能な新しいコーパスを追加する
4. **プロダクション移行前**: `parity.equalAll` が `true` であることを確認する（DAP デバッグ）

### 11.5 2 段階パリティ検証

`ThreeExecutionBackendParityTest` は 2 段階の検証を行う:

1. **サポートコーパス**: `AST_EVALUATOR` での `javacode-fallback` を禁止。すべてのバックエンドで等価な値を要求
2. **リグレッションコーパス**: `javacode-fallback` を許容。ただし `JAVA_CODE` / `AST_EVALUATOR` / `DSL_JAVA_CODE` での値パリティを要求

抽出コーパステストはプロダクション式から自動抽出し、AST 非フォールバックの最低閾値を設ける。

### 11.6 個別パーサーテスト

各パーサーは対応する `*Test.java` クラスで単体テストされる:

| テストクラス | 対象パーサー |
|------------|------------|
| `FormulaParserTest` | `TinyExpressionParser`（式全体） |
| `NumberExpressionParserTest` | `NumberExpressionParser`（数値式） |
| `IfExpressionParserTest` | `IfExpressionParser`（if/else） |
| `NumberMatchExpressionParserTest` | `NumberMatchExpressionParser`（数値 match） |
| `TripleBackTickParserTest` | `TripleBackTickParser`（Java コードブロック） |
| `AnnotationsParserTest` | `AnnotationsParser`（アノテーション） |
| `MethodInvocationParserTest` | `MethodInvocationParser`（メソッド呼び出し） |

### 11.7 既知のテスト上の制限・例外

- P4 文法でカバーされていない構文を使用する式は `_tinyP4ParserUsed=false` となり、フォールバックパスを経由する（既知例外）
- `JAVA_CODE_LEGACY_ASTCREATOR` は凍結状態のため、新機能のパリティ検証対象外
- `BackendSpeedComparisonTest` はパリティではなくパフォーマンス特性の把握が目的
- `BigDecimal` / `BigInteger` を使った混合算術のパリティは限定的

---

## 12. デプロイ / 運用

### 12.1 Maven Central への公開

TinyExpression は **Maven Central (OSSRH)** で公開される。

**Maven 座標**:

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>tinyExpression</artifactId>
  <version>1.4.11</version>
</dependency>
```

**ライセンス**: MIT

**Developer**:

```xml
<developer>
  <name>opaopa6969</name>
  <email>opaopa6969@gmail.com</email>
  <id>opaopa6969</id>
  <timezone>-9</timezone>
</developer>
```

**SCM**:

```xml
<scm>
  <connection>scm:https://opaopa6969@bitbucket.org/opaopa6969/unlaxer.git</connection>
  <url>scm:https://opaopa6969@bitbucket.org/opaopa6969/unlaxer.git</url>
</scm>
```

### 12.2 リリース手順

- リリース詳細は `ReleaseToOSSRH.md` を参照
- CI/CD は `bitbucket-pipelines.yml` で定義
- `mvnw` / `mvnw.cmd` のラッパースクリプトを使用
- バージョンは `pom.xml` の `<version>` で管理
- OSSRH 公開時は GPG 署名必須

### 12.3 バージョン履歴

| バージョン | リリース日 | 主な変更内容 |
|----------|----------|------------|
| **1.4.11** | 2026-04-21 | DSL バックエンド nested paren 乗算バグ修正（`(10-2)*(7-3)` 全 6 バックエンドパリティ達成）、`JavaCodeBlockPolicy` opt-out 追加（v1.4.11）、unlaxer-common/dsl 3.0.2 移行 |
| 1.4.10 | 2026-02-26 | P4TypedAstEvaluator を PRIMARY 昇格（ADR-001）、DAP デフォルト実行モードを `ast-evaluator` に変更、unlaxer-common 2.8.0 移行 |
| 1.4.9 | 2026-02-25 | 文字列スライス（最後の機能ギャップ）、全 6 バックエンドフルパリティ達成、FormulaInfo LSP Phase 2、インクリメンタルパースキャッシュ LSP 統合 |
| 1.4.8 | 2026-02-24 | `MethodInvocation` + `External` 呼び出しを `P4TypedAstEvaluator` でネイティブサポート（フォールバック排除）、バックエンドカバレッジマトリクス追加 |
| 1.4.7 | 2026-02-23 | 文字列述語（`startsWith`, `endsWith`, `contains`, `isPresent`）、P4 フォールバックロギング、LSP CodeAction（if ↔ ternary）、FormulaInfo LSP Phase 1 |
| 1.4.6 | 2026-02-22 | `ArgumentExpression`、文字列ドットメソッドチェーン、三項演算子（`condition ? then : else`）、文字列メソッド群（`toUpperCase`, `toLowerCase`, `trim`, `length`） |

完全な変更履歴: `CHANGELOG.md` を参照。

### 12.4 後方互換性ポリシー

| 変更種別 | ポリシー |
|---------|---------|
| `JavaCodeBlockPolicy` デフォルト値 | `true`（後方互換維持） |
| `ExecutionBackend` enum 値 | 再利用禁止（MUST NOT）。削除禁止 |
| `Calculator` API（`apply`, `dependsOnByNestLevel`） | 後方互換維持 |
| `JAVA_CODE_LEGACY_ASTCREATOR` | 凍結。最小限の互換性パッチのみ許容 |
| FormulaInfo キー | 既存キーの削除禁止 |

### 12.5 監視・観測性

本番運用において以下のマーカーを監視することを推奨する:

| マーカー / 指標 | 用途 |
|--------------|------|
| `_tinyExecutionBackend` | どのバックエンドが実行されたか（バックエンド分布） |
| `_tinyExecutionImplementation` | フォールバック発生の検出（`legacy-javacode-bridge` 等） |
| `_tinyP4ParserUsed` | P4 文法カバレッジの実測値（フォールバック率） |
| `parity.equalAll` | DAP デバッグでの 6 バックエンド等価確認 |

**フォールバック率の改善手順**:

1. `_tinyP4ParserUsed=false` となっている式を特定
2. 式の構文と P4 UBNF のカバレッジを比較（`docs/feature-parity-diff.md` を参照）
3. P4 UBNF を拡張して該当構文をカバー
4. `P4BackendParityTest` に新しいコーパスを追加してパリティを検証

### 12.6 ロールアウト戦略

プロダクションで `P4_AST_EVALUATOR` に段階的移行する場合:

1. グローバルデフォルトは `JAVA_CODE` を維持
2. 低リスクの式（単純算術、変数参照）から per-formula で `P4_AST_EVALUATOR` に切り替え
3. `_tinyP4ParserUsed=true` と `parity.equalAll=true` を本番で確認
4. カバレッジ拡大後に次の式グループへ
5. P4 文法が全機能をカバーした時点でグローバルデフォルトを変更

### 12.7 既知の制限事項（本バージョン）

| 制限 | 詳細 |
|------|------|
| P4 文法カバレッジ | 128 機能中 30 が HAND-ONLY（P4 未対応）。主にサイドエフェクト式・ドメイン関数・文字列述語 |
| `BigDecimal`/`BigInteger` | 式言語での直接演算サポートは限定的 |
| `JAVA_CODE_LEGACY_ASTCREATOR` | 凍結状態。新機能追加対象外 |
| `JavaCodeBlockPolicy` | Calculator 構築後の変更は既存インスタンスに影響しない |
| P4 フォールバック | P4 文法ギャップの式はフォールバックパスを経由し、追加レイテンシが発生する |
| 循環依存エラーメッセージ | 検出はされるが、エラーメッセージの詳細は限定的 |

---

## 付録 A: 言語仕様クイックリファレンス

### A.1 式の BNF 概要（非形式的）

```
formula      ::= codeblock* imports varDecls annotations expression methods
expression   ::= numberExpr | stringExpr | booleanExpr | objectExpr | methodInvocation
numberExpr   ::= term (('+' | '-') term)*
term         ::= factor (('*' | '/') factor)*
factor       ::= literal | variable | '(' numberExpr ')' | ifExpr | matchExpr
               | mathFunc | methodCall
ifExpr       ::= 'if' '(' boolExpr ')' '{' expression '}' 'else' '{' expression '}'
ternaryExpr  ::= boolExpr '?' expression ':' expression
matchExpr    ::= 'match' '{' (caseExpr ',')+ defaultCase '}'
caseExpr     ::= boolExpr '->' expression
defaultCase  ::= 'default' '->' expression
varDecl      ::= ('var' | 'variable') '$' name ('as' type)? ('set' ('if' 'not' 'exists')? expr)? ('description' '=' string)? ';'
```

### A.2 組み込み関数一覧

**数学関数（14 種）**:

```text
sin(x)  cos(x)  tan(x)  sqrt(x)  pow(x,y)  log(x)  exp(x)
abs(x)  round(x)  ceil(x)  floor(x)
min(x,y,...) max(x,y,...) random()
```

**文字列関数（関数形式）**:

```text
toUpperCase(s)  toLowerCase(s)  trim(s)  length(s)  toNum(s)  toNum(s,default)
```

**文字列メソッド（ドット形式）**:

```text
$s.startsWith(str)  $s.endsWith(str)  $s.contains(str)  $s.in(str,...)
$s.isPresent()
$s.toUpperCase()  $s.toLowerCase()  $s.trim()  $s.length()  （P4 UBNF のみ）
```

**日時関数**:

```text
inTimeRange($ts, from, to)  inDayTimeRange($ts, fromDay, fromHour, toDay, toHour)
```

**論理否定**:

```text
not(boolExpr)
```

### A.3 ExpressionTypes 型対応表

| FormulaInfo resultType | ExpressionTypes enum | Java 型 |
|------------------------|---------------------|---------|
| `float` (デフォルト) | `_float` / `number` | `Float` |
| `double` | `_double` | `Double` |
| `long` | `_long` | `Long` |
| `int` | `_int` | `Integer` |
| `short` | `_short` | `Short` |
| `byte` | `_byte` | `Byte` |
| `string` | `string` | `String` |
| `boolean` | `_boolean` | `Boolean` |
| `object` / FQCN | `object` | `Object` |

---

## 付録 B: 主要クラスとパッケージ構成

### B.1 パッケージ構成

```
org.unlaxer.tinyexpression
    ├── CalculationContext.java          (I/F + 実装)
    ├── Calculator.java                  (I/F)
    ├── PreConstructedCalculator.java    (abstract)
    ├── Source.java                      (式テキストラッパー)
    ├── ExpressionTypes.java             (型 enum)
    ├── parser/
    │   ├── TinyExpressionParser.java    (ルートパーサー)
    │   ├── NumberExpressionParser.java
    │   ├── StringExpressionParser.java
    │   ├── BooleanExpressionParser.java
    │   ├── IfExpressionParser.java
    │   ├── NumberMatchExpressionParser.java
    │   ├── StringMatchExpressionParser.java
    │   ├── BooleanMatchExpressionParser.java
    │   ├── VariableParser.java
    │   ├── MethodsParser.java
    │   ├── MethodInvocationParser.java
    │   ├── javalang/                    (Java コードブロック関連)
    │   │   ├── TripleBackTickParser.java
    │   │   ├── CodeParser.java
    │   │   ├── AnnotationsParser.java
    │   │   └── ...
    │   └── numbertype/                  (具体数値型パーサー)
    ├── evaluator/
    │   ├── javacode/
    │   │   ├── JavaCodeCalculatorV3.java
    │   │   ├── JavaCodeBlockPolicy.java
    │   │   ├── SpecifiedExpressionTypes.java
    │   │   ├── DslJavaCodeCalculator.java
    │   │   └── legacy/
    │   │       ├── LegacyAstCreatorJavaCodeCalculator.java
    │   │       └── LegacyOperatorOperandTreeCreator.java
    │   ├── ast/
    │   │   └── AstEvaluatorCalculator.java
    │   └── p4/
    │       ├── P4AstEvaluatorCalculator.java
    │       └── P4DslJavaCodeCalculator.java
    ├── instances/
    │   ├── TinyExpressionsExecutor.java
    │   ├── ResultConsumer.java
    │   ├── FileBaseTinyExpressionInstancesCache.java
    │   └── CalculationResult.java
    ├── loader/
    │   ├── FormulaInfoParser.java
    │   ├── model/
    │   │   ├── FormulaInfo.java
    │   │   ├── FormulaInfoAdditionalFields.java
    │   │   ├── FormulaInfoList.java
    │   │   └── CalculatorCreatorRegistry.java
    │   └── ...
    └── runtime/
        └── ExecutionBackend.java

org.unlaxer.compiler
    ├── MemoryClassLoader.java
    ├── MemoryJavaFileManager.java
    └── CompileContext.java

org.unlaxer.tinyexpression.generated.p4   (自動生成)
    ├── TinyExpressionP4Parsers.java
    ├── TinyExpressionP4Mapper.java
    └── (sealed interface AST ノード群)
```

### B.2 主要インタフェース / クラスの責務

| クラス | 責務 |
|--------|------|
| `TinyExpressionParser` | 式文字列全体のエントリポイントパーサー |
| `JavaCodeCalculatorV3` | `JAVA_CODE` バックエンドの Calculator 実装。コンストラクタで javac を実行 |
| `AstEvaluatorCalculator` | `AST_EVALUATOR` バックエンド。4 段フォールバックチェーンを管理 |
| `P4AstEvaluatorCalculator` | `P4_AST_EVALUATOR` バックエンド。P4 パース + 型安全 AST 評価 |
| `FileBaseTinyExpressionInstancesCache` | テナントごとの Calculator リストをキャッシュ |
| `TinyExpressionsExecutor` | 依存関係付きマルチフォーミュラ実行エンジン |
| `FormulaInfoParser` | `formulaInfo.txt` のパースとバックエンド選択 |
| `CalculatorCreatorRegistry` | `ExecutionBackend` → `CalculatorCreator` のレジストリ |
| `MemoryClassLoader` | javac でコンパイルした Java クラスをインメモリで管理 |
| `JavaCodeBlockPolicy` | Java コードブロック実行のグローバルポリシー（v1.4.11） |

### B.3 ADR（Architecture Decision Records）

| ADR | タイトル | ステータス | 決定内容 |
|-----|---------|---------|--------|
| ADR-001 | P4TypedAstEvaluator を PRIMARY 評価パスに昇格 | Accepted (2026-02-26) | `P4TypedAstEvaluator` を `AST_EVALUATOR` の PRIMARY に昇格。フォールバックチェーンは安全網のみ |
| ADR-002 | 数値型昇格ルール | Accepted (2026-03-01) | Java の拡大変換ルールを採用。`double > float > long > int > short > byte` |
| ADR-003 | Java コードブロック実行のセキュリティモデル | Accepted (2026-03-01) | コードブロック機能を保持（削除しない）。サンドボックスなし。信頼環境専用。`JavaCodeBlockPolicy` opt-out を将来追加（v1.4.11 で実装） |

---

## 付録 C: 用語集

| 用語 | 説明 |
|------|------|
| **TinyExpression** | 本プロジェクト。Java 組み込み式評価エンジン |
| **formula** | 評価される式テキスト。`FormulaInfo` の `formula:` フィールドに記述 |
| **FormulaInfo** | 式の定義と設定を含むメタデータブロック（`formulaInfo.txt` の 1 エントリ） |
| **Calculator** | コンパイル済みまたは構築済みの式評価器。`apply(CalculationContext)` で評価 |
| **CalculationContext** | 式評価時の変数ストア。`$variable` 参照の解決に使用 |
| **TenantID** | マルチテナント構成でのテナント識別子。formulaInfo.txt のディレクトリ名に対応 |
| **ExecutionBackend** | 式の評価方式を表す enum。6 種類（`JAVA_CODE`, `AST_EVALUATOR` 等） |
| **P4** | UBNF（Unlaxer BNF Notation）から自動生成されたパーサースタックの世代名 |
| **UBNF** | Unlaxer BNF Notation。P4 文法の記述言語 |
| **パリティ** | 複数バックエンドが同一入力に対して等価な出力を返すこと |
| **フォールバックチェーン** | `AST_EVALUATOR` の段階的評価チェーン（P4 → 生成 AST → レガシー AST → JavaCode） |
| **OOTC** | Operator Operand Tree Creator。旧式の演算子/オペランド木構築器（`JAVA_CODE_LEGACY_ASTCREATOR` で使用） |
| **レガシーパーサー** | unlaxer-common のパーサーコンビネータを使った手書きパーサー群。全言語機能をカバー |
| **JavaCodeBlockPolicy** | Java コードブロック実行の有効/無効を制御するグローバルポリシー（v1.4.11） |
| **ResultConsumer** | `TinyExpressionsExecutor` から各式の結果を受け取るコールバックインタフェース |
| **dependsOn** | FormulaInfo のキー。式間の依存関係を宣言。`calculatorName` を参照 |
| **MemoryClassLoader** | javac でコンパイルした Java クラスをヒープ上に保持するクラスローダー |
| **DAP** | Debug Adapter Protocol。VS Code のデバッグプロトコル。`tinyexpression-ide` で実装 |
| **LSP** | Language Server Protocol。VS Code の言語機能プロトコル。`tinyexpression-ide` で実装 |

---

*本仕様書は TinyExpression v1.4.11 の実装・テスト・ドキュメントを元に作成した。*  
*参照ドキュメント: `docs/architecture.md`, `docs/backends.md`, `docs/language-guide.md`, `docs/decisions/`, `specs/`, `CHANGELOG.md`*
