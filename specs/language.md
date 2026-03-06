# TinyExpression 言語仕様

> ステータス: draft
> 最終更新: 2026-03-01

## スコープ

このドキュメントは TinyExpression 言語の権威的定義を提供する。リテラル、変数、演算子、制御構造、関数、メソッド宣言、外部メソッド呼び出し、コメントの完全な仕様を含む。

このドキュメントが **扱わない** 範囲:
- 型システムの詳細（→ [type-system.md](type-system.md)）
- バックエンドごとの実行差異（→ [backends.md](backends.md)）

## 関連ドキュメント

- [type-system.md](type-system.md) — 型システム
- [p4-grammar.md](p4-grammar.md) — P4 UBNF 文法

---

## リテラル

### 数値リテラル

```
123       -- 整数
3.14      -- 小数
-42       -- 負数
1.5e3     -- 指数表記
```

- 整数および浮動小数点数をサポート
- 符号（`+`, `-`）を先頭に付けられる
- 指数表記（`e` / `E`）をサポート

### 文字列リテラル

```
'hello'   -- シングルクォート
"hello"   -- ダブルクォート
```

- シングルクォートまたはダブルクォートで囲む
- エスケープシーケンスは基本的な Java エスケープに準拠

### 真偽値リテラル

```
true
false
```

---

## 変数

変数名は `$` プレフィックスで始まる（MUST）。

```
$age
$name
$isMember
```

- 変数の値は `CalculationContext` から取得される
- 存在しない変数の参照は `null` を返す

---

## 演算子

### 算術演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `+` | 加算 | `1 + 2` |
| `-` | 減算 | `5 - 3` |
| `*` | 乗算 | `2 * 3` |
| `/` | 除算 | `10 / 3` |

### 比較演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `==` | 等価 | `$age == 20` |
| `!=` | 非等価 | `$age != 20` |
| `>` | より大きい | `$age > 18` |
| `>=` | 以上 | `$age >= 18` |
| `<` | より小さい | `$age < 65` |
| `<=` | 以下 | `$age <= 65` |

### 論理演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `&` | 論理 AND | `true & false` |
| `\|` | 論理 OR | `true \| false` |
| `^` | 論理 XOR | `true ^ false` |
| `not()` | 論理否定 | `not(false)` |

### 演算子の優先順位

1. `()` — 括弧（最高）
2. `not()` — 否定
3. `*`, `/` — 乗除算
4. `+`, `-` — 加減算
5. `>`, `>=`, `<`, `<=` — 比較
6. `==`, `!=` — 等価比較
7. `&` — 論理 AND
8. `^` — 論理 XOR
9. `|` — 論理 OR（最低）

---

## 制御構造

### if / else

```
if(condition){thenValue}else{elseValue}
```

- `condition` は真偽値を返す式（MUST）
- `thenValue` は条件が真の場合の値
- `elseValue` は条件が偽の場合の値
- `else` 節は必須（MUST）

### match

```
match{
  condition1 -> value1,
  condition2 -> value2,
  default -> defaultValue
}
```

- 上から順に条件を評価し、最初に真となった条件の値を返す
- `default` 節は必須（SHOULD）
- 各ケースはカンマで区切る

---

## 文字列関数

### 組み込み文字列関数

| 関数 | 説明 | 例 |
|------|------|-----|
| `toUpperCase()` | 大文字変換 | `toUpperCase($name)` |
| `toLowerCase()` | 小文字変換 | `toLowerCase($name)` |

### メソッド形式の文字列操作

| メソッド | 説明 | 例 |
|---------|------|-----|
| `.startsWith(str)` | 前方一致 | `$msg.startsWith('hello')` |
| `.endsWith(str)` | 後方一致 | `$msg.endsWith('world')` |
| `.contains(str)` | 部分一致 | `$msg.contains('abc')` |

### 文字列スライス

```
$message[0:3]    -- インデックス 0 から 3（排他）
```

---

## 変数宣言

```
variable $name as type set [if not exists] defaultValue description='説明';
var $name as type set defaultValue description='説明';
```

| キーワード | 説明 |
|-----------|------|
| `variable` / `var` | 変数宣言キーワード |
| `$name` | 変数名（`$` プレフィックス） |
| `as type` | 型ヒント（`number`, `string`, `boolean`, `object`, `float`） |
| `set` | デフォルト値設定 |
| `if not exists` | 値が存在しない場合のみ設定 |
| `description='...'` | 変数の説明 |

---

## メソッド宣言

```
returnType methodName(param1 as type, param2 as type){
  body
}
```

### 例

```
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

- 戻り値型: `float`, `string`, `boolean`, `object` 等
- パラメータには `as type` で型ヒントを付けられる
- メソッド呼び出しは `call methodName(args...)` 構文

---

## 外部 Java メソッド呼び出し

### import 文

```
import package.ClassName#methodName as alias;
```

### 呼び出し

```
external returning as returnType alias(args...)
```

### 例

```
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
```

### Java コードブロック埋め込み

FormulaInfo の `formula` フィールド内で Java クラスを直接定義できる:

~~~
```java:package.ClassName
package package;
public class ClassName {
  public returnType methodName(CalculationContext context, ...) { ... }
}
```
import package.ClassName#methodName as alias;
~~~

---

## コメント

| 種類 | 構文 | コンテキスト |
|------|------|------------|
| 行コメント | `#` | FormulaInfo メタデータ |
| ブロックコメント | `/* ... */` | 式本文 |

---

## 現在の制限事項

- 完全な形式文法はレガシーパーサーのソースコードに散在しており、この仕様は実装ベースの記述
- P4 UBNF 文法は全言語機能をカバーしていない（→ [p4-grammar.md](p4-grammar.md)）
- 文字列リテラル内のエスケープシーケンスの完全な仕様は未確定

## 変更履歴

- 2026-03-01: 初版作成
