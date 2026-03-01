# TinyExpression マルチプロジェクト移行計画 (詳細版)

## 1. 背景と目的
TinyExpression は、現在 **6 つの独立した実行バックエンド** を持つ高度なエンジンへと成長しました。しかし、現在の単一プロジェクト（モノリシック）構造は認知負荷が高く、循環参照のリスクも孕んでいます。本計画では、コア定義と具体的な評価戦略を分離する「3 層アーキテクチャ」への移行を定義します。

### 維持すべき 6 つの実行バックエンド:
1.  `JAVA_CODE`: 現行の JavaCode 実装 (V3)
2.  `JAVA_CODE_LEGACY_ASTCREATOR`: リファクタ前の比較ベースライン (凍結)
3.  `AST_EVALUATOR`: 生成された AST を直接評価する実行ライン
4.  `DSL_JAVA_CODE`: ハイブリッドな Java エミッタ (ネイティブ + ブリッジ)
5.  `P4_AST_EVALUATOR`: UBNF 生成パーサー + AST 評価チェーン
6.  `P4_DSL_JAVA_CODE`: UBNF 生成パーサー + DSL JavaCode チェーン

---

## 2. 進化の歴史: エージェントと共に歩んだ道のり

6 つのバックエンドの存在は、単なる技術的な選択ではなく、人間と AI エージェント（Agent A, Agent B）の共同作業によるプロジェクトの進化の記録です。

### フェーズ 1: 手書きのルーツとパフォーマンス
- **起源**: 当初は `unlaxer-common` を使用した手書きの AST トラバースとして実装。
- **転換**: パフォーマンス向上のため Java コード生成方式へ移行。保守コスト削減のため、初期の手書きトラバースは削除。

### フェーズ 2: 文法の肥大化と Agent A
- **成長**: 文法が複雑になり、ParseTree から AST への変換を人間が保守するのが困難になる。
- **介入**: **Agent A** が変換ロジックのリファクタリングを担当 (実装 2)。
- **DSL 時代**: LSP/DAP を視野に `unlaxer-dsl` を作成。Agent A が AST トラバース (実装 3) と Java 生成 (実装 4) を実装。
- **課題**: 機能はしたが、LSP/DAP 対応が正規表現ベースであり、保守性に課題が残った。

### フェーズ 3: 型安全性と Agent B
- **基盤整備**: 高精度なコード生成を支援するため、人間が `ParseFailureDiagnostics` などの型安全支援機能を整備。
- **洗練**: **Agent B** がこれらの機能を活用し、現在の主軸である「型安全な P4 バックエンドシリーズ」(実装 5, 6) を完成させた。
- **現在**: これら 6 つのバックエンドは、各進化ステップの正当性を検証（パリティテスト）するために維持されている。

---

## 3. 3 層アーキテクチャ戦略

認知負荷を下げるため、モジュールを安定性と役割に基づいて 3 つの層に分類します。

### 第 1 層: コア API とメタデータ (`tinyexpression-api`)
- **役割**: 純粋な定義、インタフェース、メタデータ。
- **主要シンボル**: `Calculator`, `ExecutionBackend`, `FormulaInfo`, `Source`。
- **制約**: 実装モジュールに依存してはならない (MUST NOT)。

### 第 2 層: 評価器の実装 (`tinyexpression-evaluators-*`)
- **グループ A: `tinyexpression-evaluators-basic`**: 標準評価器 (JavaCode V3, Legacy, AST)。
- **グループ B: `tinyexpression-evaluator-dsl`**: 高速なハイブリッド Java エミッタ。
- **戦略**: これらのモジュールは `Calculator` を実装し、SPI を通じて自身を登録する。

### 第 3 層: P4 およびツール層 (`tinyexpression-p4`, `tinyexpression-tooling`)
- **P4 エンジン**: UBNF 生成コードを含み、第 2 層の評価器へのブリッジを担う。
- **ツール**: LSP, DAP, CLI。スタック全体のコンシューマ（利用者）。

---

## 4. 分離戦略: SPI (Service Provider Interface)

循環参照（例：レジストリが実装を知っており、実装がレジストリを使う）を避けるため、Java の `ServiceLoader` を採用します。

- **レジストリ**: `tinyexpression-api` 内の `CalculatorCreatorRegistry` は、実行時に `ServiceLoader` を使って利用可能な `CalculatorCreator` を動的に見つける。
- **プロバイダー**: 各評価器モジュールは `META-INF/services/` に定義ファイルを配置する。
- **メリット**: コアコードを修正することなく、JAR をクラスパスに追加するだけで新しいバックエンドを追加できる。

---

## 5. 提案するモジュール構成

| モジュール名 | 階層 | 対象バックエンド |
| :--- | :--- | :--- |
| `tinyexpression-api` | 第 1 層 | コアインタフェース, Enum, レジストリ (SPI) |
| `tinyexpression-evaluators-basic` | 第 2 層 | `JAVA_CODE`, `JAVA_CODE_LEGACY_ASTCREATOR`, `AST_EVALUATOR` |
| `tinyexpression-evaluator-dsl` | 第 2 層 | `DSL_JAVA_CODE` |
| `tinyexpression-p4` | 第 3 層 | `P4_AST_EVALUATOR`, `P4_DSL_JAVA_CODE` |
| `tinyexpression-tooling` | 第 3 層 | LSP, DAP, CLI |
| `tinyexpression-test-suite` | - | モジュール横断の等価性検証 (パリティテスト) |

---

## 6. 技術的課題と解決策

### A. コード生成のライフサイクル
- **課題**: `tinyexpression-p4` は `unlaxer-dsl` が生成したコードに依存する。
- **解決策**: `unlaxer-dsl` をビルド時ライブラリとして組み込み、Maven の `generate-sources` フェーズで自動生成を実行する。

### B. バックエンド間の等価性 (Parity)
- **課題**: モジュール分割後も `ThreeExecutionBackendParityTest` を維持する必要がある。
- **解決策**: `tinyexpression-test-suite` モジュールを「フルスタック検証器」とし、全バックエンドの出力を横断的にチェックする。

### C. Java 21 Preview 機能
- **解決策**: 親 POM で `--enable-preview` 設定を一括管理し、全モジュールで一貫性を保つ。

---

## 7. 次のステップ
1.  **`ExecutionBackend` のリファクタ**: 準備として、現行コードのレジストリを SPI 対応にする。
2.  **親 POM の作成**: 共通のバージョンとプラグインを定義。
3.  **`tinyexpression-api` の抽出**: コアインタフェースと基本モデルを移動。
4.  **評価器の分離**: 実装ロジックをそれぞれのモジュールへ移動。
5.  **P4 生成の統合**: UBNF ビルドを Maven 構造内で自動化。
