# Issue Draft: design — P4 LSP import full completion (class / method / alias)

> Created 2026-04-28 — gh issue create が hook で denied のため local draft。
> 投稿時は `gh issue create --title "design: P4 LSP import full completion (class / method / alias)" --body-file docs/TINYEXPRESSION-P4-IMPORT-COMPLETION-ISSUE-DRAFT.md` で投稿可能 (preamble は手動で削除)。

## 背景

issue #11 §3 で「`import sample.v1.Class#method as alias;` のフル補完が未移植」と記載されていたが、調査の結果、**旧 `calculator-lsp` も実装していない**ことが判明した。
旧側は snippet template (`import ${1:Class}#${2:method} as ${3:alias};`) を返すだけで、現在の P4 LSP の `BLOCK_SNIPPETS.put("import", ...)` (commit `e5d036c`) と同等。

つまり「未移植」ではなく「未実装」で、新規設計が必要。本 issue でフル補完を設計・実装する。

参考: `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/TinyExpressionSuggestableParser.java:20`

## ゴール

`import` 構文の各位置で文脈に応じた候補を返す:

1. `import |` → 利用可能なクラス名 (FQN) を補完
2. `import sample.v1.Class#|` → `Class` のメソッド候補を補完
3. `import sample.v1.Class#method as |` → 既存 alias と衝突しない alias 候補
4. `import sample.v1.Class as |` (#method 省略時) → 同上

## 未解決の設計課題

### 1. クラス候補のソース

選択肢:

a. **registry ファイル** (`.teimports` のような独自フォーマット): プロジェクト固有のクラス一覧を明示
b. **classpath scan**: `Thread.currentThread().getContextClassLoader()` から `import` 可能なクラスを動的列挙
c. **CatalogResolver と統合**: `CatalogEntry` を拡張して `availableClasses` を持たせる
d. **ハードコード + 設定オーバーライド**: 標準セット + ユーザー追加

旧 LSP の `TinyExpressionParserMethodCatalog.java:46-59` は classpath からパーサーメソッドを抽出しているが、import 補完には使われていない。再利用検討の余地あり。

### 2. カーソル位置の文脈判定

現在の `wordAt()` 方式では `import` 後の位置を識別できない。**state machine か prefix 解析**が必要:

- 行のプレフィックスが `import\s+` で終わる → クラス候補モード
- `import\s+\S+#` で終わる → メソッド候補モード
- `import\s+\S+(#\S+)?\s+as\s+` で終わる → alias 候補モード

### 3. メソッド補完のソース

クラス選択後に `#method` を補完する:

a. **reflection**: `Class.forName(fqn).getDeclaredMethods()` で実メソッドを取得
b. **registry に method list を持たせる**: ファイルベース、reflection なし
c. **method catalog (旧 LSP の仕組みを再利用)**: 上記 (1) と統合

reflection だと jar が classpath にないと動かない / クラスの初期化を強制する副作用があるので、**registry 方式が望ましい** (opinion)。

### 4. alias 衝突検出 + 候補生成

- 既存 alias は `state.declarations()` から抽出可能 (旧 LSP は `extractImportAliases()` を使っている — `CalculatorAstAnalyzer.java:25-26, 104`)
- 候補生成: クラス名から camelCase 推定 (e.g. `MyClass` → `myClass`)、衝突時は `_2` などサフィックス

## 段階的実装案

1. **Phase 1**: registry ファイル (`.teimports` 仮称) のフォーマット決定 + 読み込み
2. **Phase 2**: state machine で `import ` 後のクラス候補補完
3. **Phase 3**: `#method` 候補補完
4. **Phase 4**: alias 補完 + 衝突検出
5. **Phase 5**: TE026 (使われていない import) / TE027 (未定義 import 参照) などの診断

## 受け入れ条件

1. `import |` で 1 つ以上のクラス候補が返る (registry 経由)
2. `import sample.v1.Class#|` で `Class` のメソッド候補が返る
3. `import ... as |` で alias 候補が返り、既存 alias とは衝突しない
4. registry 未設定でも既存の snippet 補完 (BLOCK_SNIPPETS) は動く (regression なし)
5. テスト: 各位置で適切な候補が返ることを LSP smoke で gate

## 関連

- 親 issue: #11 §3
- 関連 commit: `e5d036c` (BLOCK_SNIPPETS で snippet template 提供)
- 旧 LSP 参考:
  - `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/TinyExpressionSuggestableParser.java:20`
  - `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/TinyExpressionParserMethodCatalog.java:46-59`
