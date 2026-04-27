# P4 Feature Gap Analysis: calculator-lsp 0.2.31 vs tinyexpression-p4-lsp

Last updated: 2026-04-24

## 概要

旧拡張（`calculator-lsp-vscode` VSIX 0.2.31）と新P4拡張（`tinyexpression-p4-lsp-vscode` VSIX 0.2.3）の機能差分。
P4拡張へ機能を移植する際の作業基準として使用する。

2026-04-24 時点では、P4 側に strict `match` typing (`TE025`)、preferred-root hover、parser exact/probe 観測が追加済み。
以下の表はその反映後の差分として読む。

---

## 1. エラー診断（TE コード）

| 観点 | 0.2.31 | P4現行 |
|------|--------|--------|
| TEコード数 | TE001〜TE024（24種） | TE001〜TE025 |
| 検出方法 | 15以上のヒューリスティック関数で文脈解析 | パーサーネイティブヒント + ScopeStoreセマンティクス |
| スコープ診断 | ❌ | ✅（未宣言・重複宣言の検出） |
| 実用上の差 | TEエラーが細かく出る | `TE025` は追加済みだが、編集支援系 TE はまだ不足が多い |

### 0.2.31 で実装されている主なTE診断
- TE003: 文字列クォート不正
- TE004: 閉じ括弧欠如
- TE005: 閉じブレース欠如
- TE006: セミコロン欠如
- TE008: 全角文字混入
- TE009: 予期しないトークン
- TE010: トークン不一致
- TE011: if条件が非boolean
- TE013: match構文不正
- TE021: 未定義メソッド
- TE022: 未定義変数（カタログ連携あり）
- TE023: 演算子表記不正（`&&`→`&` 等）
- TE024: partialKey変数のサフィックス欠如（カタログ連携）
- TE025: `match` case value の strict typing 不正

---

## 2. 入力補完

| 補完の種類 | 0.2.31 | P4現行 |
|-----------|--------|--------|
| キーワード | パーサー定義から動的取得 | ハードコード15語 |
| 変数カタログ（外部ファイル） | ✅ exactName / prefixWithSuffix | ❌ |
| 変数（ドキュメント内） | ✅（import alias抽出含む） | ✅（ScopeStore） |
| メソッド名 | ✅（TinyExpressionParserMethodCatalog） | ✅（ScopeStore） |
| **import文フル補完** | ✅ `import sample.v1.Cls#method as alias;` まで | ❌（`import` まで） |
| **行末セミコロン補完** | ✅（parseFailureHint由来） | ❌ |
| `$` トリガー補完 | ✅（変数候補を即提示） | ❌ |
| トリガー文字 | パーサー定義から取得 | 未設定 |

---

## 3. クイックフィックス（コードアクション）

**最大の差分**。0.2.31では18種以上のTE修正が自動提案される。

| TE コード | 0.2.31 の修正内容 | P4現行 |
|-----------|----------------|--------|
| TE003 | クォート正規化（`'...'`→`"..."` 等） | ❌ |
| TE004 | `)` 挿入 | ❌ |
| TE005 | `}` 挿入 | ❌ |
| TE006 | `;` 挿入 | ❌ |
| TE007 | descriptionクォート正規化 | ❌ |
| TE008 | 全角→半角変換 | ❌ |
| TE009 | ブレース・カンマ等構造修正 | ❌ |
| TE010 | `{` 挿入等 | ❌ |
| TE013 | カンマ挿入、`->` 後RHS修正、matchブレース修正 | ❌ |
| TE021 | メソッド名のtypo修正（候補表示） | ❌ |
| TE022 | 変数名のtypo修正（候補表示） | ❌ |
| TE023 | `&&`→`&`、`\|\|`→`\|`、演算子修正 | ❌ |
| TE024 | `_<suffix>` 付加 | ❌ |
| TE001 | ❌（なし） | ✅ if-else構文修正・callキーワード追加 |

---

## 4. ホバー

