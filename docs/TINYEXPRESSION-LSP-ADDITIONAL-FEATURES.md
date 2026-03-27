# TinyExpression LSP — 追加機能実装仕様

> 最終更新: 2026-03-09
> ステータス: draft — Backlog アイテム化（4機能）

型システムなし環境で実装可能な LSP 追加機能の仕様書。

---

## 概要

既存の LSP 機能（callHierarchy, inlayHints, foldingRange 等 12機能）に加えて、以下 4 つの機能を Backlog 化。

**実装可能な理由**: ScopeStore API による シンボル宣言/参照の検索が基盤

**実装困難な機能**（型システム依存）:
- ❌ `implementation` — 実装先検索（インタフェース/クラス継承情報が必要）
- ❌ `typeDefinition` — 型定義へ移動（型システムが必要）
- ❌ `declaration` — 宣言検索（型推論と重複）

---

## LSI-1: definition（定義へ移動）

### 概要

カーソル位置の変数/メソッド名をクリックして **「定義へ移動」** を実行。

### 仕様

**LSP 対応メソッド**: `textDocument/definition`

**入力**: `DefinitionParams` = `{ uri, position }`

**戻り値**: `LocationLink[] | Location[]`

```java
record Location(
    String uri,         // ファイル URI
    Range range         // 定義位置の範囲
) {}

record LocationLink(
    String targetUri,
    Range targetRange,          // 定義の完全範囲
    Range targetSelectionRange  // 定義名のみの範囲
) {}
```

### 表示イメージ

```
// エディタ A: formula
var $age as number set 42;
    ^^^^
    ↓ Ctrl+Click → 定義へ移動

// エディタ B: メソッド定義
method isAdult($age as number) → boolean {
           ^^^^
           ↑ ここにジャンプ
  ...
}
```

### 処理フロー

1. **単語抽出**: カーソル位置の単語を取得（`wordAt()` helper 使用）
2. **シンボル検索**: `ExtDocumentState.declarations` から name 一致を検索
3. **SymbolInfo 取得**: 定義の offset/length を取得
4. **Range 構築**: offset → Position 変換で Range を構築
5. **Location 返却**: LocationLink として返す

### 実装詳細

```java
@Override
public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
    definition(DefinitionParams params) {

  String uri = params.getTextDocument().getUri();
  ExtDocumentState state = server.extDocuments.get(uri);
  if (state == null) {
    return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
  }

  // 1. カーソル位置の単語を取得
  String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
  if (word.isEmpty()) {
    return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
  }

  // 2. declarations から定義を検索
  List<LocationLink> links = new ArrayList<>();
  for (var decl : state.declarations()) {
    if (word.equals(decl.name())) {
      int offset = decl.sourceOffset();
      Range range = offsetToRange(state.content(), offset, word.length());

      LocationLink link = new LocationLink();
      link.setTargetUri(uri);
      link.setTargetRange(range);
      link.setTargetSelectionRange(range);
      links.add(link);
    }
  }

  return CompletableFuture.completedFuture(Either.forRight(links));
}
```

### バリデーション

- 複数定義が見つかった場合は最初のものを返す（エラーではなく警告）
- 定義がない場合は空リスト返却

### 実装難度

**S（Simple）** — references の逆方向検索のみ

### 実装箇所

**tinyexpression 側**:
- `TinyExpressionP4LanguageServerExt.ExtTextDocumentService#definition()`

### 優先度

⭐⭐⭐ **高** — 基本的なナビゲーション機能

---

## LSI-2: linkedEditingRange（リンク編集）

### 概要

同一変数名/メソッド名を複数選択して、同時に編集可能にする機能。

### 仕様

**LSP 対応メソッド**: `textDocument/linkedEditingRange`

**入力**: `LinkedEditingRangeParams` = `{ uri, position }`

**戻り値**: `LinkedEditingRanges`

```java
record LinkedEditingRanges(
    List<Range> ranges  // 同時編集対象の範囲リスト
) {}
```

### 表示イメージ

```
var $age as number set 42;
    ^^^
if ($age > 18) {
    ^^^  ← 同時にハイライト・編集可能
  return $age + 1;
         ^^^
}
```

VS Code で「シンボルの名前変更」パレット（F2）を開くと、複数箇所が自動選択される。

### 処理フロー

1. **単語抽出**: カーソル位置の単語を取得
2. **参照検索**: `ExtDocumentState.references` から name 一致を全検索
3. **Range 構築**: 各参照を Range に変換
4. **LinkedEditingRanges 返却**

