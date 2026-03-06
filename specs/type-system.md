# 型システム仕様

> ステータス: draft
> 最終更新: 2026-03-01

## スコープ

このドキュメントは TinyExpression の型システム仕様を定義する。すべての型、ExpressionTypes enum、型変換ルール、型昇格ルールを含む。

このドキュメントが **扱わない** 範囲:
- 言語構文（→ [language.md](language.md)）

## 関連ドキュメント

- [language.md](language.md) — 言語仕様
- [backends.md](backends.md) — バックエンドごとの型処理

---

## ExpressionTypes enum

**enum**: `org.unlaxer.tinyexpression.parser.ExpressionTypes`

| 値 | Java ラッパー型 | Java プリミティブ型 | リテラルサフィックス |
|----|---------------|-------------------|-------------------|
| `_byte` | `Byte` | `byte` | — |
| `_short` | `Short` | `short` | — |
| `_int` | `Integer` | `int` | — |
| `_float` | `Float` | `float` | `"f"` |
| `_double` | `Double` | `double` | `"d"` |
| `_long` | `Long` | `long` | `"L"` |
| `bigDecimal` | `BigDecimal` | — | — |
| `bigInteger` | `BigInteger` | — | — |
| `number` | `Float` | `float` | — |
| `string` | `String` | — | — |
| `_boolean` | `Boolean` | `boolean` | — |
| `object` | `Object` | — | — |
| `timestamp` | `Timestamp` | — | — |
| `_void` | `Void` | `void` | — |

### number 型

`number` 型は `float` のエイリアスとして扱われる。`Float.class` と `float.class` にマッピングされる。

---

## 型の使用箇所

### 変数宣言の型ヒント

```
variable $age as number ...
variable $name as string ...
variable $flag as boolean ...
```

変数宣言で使用可能な型ヒント: `number`, `float`, `string`, `boolean`, `object`

### FormulaInfo の resultType

FormulaInfo の `resultType` キーで使用可能な値:

| 値 | 対応する ExpressionTypes |
|----|----------------------|
| `string` | `string` |
| `boolean` | `_boolean` |
| `byte` | `_byte` |
| `short` | `_short` |
| `int` | `_int` |
| `long` | `_long` |
| `float` | `_float` |
| `double` | `_double` |
| FQCN | `object`（指定クラスにキャスト） |

### メソッド宣言の戻り値型

```
float methodName(...){ ... }
string methodName(...){ ... }
boolean methodName(...){ ... }
```

---

## 型変換ルール

### 暗黙の型変換

数値型間の暗黙変換は Java の拡大変換ルールに従う:

```
byte → short → int → long → float → double
```

### 明示的な型指定

`SpecifiedExpressionTypes` により式と結果の型を明示的に指定できる:

```java
new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float)
```

- 第1引数: 式の評価型
- 第2引数: 結果の型

---

## 型昇格ルール

### 算術演算の型昇格

2つの数値オペランドの型が異なる場合、より広い型に昇格される:

1. いずれかが `double` → 結果は `double`
2. いずれかが `float` → 結果は `float`
3. いずれかが `long` → 結果は `long`
4. それ以外 → 結果は `int`

### 比較演算の型

- 数値比較: オペランドを共通の数値型に昇格して比較
- 文字列比較: `==`, `!=` は文字列の `equals()` を使用
- 結果は常に `boolean`

---

## Tag ベースの型情報

各 `ExpressionTypes` 値は `Tag` オブジェクトを持つ。パーサーのタグシステムを通じて式の型情報が伝搬される:

```java
ExpressionTypes._float.asTag()  // Tag.of(ExpressionTypes._float)
```

---

## 現在の制限事項

- `BigDecimal`, `BigInteger` は型として定義されているが、式言語での直接的なリテラルサポートは限定的
- `timestamp` 型は特殊用途で、式言語の標準的な演算ではサポートされていない
- `_void` 型は式の戻り値としては使用されない

## 変更履歴

- 2026-03-01: 初版作成
