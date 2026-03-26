# TinyExpression LSP Behavior Emulation Test Specification

> 最終更新: 2026-03-09
> ステータス: Draft

## 目的

LSP サーバーの機能（定義へ移動、ワークスペース検索、フォーマット等）が、VS Code を起動せずに Java のユニットテストとして検証可能にすることを目的とする。これにより、開発サイクルの高速化と回帰バグの防止を実現する。

---

## テストアーキテクチャ

### 1. LanguageClient Emulator

LSP サーバーが通知（`publishDiagnostics` 等）を送信するためのモッククライアント。

```java
class LanguageClientEmulator implements LanguageClient {
    // 送信された診断情報を保持
    List<PublishDiagnosticsParams> diagnostics = new ArrayList<>();

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {
        diagnostics.add(params);
    }
    // ... 他のメソッドは no-op
}
```

### 2. Test Harness

サーバーの初期化、ドキュメントのオープン、およびリクエストの送信を簡略化するヘルパークラス。

```java
class LspTestHarness {
    TinyExpressionP4LanguageServerExt server;
    LanguageClientEmulator client;

    void openDocument(String uri, String content) {
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
            new TextDocumentItem(uri, "tinyexpressionP4", 1, content)));
    }

    // 各種リクエストのエミュレーション
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> 
    gotoDefinition(String uri, int line, int col);
}
```

---

## テストケース定義

### LSI-1: Definition

- **ケース 1.1**: 同一ファイル内の変数定義へのジャンプ。
- **ケース 1.2**: メソッド名からの定義ジャンプ。
- **ケース 1.3**: FormulaInfo 形式（行オフセットあり）での正しい位置へのジャンプ。

### LSI-2: Linked Editing Range

- **ケース 2.1**: 変数名を選択した際、その宣言とすべての参照がハイライトされること。
- **ケース 2.2**: 重複する範囲が排除されていること。

### LSI-3: Workspace Symbol

- **ケース 3.1**: クエリ文字列に部分一致する全ドキュメントのシンボルが返されること。
- **ケース 3.2**: 推定された `SymbolKind` が正しいこと（`$` は Variable, 英字開始は Method）。

### LSI-4: Formatting

- **ケース 4.1**: インデントが崩れたコードが正しく修正されること。
- **ケース 4.2**: 空行が維持されること。
- **ケース 4.3**: `FormattingOptions` (tabSize=4 等) が反映されること。

---

## 検証項目

1.  **位置情報の正確性**: LSP の `Position` (0-indexed line/char) と、内部の `offset` 変換にズレがないか。特に `lineOffset` 適用時。
2.  **型安全性の維持**: `ParseFailureDiagnostics` (sealed interface) を経由した診断生成が正しく動作しているか。
3.  **パフォーマンス**: ワークスペース全体の走査において、ドキュメント数が増えても遅延が許容範囲内か。

---

## 実装計画

1.  `TinyExpressionP4LspEmulationTest` クラスの作成。
2.  各機能（LSI-1~4）に対応するテストメソッドの実装。
3.  FormulaInfo 形式のサンプルファイルを用いた統合テストの実施。