### 実装詳細

```java
@Override
public CompletableFuture<LinkedEditingRanges> linkedEditingRange(
    LinkedEditingRangeParams params) {

  String uri = params.getTextDocument().getUri();
  ExtDocumentState state = server.extDocuments.get(uri);
  if (state == null) {
    return CompletableFuture.completedFuture(new LinkedEditingRanges(Collections.emptyList()));
  }

  // 1. カーソル位置の単語を取得
  String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
  if (word.isEmpty()) {
    return CompletableFuture.completedFuture(new LinkedEditingRanges(Collections.emptyList()));
  }

  // 2. 宣言と参照をあわせて検索
  List<Range> ranges = new ArrayList<>();

  // 2a. 宣言を追加
  for (var decl : state.declarations()) {
    if (word.equals(decl.name())) {
      int offset = decl.sourceOffset();
      Range range = offsetToRange(state.content(), offset, word.length());
      ranges.add(range);
    }
  }

  // 2b. 参照を追加
  for (var ref : state.references()) {
    if (word.equals(ref.name())) {
      int offset = ref.offset();
      Range range = offsetToRange(state.content(), offset, word.length());
      ranges.add(range);
    }
  }

  return CompletableFuture.completedFuture(new LinkedEditingRanges(ranges));
}
```

### バリデーション

- 同じ範囲が重複した場合は LinkedHashSet で去重
- 定義のない単語は参照のみを返す

### 実装難度

**S（Simple）** — references の単純マッピング

### 実装箇所

**tinyexpression 側**:
- `TinyExpressionP4LanguageServerExt.ExtTextDocumentService#linkedEditingRange()`

### 優先度

⭐⭐⭐ **高** — リファクタリング支援

---

## LSI-3: workspaceSymbol（ワークスペース全体のシンボル検索）

### 概要

VS Code の「ワークスペースシンボル検索」パレット（Ctrl+T）で、開いている全ファイルのシンボルを検索。

### 仕様

**LSP 対応メソッド**: `workspace/symbol`

**入力**: `WorkspaceSymbolParams` = `{ query }`

**戻り値**: `SymbolInformation[]`

```java
record SymbolInformation(
    String name,
    SymbolKind kind,
    Location location,      // シンボルのファイル位置
    String containerName    // 親シンボル名（オプション）
) {}
```

### 表示イメージ

```
Ctrl+T → "age" と入力
  ↓
検索結果:
  $age (Variable) — formula.p4 Line 5
  $age (Parameter) — calculateAge() パラメータ
  isAdult (Method) — formula.p4 Line 10
```

### 処理フロー

1. **クエリ解析**: 入力文字列を prefix として保持
2. **全ドキュメント走査**: `server.extDocuments.values()` のすべての state を走査
3. **シンボルフィルタ**: 各 state の declarations/references から query にマッチするものを抽出
4. **SymbolInformation リスト構築**

### 実装詳細

```java
@Override
public CompletableFuture<List<? extends SymbolInformation>> symbol(
    WorkspaceSymbolParams params) {

  String query = params.getQuery().toLowerCase();
  List<SymbolInformation> results = new ArrayList<>();

  // 全オープンドキュメントを走査
  for (Map.Entry<String, ExtDocumentState> entry : server.extDocuments.entrySet()) {
    String uri = entry.getKey();
    ExtDocumentState state = entry.getValue();

    // declarations から query マッチするシンボルを抽出
    for (var decl : state.declarations()) {
      if (decl.name().toLowerCase().contains(query)) {
        int offset = decl.sourceOffset();
        Range range = offsetToRange(state.content(), offset, decl.name().length());

        SymbolInformation info = new SymbolInformation();
        info.setName(decl.name());
        info.setKind(inferSymbolKind(decl.name()));
        info.setLocation(new Location(uri, range));

        results.add(info);
      }
    }
  }

  return CompletableFuture.completedFuture(results);
}

/** シンボル名から SymbolKind を推定 */
private SymbolKind inferSymbolKind(String name) {
  if (name.startsWith("$")) return SymbolKind.Variable;
  if (name.startsWith("is") || name.startsWith("get")) return SymbolKind.Method;
  return SymbolKind.Function;
}
```

### バリデーション

- 検索結果の上限を 100 件に制限（パフォーマンス）
- 大文字小文字区別なし（`.toLowerCase()` で正規化）

### 実装難度

**M（Medium）** — 複数ドキュメント走査が必要

### 実装箇所

**tinyexpression 側**:
- `TinyExpressionP4LanguageServerExt#symbol()` （TextDocumentService ではなく main class）

