# TinyExpression 概要

> ステータス: draft
> 最終更新: 2026-03-01

## スコープ

このドキュメントは TinyExpression プロジェクトの目的、unlaxer-common/dsl との関係、ハイブリッドアーキテクチャ（レガシー + P4 DSL 共存）、およびユースケースを定義する。

このドキュメントが **扱わない** 範囲:
- unlaxer-common のパーサーコンビネータ仕様（→ unlaxer-common/specs/）
- unlaxer-dsl の UBNF 仕様（→ unlaxer-dsl/specs/）
- チュートリアル・使い方ガイド（→ README.md）

## 関連ドキュメント

- [language.md](language.md) — TinyExpression 言語仕様
- [type-system.md](type-system.md) — 型システム仕様
- [backends.md](backends.md) — 実行バックエンド仕様
- [pipeline.md](pipeline.md) — 実行パイプライン仕様
- [formula-info.md](formula-info.md) — FormulaInfo 仕様
- [api.md](api.md) — 公開 API 仕様
- [p4-grammar.md](p4-grammar.md) — P4 UBNF 文法仕様

## プロジェクト目的

TinyExpression は **Java アプリケーションに組み込み可能な式評価エンジン** である。ランタイムで式を評価し、複数の式を依存関係付きで実行する機能を提供する。

### 主要機能

- ランタイムでの式評価
- 複数式の依存関係付き実行（`TinyExpressionsExecutor`）
- Java コード生成系と AST 評価系の両対応
- 外部 Java メソッドの呼び出し
- ユーザー定義メソッド

## unlaxer-common/dsl との関係

| プロジェクト | TinyExpression での役割 |
|------------|----------------------|
| unlaxer-common | パーサーコンビネータのランタイムライブラリ。レガシーパーサー（手書き）のベース |
| unlaxer-dsl | UBNF 文法からのパーサー自動生成。P4 文法の生成に使用 |

## ハイブリッドアーキテクチャ

TinyExpression は **レガシーパーサー** と **P4 DSL 生成パーサー** が共存するハイブリッドアーキテクチャを採用している。

### レガシーパーサー

- unlaxer-common の `Parser` インタフェースを直接実装した手書きパーサー群
- `org.unlaxer.tinyexpression.parser` パッケージに配置
- 全言語機能をカバー

### P4 DSL 生成パーサー

- `docs/ubnf/tinyexpression-p4-draft.ubnf` で UBNF 文法を定義
- unlaxer-dsl により型安全なパーサー・AST・マッパーを自動生成
- 段階的にレガシーパーサーの機能をカバー中
- LSP/DAP サポートのベース

## 6 つの実行バックエンド

| バックエンド | 説明 |
|------------|------|
| `JAVA_CODE` | 現行 JavaCode 実装（プロダクションベースライン） |
| `JAVA_CODE_LEGACY_ASTCREATOR` | リファクタ前の比較ベースライン（凍結） |
| `AST_EVALUATOR` | AST 走査実行 |
| `DSL_JAVA_CODE` | DSL JavaCode シーム（ハイブリッド） |
| `P4_AST_EVALUATOR` | UBNF 生成パーサー + AST 評価チェーン |
| `P4_DSL_JAVA_CODE` | UBNF 生成パーサー + DSL JavaCode チェーン |

詳細: [backends.md](backends.md)

## ユースケース

### パターン A: サービス内で単発評価

固定式やデプロイ同梱式向け。`JavaCodeCalculatorV3` で式をコンパイルして実行。

### パターン B: 複数式の依存解決実行

`TinyExpressionsExecutor` による複数式の依存関係付き実行。`FormulaInfo` ファイルで式群を管理。

### パターン C: 外部メソッド呼び出し

Java クラスのメソッドを式内から呼び出す。`import` 文でメソッドを宣言し、`external` キーワードで呼び出す。

## 実行環境

- **Java**: 21+
- **Maven**: 3.8+
- **注意**: テスト/ランタイムで反射アクセスを使うため `add-opens` が必要

## 現在の制限事項

- P4 文法は全言語機能をカバーしていない（段階的拡張中）
- `JAVA_CODE_LEGACY_ASTCREATOR` は凍結状態で最小限のパッチのみ許容
- P4 文法でカバーされていない構文を使用するフォーミュラはフォールバックパスを使用する

## 変更履歴

- 2026-03-01: 初版作成
