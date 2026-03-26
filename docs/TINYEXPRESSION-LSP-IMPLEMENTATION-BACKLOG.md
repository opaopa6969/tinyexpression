# TinyExpression LSP Implementation Backlog

> 最終更新: 2026-03-09
> フェーズ: A, B, C, D（順序実装）+ 型システム検討タスク

LSP 追加機能実装と、型システム UBNF サポート検討の Backlog 管理。

---

## Overview

**既実装** (VSIX v0.2.2 callHierarchy対応):
- ✅ LSE-1~5: 基本 LSP 機能 (documentSymbol, rename, hover 等 12機能)
- ✅ LSE-EXT-1~5: 拡張機能 (signatureHelp拡張, codeLens, callHierarchy 等 5機能)

**新規 Backlog** (本ドキュメント):
- Phase A (⭐⭐⭐ 高優先): LSI-1, LSI-2 ✅ 完了 (2026-03-09)
- Phase B (⭐⭐ 中優先): LSI-3, LSI-4 ✅ 完了 (2026-03-09)
- Phase D (⭐⭐ 中優先): LSI-TEST-EMU (LSP動作エミュレーションテスト) 🆕
- **検討タスク**: TYP-SYS-EVAL (型システム UBNF サポート評価)

---

## Phase A — 実装済み（高優先度 ⭐⭐⭐）

### LSI-1: definition（定義へ移動）

**ステータス**: ✅ 完了 (2026-03-09)

**説明**: カーソル位置の変数/メソッド名から定義箇所へジャンプ（Ctrl+Click）

---

### LSI-2: linkedEditingRange（リンク編集）

**ステータス**: ✅ 完了 (2026-03-09)

**説明**: 同一識別子を複数選択して同時編集（F2 → 全出現箇所編集）

---

## Phase B — 実装済み（中優先度 ⭐⭐）

### LSI-3: workspaceSymbol（ワークスペース検索）

**ステータス**: ✅ 完了 (2026-03-09)

**説明**: VS Code Ctrl+T でワークスペース全体のシンボルを検索

---

### LSI-4: formatting（ドキュメント整形）

**ステータス**: ✅ 完了 (2026-03-09)

**説明**: Alt+Shift+F でドキュメント全体をインデント整形

---

## Phase D — 実装予定（中優先度 ⭐⭐）

### LSI-TEST-EMU: LSP動作エミュレーションテスト

**ステータス**: Backlog

**説明**: LSPサーバーの各機能（definition, formatting, etc）が期待通りに動作するかを、VS Codeを起動せずにJava側でシミュレートして検証するテスト群の構築。

**仕様**: `docs/TINYEXPRESSION-LSP-TEST-SPEC.md` 参照

**工数見積**: 8-10h

**チェックリスト**:
- [ ] テスト用 LanguageClient エミュレータの作成
- [ ] LSI-1 (definition) の回帰テスト作成
- [ ] LSI-2 (linkedEditingRange) の回帰テスト作成
- [ ] LSI-3 (workspaceSymbol) の回帰テスト作成
- [ ] LSI-4 (formatting) の回帰テスト作成
- [ ] FormulaInfo形式（行オフセットあり）の複合テスト作成

---

## 検討タスク — 型システム UBNF サポート評価

### TYP-SYS-EVAL: UBNF での型システムサポート可能性検討

**ステータス**: Backlog（検討タスク、実装ではない）

... (中略) ...

---

## 実装スケジュール案 (2026-03-09 更新)

```
Week 1 (実績):
  Mon: LSI-1, LSI-2, LSI-3, LSI-4 実装完了 + VSIX v0.2.3 パッケージング

Week 2:
  Mon-Tue: LSI-TEST-EMU 仕様策定 + エミュレータ基盤作成
  Wed-Fri: LSI-1~4 エミュレーションテスト実装

Week 3:
  Mon-Wed: 統合テスト完了 + VSIX v0.2.4 リリース
  Thu-Fri: TYP-SYS-EVAL 設計検討開始
```

---

## 版管理

| VSIX版 | 実装機能 | unlaxer-dsl | ステータス |
|--------|---------|-------------|-----------|
| v0.2.2 | callHierarchy (LSE-EXT-5) | 0.3.2 | ✅ リリース |
| v0.2.3 | LSI-1, LSI-2, LSI-3, LSI-4 | 0.3.2 | ✅ 進行中 |
| v0.2.4 | LSI-TEST-EMU | 0.3.2 | 計画中 |
| v0.2.5+ | TYP-SYS (if approved) | 0.4.0? | 検討中 |

---

## 関連ドキュメント

- [`TINYEXPRESSION-LSP-ADDITIONAL-FEATURES.md`](./TINYEXPRESSION-LSP-ADDITIONAL-FEATURES.md) — 各機能の詳細 specs
- [`TINYEXPRESSION-LSP-TEST-SPEC.md`](./TINYEXPRESSION-LSP-TEST-SPEC.md) — テストエミュレーション specs
- [`TINYEXPRESSION-DESIGN-BACKLOG.md`](./TINYEXPRESSION-DESIGN-BACKLOG.md) — 高レベルの設計課題