**LanguageServer インターフェース** から呼ばれる:
```java
@Override
public CompletableFuture<List<? extends SymbolInformation>> symbol(
    WorkspaceSymbolParams params) {
  return getTextDocumentService().symbol(params);
}
```

### 優先度

⭐⭐ **中** — 複数ファイル対応が必要

---

## LSI-4: formatting（ドキュメント整形）

### 概要

メニュー「フォーマット」でドキュメント全体を自動整形。

簡易版: インデント/空白を正規化。

### 仕様

**LSP 対応メソッド**: `textDocument/formatting`

**入力**: `DocumentFormattingParams` = `{ uri, options }`

```java
record FormattingOptions(
    Integer tabSize,            // インデント幅（通常 2 or 4）
    Boolean insertSpaces        // tab vs space
) {}
```

**戻り値**: `TextEdit[]`

### 処理フロー

1. **パース**: ドキュメント全体をパース
2. **整形ルール適用**:
   - 行頭の余分なスペース削除
   - ブロック内のインデント統一
   - キーワード周りの空白正規化
3. **TextEdit リスト構築**: 元のテキスト → 整形後のテキスト への diff を TextEdit[] に変換

### 実装詳細

```java
@Override
public CompletableFuture<List<? extends TextEdit>> formatting(
    DocumentFormattingParams params) {

  String uri = params.getTextDocument().getUri();
  ExtDocumentState state = server.extDocuments.get(uri);
  if (state == null) {
    return CompletableFuture.completedFuture(Collections.emptyList());
  }

  // 1. ドキュメント全体を整形
  String formatted = formatDocument(state.content(), params.getOptions());

  // 2. 変更箇所を TextEdit に変換
  List<TextEdit> edits = new ArrayList<>();
  if (!formatted.equals(state.content())) {
    // 全置換: 最初から最後まで replace
    Position start = new Position(0, 0);
    Position end = offsetToPosition(state.content(), state.content().length());
    TextEdit edit = new TextEdit(new Range(start, end), formatted);
    edits.add(edit);
  }

  return CompletableFuture.completedFuture(edits);
}

/** ドキュメント整形ロジック（簡易版） */
private String formatDocument(String content, FormattingOptions options) {
  // 実装例: 行頭空白の正規化
  StringBuilder result = new StringBuilder();
  String[] lines = content.split("\n", -1);

  int indentLevel = 0;
  int indentSize = options.getTabSize() != null ? options.getTabSize() : 2;
  boolean useSpaces = options.getInsertSpaces() != null ? options.getInsertSpaces() : true;
  String indentStr = useSpaces ? " ".repeat(indentSize) : "\t";

  for (String line : lines) {
    String trimmed = line.stripLeading();

    // ブロック開始/終了で indent レベルを調整
    if (trimmed.startsWith("}") || trimmed.startsWith("endif")) {
      indentLevel = Math.max(0, indentLevel - 1);
    }

    // インデント再構築
    if (!trimmed.isEmpty()) {
      result.append(indentStr.repeat(indentLevel)).append(trimmed);
    }
    result.append("\n");

    // ブロック開始でインデント増加
    if (trimmed.endsWith("{") || trimmed.startsWith("if")) {
      indentLevel++;
    }
  }

  return result.toString();
}
```

### バリデーション

- フォーマット後のパース検証（整形によるエラー防止）
- 元のテキストと同じ場合は空のリスト返却

### 実装難度

**M（Medium）** — 整形ルール設計が複雑

### 実装箇所

**tinyexpression 側**:
- `TinyExpressionP4LanguageServerExt.ExtTextDocumentService#formatting()`
- （オプション）`rangeFormatting()` — 選択範囲のみ整形
- （オプション）`onTypeFormatting()` — 入力時自動整形

### 優先度

⭐ **低** — オプション機能（developers は通常 Prettier 等を使用）

---

## LSI-5: diagnostics（TE系エラー）

### 概要

パースエラーやセマンティックエラーに対して、独自のコード（TE0xx）を付与して報告。VS Code 上でクイックフィックスやドキュメント参照を可能にする。

### 仕様

**エラーコード体系**:
- **TE001**: パースエラー（文法間違い）。P4 文法への書き換えを提案。
- **TE002**: 未定義変数の参照。
- **TE003**: 未定義メソッドの呼び出し。
- **TE004**: 重複した定義。
- **TE005**: 型の不一致（将来的な型システム導入時）。

### 表示イメージ