| 観点 | 0.2.31 | P4現行 |
|------|--------|--------|
| エラー上 | エラーメッセージ表示 | エラー＋期待トークン（Markdown） |
| 式の計算値 | ✅ `= 結果` 表示 | ❌ |
| シンボル（変数名）上 | ❌ | ✅ `$var: type` をMarkdown表示 |
| シンボル（メソッド名）上 | ❌ | ✅ `method() → type` をMarkdown表示 |
| AST root / parse観測 | ❌ | ✅ AST root 表示、strict match typing 反映 |

---

## 5. コードレンズ

| 観点 | 0.2.31 | P4現行 |
|------|--------|--------|
| 配置 | ファイル先頭 1個（ドキュメント全体の状態） | 代入文ごとに最大5個（インライン） |
| 内容 | エラー概要 or `= 計算結果` | `= 型推論結果` |
| 式評価 | ✅（AST解析） | ✅（名前パターンベース） |

---

## 6. その他LSP機能

| 機能 | 0.2.31 | P4現行 |
|------|--------|--------|
| 定義へジャンプ | ❌ | ✅（ScopeStore） |
| 参照検索 | ❌ | ✅ |
| リネーム | ❌ | ✅ |
| リンク編集範囲 | ❌ | ✅（宣言と参照の同期編集） |
| ドキュメントシンボル | ❌ | ✅（階層表示） |
| ドキュメントハイライト | ❌ | ✅ |
| シグネチャヘルプ | ❌ | ✅ |
| インレイヒント | ❌ | ✅（型ヒント） |
| フォールディング | ❌ | ✅（if/matchブロック） |
| セマンティックトークン | ✅（正規表現ベース） | ✅（パーサーベース、正確） |
| フォーマット | ❌ | ✅（簡易インデント） |
| コールヒエラルキー | ❌ | ✅ |
| ワークスペースシンボル | ❌ | ✅ |

---

## 7. 変数カタログシステム（0.2.31 のみ）

### ファイルフォーマット（現状4種混在）

| ファイル | フォーマット |
|---------|------------|
| `fa-allowed-variables-cf-variable.txt` | `variable\|type` |
| `fa-allowed-variables-checkkind.txt` | `variable\|description` |
| `nimt-allowed-variables-cfvar.txt` | `variable\|type\|api\|description` |
| `nimt-allowed-variables-checkkind.txt` | `variable\|api\|description` |

### カタログ機能
- exact match変数（`$varName`）
- prefixWithSuffix変数（`$prefix_*`）
- context分類（NIM/FA等）
- 最近接候補サジェスト（TE022 / TE021）
- 外部ファイル or RuntimeCatalogProvider（in-memory）対応

---

## 8. 移植優先度

### 高優先度（ユーザーが「出なくなった」と感じる機能）
1. **変数カタログ補完** — UBNF拡張で定義 + Resolver概念導入
2. **TEエラー診断の復活** — TE006（セミコロン）、TE022（未定義変数）等
3. **クイックフィックス** — セミコロン挿入、括弧補完など日常的なもの
4. **import文フル補完**

### 中優先度
- エラーメッセージ多言語化（errorCode → i18n）
- 式の評価値ホバー表示
- `$` トリガー変数補完
- ホバーでの計算値表示

### 低優先度（P4で既に上位互換あり）
- セマンティックトークン（P4の方が正確）
- コードレンズ（P4でインライン対応済み）

---

## 9. 2026-04 時点で解消済みの項目

1. strict `match` typing のセマンティック診断
   - `TE025` を追加し、runtime / LSP の条件を統一
2. `match` / `if` / ternary の root 選択
   - preferred AST root を導入し、shallow root への吸い込みを低減
3. DAP / runtime observability
   - `_tinyP4ParserExact` と `_tinyP4ParserProbeMode` を公開
4. context-light だけでなく contextful parity の検証
   - `.in(...)` と `inDayTimeRange(...)` を parity corpus に追加

---

## 関連ファイル

- 旧LSPサーバー: `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/CalculatorLanguageServer.java`
- 旧AST解析: `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/CalculatorAstAnalyzer.java`
- 旧変数カタログ: `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/TinyExpressionVariableCatalog.java`
- P4 LSPサーバー: `tools/tinyexpression-p4-lsp-vscode/src/main/java/org/unlaxer/tinyexpression/lsp/p4/TinyExpressionP4LanguageServerExt.java`
