# TinyExpression LSP 診断/Quick Fix 仕様

更新日: 2026-02-27
対象: `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/CalculatorLanguageServer.java`

## 1. 目的

この仕様書は、TinyExpression LSP が

- どの条件でどの診断コード (`TE###`) を出すか
- どの診断に Quick Fix が出るか
- どの修正が適用されるか

を実装ベースで定義する。

## 2. 診断の流れ

1. パース結果 (`ParseFailureDescription`) を作る  
2. `resolveErrorCatalogEntry(...)` で `TE###` にマッピング  
3. `formatCatalogMessage(...)` で表示文を作る  
4. `codeAction(...)` で `TE###` ごとの Quick Fix を生成する

補足:
- 一部の外部診断メッセージ（例: `開き括弧が閉じられていません`）は `inferCatalogCodeFromMessage(...)` で `TE004/TE005` に補完する。

## 3. 優先順位（ノイズ抑制）

`unexpected characters` (`TE009`) に落とす前に、次を優先して検出する。

- `if (...)` / `else` の `{` 欠落 (`expected '{'` -> `TE010`)
- `->` 右辺欠落 (`expected expression after '->'` -> `TE013`)
- `match` のケース区切りカンマ欠落 (`missing ',' between match cases` -> `TE013`)
- `default` 前カンマ欠落 (`missing ',' before default case` -> `TE014`)
- `var` 宣言の `;` 欠落 (`TE006`)

## 4. TEコードとQuick Fix

### 4.1 Quick Fix あり

| Code | 主な検出条件 | Quick Fix |
|---|---|---|
| `TE003` | 文字列で `"` を使用 | `"` -> `'` に置換 |
| `TE004` | `)` 欠落 | 未閉じ `(` 行末へ `)` 挿入 |
| `TE005` | `}` 欠落 | `}` 挿入 |
| `TE006` | `var/variable` 行の `;` 欠落 | 宣言行末へ `;` 挿入 |
| `TE007` | `description='...` の閉じ忘れ | `'` を補完 |
| `TE008` | 全角記号混入 | 全角 -> 半角へ正規化 |
| `TE009` | 余分トークン（フォールバック） | 構造ヒューリスティック修正（`{`/`,`/`-> rhs`） |
| `TE010` | 期待トークン不一致（特に `{`） | `TE009` と同等の構造ヒューリスティック + `{` 挿入 |
| `TE013` | `match` 構文不正 | `,` 挿入 / `->` 右辺補完 |
| `TE016` | `import` の alias 不足 | `import ... as alias;` へ補完 |
| `TE017` | 変数宣言ヘッド不正 | 変数名へ `$` 補完 |
| `TE018` | `as type $name` | `$name as type` に並べ替え |
| `TE019` | `get(...).orElse(...)` 不正 | `orElse(...)`/`)`/`.orElse(0)` 補完 |
| `TE021` | 未定義メソッド | 候補名へリネーム |
| `TE022` | 未定義変数 | 候補変数へリネーム |
| `TE023` | `&&/||/$method/...` 記法不正 | `&&->&`, `||->|`, `$`除去, 右辺 `true` 補完 |
| `TE024` | partialKey 変数 suffix 不足 | `_<suffix>` 補完 |

### 4.2 Quick Fix なし（現状）

| Code | 理由 |
|---|---|
| `TE002` | 意図する値（変数/文字列/関数）が自動確定できない |
| `TE011` | 条件式の意味論が複数あり自動補完が危険 |
| `TE014` | `default` 位置/区切り補完は文脈依存が高い |
| `TE015` | 関数引数修正は候補が複数で自動確定困難 |
| `TE020` | 汎用フォールバックで修正候補が限定できない |

## 5. よくあるケース

### 5.1 `if (...)` 後の `{` 欠落

入力:

```tinyexpression
if(external returning as boolean checkDigits($input))
  1
}else{
  ...
}
```

期待:
- 診断: `TE010`（`TE009` より優先）
- Quick Fix: `if(...)` の直後に `{` を挿入

### 5.2 `match` ケース区切りの `,` 欠落

入力:

```tinyexpression
match{
  $name=='肉太郎' -> 1
  $didCheck -> 2,
  default -> 0
}
```

期待:
- 診断: `TE013`
- detail: `match case の区切りに ',' が必要です`
- Quick Fix: `$didCheck` 行先頭位置に `,` 挿入

### 5.3 `->` 右辺欠落

入力:

```tinyexpression
match{
  $name=='肉太郎' -> ,
  default -> 0
}
```

期待:
- 診断: `TE013`（可能な限り `TE009` を避ける）
- Quick Fix: `-> 0` 補完

## 6. 実装メモ

- CRLF (`\r\n`) で `TE017` が誤検出されないよう、行単位判定は末尾 `\r` を正規化する。
- `set if not exists` の `if` は制御構文 `if (...)` と区別する。
- `TE009` は最終フォールバックとして残し、構造的に特定できるものは `TE010/TE013/TE014/TE006` を優先する。
