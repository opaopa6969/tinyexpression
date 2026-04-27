[English](./INDEX.md) | [日本語](./INDEX.ja.md)

---

# tinyexpression ドキュメント索引

## 主要ガイド

| ドキュメント | 説明 |
|-------------|------|
| [getting-started-ja.md](./getting-started-ja.md) | Maven 設定、単発式、複数式エグゼキュータ |
| [language-guide-ja.md](./language-guide-ja.md) | 完全な言語仕様 |
| [backends-ja.md](./backends-ja.md) | 6 バックエンドの比較、フォールバックチェーン、パリティ契約 |
| [architecture-ja.md](./architecture-ja.md) | パーサーコンビネータ、AST、6 エバリュエータ、型システム |

## アーキテクチャ決定記録

| ドキュメント | 説明 |
|-------------|------|
| [decisions/ADR-001-p4-primary.md](./decisions/ADR-001-p4-primary.md) | P4TypedAstEvaluator を PRIMARY に昇格した理由 |
| [decisions/ADR-002-type-promotion.md](./decisions/ADR-002-type-promotion.md) | 数値型昇格ルール |
| [decisions/ADR-003-java-codeblock-safety.md](./decisions/ADR-003-java-codeblock-safety.md) | Java コードブロックのセキュリティモデル |

## 会話形式ガイド

| # | ドキュメント | 説明 |
|---|-------------|------|
| 1 | [実装ガイド](./implementation-guide-dialogue.ja.md) | 先輩と新人の会話で学ぶ5つのバックエンド |
| 2 | [Parser Generator 比較 & @eval Strategy](./parser-generator-comparison-and-eval-strategy.ja.md) | unlaxer vs ANTLR/PEG.js/Tree-sitter 比較と @eval アノテーション設計 |

## 分析

| ドキュメント | 説明 |
|-------------|------|
| [backend-coverage-matrix.md](./backend-coverage-matrix.md) | 5つの実行バックエンドのカバレッジマトリクス |
| [feature-parity-diff.md](./feature-parity-diff.md) | 手書きパスと UBNF (P4) パスの機能パリティ差分 |

## その他のドキュメント

| ドキュメント | 説明 |
|-------------|------|
| [TINYEXPRESSION-BACKEND-CONTRACT.md](./TINYEXPRESSION-BACKEND-CONTRACT.md) | バックエンド契約仕様 |
| [TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md](./TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md) | 依存関係拡張ノート |
| [TINYEXPRESSION-DESIGN-BACKLOG.md](./TINYEXPRESSION-DESIGN-BACKLOG.md) | 設計バックログ |
| [TINYEXPRESSION-DSL-HANDOVER-2026-02-20.md](./TINYEXPRESSION-DSL-HANDOVER-2026-02-20.md) | DSL 引き継ぎドキュメント |
| [TINYEXPRESSION-DSL-ROADMAP.md](./TINYEXPRESSION-DSL-ROADMAP.md) | DSL ロードマップ |
| [TINYEXPRESSION-DUAL-EVALUATOR-DAP-PLAN.md](./TINYEXPRESSION-DUAL-EVALUATOR-DAP-PLAN.md) | デュアル評価器 DAP 計画 |
| [TINYEXPRESSION-FINAL-GAP-AUDIT.md](./TINYEXPRESSION-FINAL-GAP-AUDIT.md) | 最終ギャップ監査 |
| [TINYEXPRESSION-P4-UPGRADE-FOLLOWUP-ISSUE-2026-04-24.md](./TINYEXPRESSION-P4-UPGRADE-FOLLOWUP-ISSUE-2026-04-24.md) | 最新 UBNF / P4 更新後の残課題 issue draft |
| [TINYEXPRESSION-LSP-ADDITIONAL-FEATURES.md](./TINYEXPRESSION-LSP-ADDITIONAL-FEATURES.md) | LSP 追加機能 |
| [TINYEXPRESSION-LSP-IMPLEMENTATION-BACKLOG.md](./TINYEXPRESSION-LSP-IMPLEMENTATION-BACKLOG.md) | LSP 実装バックログ |
| [TINYEXPRESSION-LSP-TEST-SPEC.md](./TINYEXPRESSION-LSP-TEST-SPEC.md) | LSP テスト仕様 |
| [TINYEXPRESSION-MULTIPROJECT-PLAN.md](./TINYEXPRESSION-MULTIPROJECT-PLAN.md) | マルチプロジェクト計画 |
| [TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md](./TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md) | P4 機能ギャップ分析 |
| [TINYEXPRESSION-P4-LSP-DAP-IMPL-PLAN.md](./TINYEXPRESSION-P4-LSP-DAP-IMPL-PLAN.md) | P4 LSP/DAP 実装計画 |
| [TINYEXPRESSION-P4-LSP-DAP-TASKS.md](./TINYEXPRESSION-P4-LSP-DAP-TASKS.md) | P4 LSP/DAP タスク |
| [TINYEXPRESSION-P4-PIPELINE-GUIDE.md](./TINYEXPRESSION-P4-PIPELINE-GUIDE.md) | P4 パイプラインガイド |
| [TINYEXPRESSION-P4-UBNF-EXTENSION-SPEC.md](./TINYEXPRESSION-P4-UBNF-EXTENSION-SPEC.md) | P4 UBNF 拡張仕様 |
| [TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md](./TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md) | Unlaxer DSL ハンドブック |
| [TINYEXPRESSION-UNLAXERDSL-MIGRATION-GUIDE.md](./TINYEXPRESSION-UNLAXERDSL-MIGRATION-GUIDE.md) | Unlaxer DSL 移行ガイド |
| [TINYEXPRESSION-VSCODE-PLUGIN-NOTES.md](./TINYEXPRESSION-VSCODE-PLUGIN-NOTES.md) | VSCode プラグインノート |
