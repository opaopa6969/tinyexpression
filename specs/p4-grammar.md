# P4 UBNF 文法仕様

> ステータス: draft
> 最終更新: 2026-03-01

## スコープ

このドキュメントは TinyExpression の P4 UBNF 文法のカバレッジ、全言語機能との差分、レガシーパーサーとの関係、およびマイグレーション状況を定義する。

このドキュメントが **扱わない** 範囲:
- UBNF 文法の一般仕様（→ unlaxer-dsl/specs/ubnf-syntax.md）
- 完全な言語仕様（→ [language.md](language.md)）

## 関連ドキュメント

- [language.md](language.md) — TinyExpression 言語仕様
- [backends.md](backends.md) — P4 バックエンド
- [docs/ubnf/tinyexpression-p4-draft.ubnf](../docs/ubnf/tinyexpression-p4-draft.ubnf) — P4 文法定義

---

## 文法名

```
grammar TinyExpressionP4 { ... }
```

- パッケージ: `org.unlaxer.tinyexpression.generated.p4`
- 空白処理: `@whitespace: javaStyle`

---

## トークン定義

| トークン | パーサークラス |
|---------|-------------|
| `NUMBER` | `NumberParser` |
| `IDENTIFIER` | `IdentifierParser` |
| `STRING` | `SingleQuotedParser` |

---

## カバレッジ状況

### カバー済み言語機能

| 機能 | P4 ルール |
|------|----------|
| 数値リテラル | `NumberExpression`, `NUMBER` |
| 文字列リテラル | `StringExpression`, `STRING` |
| 真偽値リテラル | `BooleanExpression`, `'true'`, `'false'` |
| 変数参照 | `'$' IDENTIFIER` |
| 算術演算（+, -, *, /） | `AddExpression`, `MultiplyExpression` |
| 比較演算 | `ComparisonExpression` |
| 等価演算 | `EqualityExpression` |
| 論理演算（&, \|, ^） | `AndExpression`, `OrExpression`, `XorExpression` |
| 否定 | `NotExpression` |
| 括弧グループ | `'(' Expression ')'` |
| if/else | `IfExpression` |
| match | `MatchExpression` |
| 変数宣言 | `VariableDeclaration` |
| メソッド宣言 | `MethodDeclaration` |
| メソッド呼び出し | `MethodCallExpression` |
| 型ヒント | `TypeHint`, `NumberTypeHint`, `StringTypeHint` 等 |
| 説明句 | `Description` |
| アノテーション | `Annotation` |

### 演算子優先順位（P4 文法）

P4 文法では以下の優先順位を UBNF の `@precedence` / `@leftAssoc` / `@rightAssoc` アノテーションで定義:

```
OrExpression      @leftAssoc @precedence(level=0)
XorExpression     @leftAssoc @precedence(level=1)
AndExpression     @leftAssoc @precedence(level=2)
EqualityExpression   @leftAssoc @precedence(level=3)
ComparisonExpression @leftAssoc @precedence(level=4)
AddExpression     @leftAssoc @precedence(level=5)
MultiplyExpression   @leftAssoc @precedence(level=6)
```

### 未カバーの言語機能

| 機能 | 状況 |
|------|------|
| 外部 Java メソッド呼び出し（`external`） | 部分実装 |
| Java コードブロック埋め込み | 未カバー |
| 文字列スライス（`$msg[0:3]`） | 未カバー |
| 一部の文字列メソッド | 段階的拡張中 |

---

## レガシーパーサーとの関係

### 共存モデル

```
P4 パーサー（UBNF 生成）
  ├── 成功 → P4 AST → 評価
  └── 失敗/未カバー → レガシーパーサー → 従来パイプライン
```

- P4 パーサーが対応できない構文は自動的にレガシーパーサーにフォールバック
- パリティテストにより両パーサーの結果等価性を検証

### 型安全性の利点

P4 生成パーサーは unlaxer-dsl の以下の機能を活用:

- `@mapping` による型安全な AST（sealed interface + record）
- `@precedence` / `@leftAssoc` による演算子優先順位の宣言的定義
- `@scopeTree` によるスコープ管理メタデータ
- LSP/DAP サーバーの自動生成

---

## マイグレーション状況

### 段階

1. **Phase 0**: P4 文法定義の確定（現在のステータス: draft）
2. **Phase 1**: 基本式（算術、比較、論理、if/else、match）のカバレッジ
3. **Phase 2**: 変数宣言、メソッド宣言のカバレッジ
4. **Phase 3**: 外部メソッド呼び出し、文字列操作のカバレッジ
5. **Phase 4**: 全言語機能のカバレッジ（レガシーパーサー廃止目標）

### 現在の位置

Phase 2 の段階。基本式とメソッド宣言はカバー済み。外部メソッド呼び出しと一部の文字列操作が未カバー。

---

## P4 文法の構造

```
Formula ::= { VariableDeclaration } { Annotation } Expression { MethodDeclaration }
```

### ルート構造

式は以下の3つのセクションから構成される:

1. **変数宣言**（0個以上）
2. **アノテーション**（0個以上）+ **メインの式**
3. **メソッド宣言**（0個以上）

### 式の階層

```
Expression
  ├── NumberExpression (算術・比較・等価式のチェーン)
  ├── StringExpression (文字列リテラル・変数・関数)
  ├── BooleanExpression (論理式)
  ├── ObjectExpression (オブジェクト式)
  ├── IfExpression (if/else)
  └── MatchExpression (match)
```

---

## 現在の制限事項

- P4 文法は draft ステータスであり、変更される可能性がある
- 全言語機能をカバーするまでレガシーパーサーとの共存が必要
- 文法の自己ホスティング（P4 文法を P4 自身でパース）は対象外

## 変更履歴

- 2026-03-01: 初版作成