```
$x = 10;
$y = $x + $z;
          ^^
          Error (TE002): Undefined variable '$z'
```

### 処理フロー

1. **パース/セマンティック解析**: `TinyExpressionP4Mapper.parse` および `ScopeStore` による解析。
2. **診断生成**: `SymbolDiagnostic` を LSP `Diagnostic` に変換。
3. **コード付与**: `Diagnostic.setCode()` に `TE0xx` をセット。
4. **クライアント通知**: `publishDiagnostics` を通じて通知。

---

## LSI-6: completion（変数・キーワード補完）

### 概要

カーソル位置で `$` を入力した際、定義済みの変数をリストアップ。またキーワード（`if`, `match` 等）も補完。

### 仕様

**変数の扱い**:
- 識別子のプレフィックスとして `$` を使用。
- 補完候補のラベルには必ず `$` を含める（例: `$age`, `$name`）。

### 表示イメージ

```
var $age set 42;
if ($a|)
      ↓ (Ctrl+Space)
    候補:
    $age (Variable)
    as (Keyword)
```

### 処理フロー

1. **プレフィックス取得**: `wordAt()` により現在の入力（`$` 等）を取得。
2. **候補収集**:
   - `ScopeStore.declarations` からシンボル名を収集。
   - `$identifier` 形式でラベルを作成。
3. **フィルタリング**: プレフィックスに一致する候補のみを返却。

---

## 実装優先度表

| ID | 機能 | 規模 | 難度 | 優先度 | 依存 | ステータス |
|----|------|------|------|--------|------|-----------|
| LSI-1 | definition | S | 低 | ⭐⭐⭐ | ScopeStore | ✅ 完了 |
| LSI-2 | linkedEditingRange | S | 低 | ⭐⭐⭐ | ScopeStore | ✅ 完了 |
| LSI-3 | workspaceSymbol | M | 中 | ⭐⭐ | ScopeStore, 複数doc管理 | ✅ 完了 |
| LSI-4 | formatting | M | 中 | ⭐ | 整形ルール設計 | ✅ 完了 |
| LSI-5 | diagnostics | S | 低 | ⭐⭐⭐ | ScopeStore (SymbolDiagnostic) | ✅ 完了 |
| LSI-6 | completion | S | 低 | ⭐⭐⭐ | ScopeStore (declarations) | ✅ 完了 |

---

## 実装計画

### Phase A（優先度⭐⭐⭐）
- **LSI-1**: definition — 基本的なナビゲーション
- **LSI-2**: linkedEditingRange — リファクタリング支援

### Phase B（優先度⭐⭐）
- **LSI-3**: workspaceSymbol — ワークスペース横断検索

### Phase C（優先度⭐）
- **LSI-4**: formatting — 簡易整形（オプション）

---

## 開発チェックリスト

### LSI-1: definition
- [ ] `DefinitionParams` の position → word 変換
- [ ] `ExtDocumentState.declarations` から検索
- [ ] offset → Range 変換ロジック
- [ ] LocationLink 構築
- [ ] テスト: カーソル位置の変数 → 定義へジャンプ

### LSI-2: linkedEditingRange
- [ ] `LinkedEditingRanges` 構築
- [ ] 宣言 + 参照の Range リスト化
- [ ] 重複排除
- [ ] テスト: F2 で同時編集

### LSI-3: workspaceSymbol
- [ ] `server.extDocuments` 全走査
- [ ] query prefix マッチング
- [ ] `SymbolKind` 推定ロジック
- [ ] 結果上限（100件）設定
- [ ] テスト: Ctrl+T で検索

### LSI-4: formatting
- [ ] 簡易整形ルール実装（インデント統一）
- [ ] TextEdit diff 生成
- [ ] パース検証（整形後のエラーチェック）
- [ ] テスト: Alt+Shift+F で整形

---

## 参考資料

- [LSP Specification — textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/specification-3-17-0/#textDocument_definition)
- [LSP Specification — textDocument/linkedEditingRange](https://microsoft.github.io/language-server-protocol/specifications/specification-lsp-all-in-one/#textDocument_linkedEditingRange)
- [LSP Specification — workspace/symbol](https://microsoft.github.io/language-server-protocol/specifications/specification-3-17-0/#workspace_symbol)
- [LSP Specification — textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/specification-3-17-0/#textDocument_formatting)

---

## 関連 Issue

- **型システム依存機能**: `implementation`, `typeDefinition`, `declaration` は実装不可（型システムが必要）
- **複数ファイル対応**: `workspaceSymbol` の実装時に複数ドキュメント管理を確認
