# TinyExpression 言語ガイド

[English version](language-guide.md)

TinyExpression v1.4.10 の完全言語仕様です。

---

## 目次

- [リテラル](#リテラル)
- [変数](#変数)
- [演算子](#演算子)
- [条件式](#条件式)
- [文字列関数](#文字列関数)
- [変数宣言](#変数宣言)
- [ユーザー定義メソッド](#ユーザー定義メソッド)
- [外部 Java メソッド](#外部-java-メソッド)
- [Java コードブロック](#java-コードブロック)
- [コメント](#コメント)
- [型ヒント](#型ヒント)

---

## リテラル

### 数値リテラル

```text
123        整数
3.14       小数
-42        負の整数
1.5e3      指数表記（= 1500.0）
```

- 符号プレフィックス（`+`, `-`）対応
- 指数表記（`e` / `E`）対応
- 既定型は `FormulaInfo` の `numberType` で制御（初期値: `float`）

### 文字列リテラル

```text
'hello'    シングルクォート
"hello"    ダブルクォート
```

- どちらのクォートスタイルも使用可能で、等価
- Java のエスケープシーケンス（`\n`, `\t`, `\\` 等）に準拠

### 真偽値リテラル

```text
true
false
```

---

## 変数

変数名は `$` プレフィックスで始まります。

```text
$age
$name
$isMember
```

- 値は `CalculationContext` から解決されます
- コンテキストに存在しない変数は `null` を返します
- 変数名は大文字小文字を区別します

---

## 演算子

### 算術演算子

| 演算子 | 説明 | 例 |
|--------|------|----|
| `+` | 加算 | `1 + 2` |
| `-` | 減算 | `5 - 3` |
| `*` | 乗算 | `2 * 3` |
| `/` | 除算 | `10 / 3` |

### 比較演算子

| 演算子 | 説明 | 例 |
|--------|------|----|
| `==` | 等価 | `$age == 20` |
| `!=` | 非等価 | `$age != 20` |
| `>` | より大きい | `$age > 18` |
| `>=` | 以上 | `$age >= 18` |
| `<` | より小さい | `$age < 65` |
| `<=` | 以下 | `$age <= 65` |

文字列の `==` / `!=` は内部的に `String.equals()` を使用します。

### 論理演算子

| 演算子 | 説明 | 例 |
|--------|------|----|
| `&` | 論理 AND | `$a & $b` |
| `\|` | 論理 OR | `$a \| $b` |
| `^` | 論理 XOR | `$a ^ $b` |
| `not()` | 論理 NOT | `not($flag)` |

### 演算子優先順位（高→低）

1. `()` — 括弧
2. `not()` — 否定
3. `*`, `/` — 乗除算
4. `+`, `-` — 加減算
5. `>`, `>=`, `<`, `<=` — 関係演算
6. `==`, `!=` — 等価比較
7. `^` — XOR
8. `&` — AND
9. `|` — OR

---

## 条件式

### if / else

```text
if(条件){真の値}else{偽の値}
```

- `条件` は真偽値を返す式（MUST）
- `then` と `else` の両方が必要
- 一致したブランチの値を返す

例:

```text
if($age >= 20){100}else{0}
```

### 三項演算子

```text
条件 ? 真の値 : 偽の値
```

`if`/`else` と等価。P4 バックエンドでサポート。

### match

```text
match{
  条件1 -> 値1,
  条件2 -> 値2,
  default -> デフォルト値
}
```

- 上から順に評価し、最初に真となった条件の値を返す
- `default` ブランチを強く推奨（SHOULD）
- 各ケースはカンマで区切る

例:

```text
match{
  $countryCode == 'JP' -> 1,
  $countryCode == 'US' -> 2,
  default -> 0
}
```

---

## 文字列関数

### 組み込み関数

| 関数 | 説明 | 例 |
|------|------|----|
| `toUpperCase(s)` | 大文字変換 | `toUpperCase($name)` |
| `toLowerCase(s)` | 小文字変換 | `toLowerCase($name)` |
| `trim(s)` | 前後の空白を除去 | `trim($input)` |
| `length(s)` | 文字列長 | `length($name)` |
| `toNum(s)` | 数値に変換 | `toNum($numStr)` |

### メソッド形式の操作

| メソッド | 説明 | 例 |
|---------|------|----|
| `.startsWith(str)` | 前方一致 | `$msg.startsWith('hello')` |
| `.endsWith(str)` | 後方一致 | `$msg.endsWith('world')` |
| `.contains(str)` | 部分一致 | `$msg.contains('abc')` |
| `.isPresent()` | null / 空文字チェック | `$name.isPresent()` |

### 文字列スライス

```text
$message[0:3]    インデックス 0, 1, 2（終端は排他）
```

### 文字列連結

```text
$firstName + ' ' + $lastName
```

オペランドが文字列の場合、`+` 演算子は文字列連結を行います。

---

## 変数宣言

変数宣言で式の入力にデフォルト値と型ヒントを定義します。

```text
variable $名前 as 型 set [if not exists] デフォルト値 description='説明';
var $名前 as 型 set デフォルト値 description='説明';
```

| 部分 | 説明 |
|------|------|
| `variable` / `var` | 宣言キーワード |
| `$名前` | 変数名（`$` で始まる） |
| `as 型` | 型ヒント: `number`, `string`, `boolean`, `object`, `float` |
| `set` | デフォルト値の設定 |
| `if not exists` | コンテキストに値が存在しない場合のみ設定 |
| `description='...'` | 人間向けの説明（LSP ホバーで使用） |

例:

```text
variable $gender as string set if not exists 'male' description='性別';
variable $age as number set 18 description='年齢';
variable $isMember as boolean description='会員フラグ';
```

---

## ユーザー定義メソッド

式内で複数の名前付きメソッドを定義できます。

```text
戻り値型 メソッド名($パラメータ as 型, ...){
  本文
}
```

- エントリーポイントは `main` という名前にする必要があります
- メソッド呼び出しは `call メソッド名(引数)` 構文
- サポートする戻り値型: `float`, `string`, `boolean`, `object`

例:

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

---

## 外部 Java メソッド

ホストアプリケーションの Java メソッドを式内から呼び出します。

### import 文

```text
import パッケージ.クラス名#メソッド名 as エイリアス;
```

### 呼び出し構文

```text
external returning as 戻り値型 エイリアス(引数1, 引数2, ...)
```

### 例

式:

```text
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
```

Java セットアップ:

```java
// 式を実行する前に CalculationContext にオブジェクトを登録
context.set(new sample.v1.CheckDigits());
```

Java クラス:

```java
package sample.v1;
import org.unlaxer.tinyexpression.CalculationContext;

public class CheckDigits {
  public boolean check(CalculationContext context, String target) {
    return target.matches("\\d+");
  }
}
```

---

## Java コードブロック

> **セキュリティ警告**: Java コードブロックは式評価時に JVM 上で任意の Java コードをコンパイル・実行します。この機能は式の作成者を完全に信頼できる場合にのみ有効化してください。信頼できないユーザーへはこの機能を公開しないでください。
>
> **リスク**: ファイルシステムアクセス、ネットワーク呼び出し、`System.exit()` など、あらゆる Java コードが埋め込まれる可能性があります。サンドボックスの制御はホストアプリケーションの責任です。

`formula` フィールドにトリプルバッククォート構文で Java クラスを直接埋め込めます:

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

クラスはインメモリでコンパイルされ、`MemoryClassLoader` 経由でロードされます。コンパイル済みバイトコードは式のクラスローダーにスコープされ、ディスクには保存されません。

セキュリティ上の判断の詳細は [decisions/ADR-003-java-codeblock-safety.md](decisions/ADR-003-java-codeblock-safety.md) を参照。

---

## コメント

| コンテキスト | 構文 | スコープ |
|------------|------|---------|
| FormulaInfo メタデータ | `# コメント` | キーバリューブロックの `#` で始まる行 |
| 式本文 | `/* コメント */` | 式内の任意の場所のブロックコメント |

---

## 型ヒント

### ExpressionTypes

| 型名 | Java 型 | 備考 |
|------|---------|------|
| `byte` | `Byte` | |
| `short` | `Short` | |
| `int` | `Integer` | |
| `long` | `Long` | |
| `float` | `Float` | 推奨デフォルト |
| `double` | `Double` | |
| `number` | `Float` | `float` のエイリアス |
| `string` | `String` | |
| `boolean` | `Boolean` | |
| `object` | `Object` | |
| `bigDecimal` | `BigDecimal` | 式言語でのサポートは限定的 |
| `bigInteger` | `BigInteger` | 式言語でのサポートは限定的 |
| `timestamp` | `Timestamp` | 特殊用途 |

### 算術演算の型昇格

2つのオペランドの数値型が異なる場合、より広い型に昇格します:

```
double > float > long > int
```

例: `1 + 2.0` → 結果型は `float`（オペランドが `double` の場合は `double`）

---

## 組み込み数学関数

| 関数 | 説明 | 例 |
|------|------|----|
| `min(a, b, ...)` | 最小値（2 引数以上） | `min($a, $b, 0)` |
| `max(a, b, ...)` | 最大値（2 引数以上） | `max($a, $b, 100)` |
| `abs(n)` | 絶対値 | `abs($score)` |
| `floor(n)` | 切り捨て | `floor($value)` |
| `ceil(n)` | 切り上げ | `ceil($value)` |

## 組み込み日時関数

| 関数 | 説明 |
|------|------|
| `inTimeRange($ts, start, end)` | タイムスタンプが範囲内なら true |
| `inDayTimeRange($ts, start, end)` | 時刻が範囲内なら true |
