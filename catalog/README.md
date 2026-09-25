# 言語カタログ（catalog/tinyexpression-catalog.json）

tinyexpression のユーザー向け言語知識（変数・関数・キーワード・external・エラーコード・設定項目）の
**正本**（issue #201）。スキーマは [`tinyexpression-catalog.schema.json`](tinyexpression-catalog.schema.json)。

読む側:

| 読む側 | 使い方 |
|---|---|
| VSIX の LSP サーバ（`tools/tinyexpression-p4-lsp-vscode`） | server jar に `/tinyexpression-catalog.json` として同梱。`CatalogProvider` が hover（変数・関数・キーワード）、補完（関数 snippet と説明、キーワード、曜日、変数）、診断（TE / FI の文言、parse 失敗から TE コードを選ぶ規則）に使う。設定 `tinyExpressionP4Lsp.catalog.overridePath` の JSON（部分でよい）を上書きマージする |
| playground（`playground/`） | 補完・hover・診断の TE コードと文言、評価エラーの説明（`runtimeErrors`）、CalculationContext の変数ヒント |
| 派生ファイル | `node catalog/scripts/generate-derived.mjs` が `config/*.tecatalog`（旧形式の変数一覧）と `src/main/resources/error-catalog.json` を生成。CI は `--check` で同期を検査 |

編集したら `node catalog/scripts/generate-derived.mjs` を実行して派生ファイルとカタログの整形を更新する。

## 棚卸し（段階 1）: 移行前の出所と件数

| 種類 | 移行前の定義場所 | VSIX での見え方 | カタログの節 | 件数 |
|---|---|---|---|---:|
| 変数（FA CF 変数） | `tools/tinyexpression-p4-lsp-vscode/config/fa-variables.tecatalog`（上流: `tools/calculator-lsp-vscode/config/fa-allowed-variables-cf-variable.txt`） | `$` 補完・hover・TE022（extension が `-Dtinyexpressionp4.catalog.path` で渡していた） | `variables`（group `fa-variables`） | 344 |
| 変数（FA CheckKind 由来 `calculated_*`） | `config/fa-checkkind.tecatalog` | 同上 | `variables`（`fa-checkkind`） | 201 |
| 変数（NIM CF 変数） | `config/nimt-cfvar.tecatalog`（`exact` 732 + `prefixWithSuffix` 7） | 同上（`prefixWithSuffix` は生成サーバが解釈できず説明が `_` になっていた） | `variables`（`nimt-cfvar`、`match: prefixWithSuffix` を正しく解釈） | 739 |
| 変数（NIM CheckKind） | `config/nimt-checkkind.tecatalog` | 同上 | `variables`（`nimt-checkkind`） | 87 |
| エラーコード TE001〜TE025 | `TinyExpressionP4LanguageServerExt.java` の `ERROR_CATALOG`（ハードコード） | 診断メッセージ `[TE0xx] … 修正例: …`、data.fix | `errorCodes` | 25 |
| エラーコード（別文言） | `src/main/resources/error-catalog.json`（TE001/004/005/006 の 4 件、どこからも読まれていなかった） | なし | 同じコードの `variants` に保存。ファイル自体は派生物に変更 | 4 |
| TE025 / TE022 の本文 | Ext の文字列連結 | 診断メッセージ | TE025 `templates`（2 種）、TE022 `templates` | 3 |
| FI001 / FI002 | Ext の英語文字列 | FormulaInfo 診断 | `errorCodes`（`templates.DEFAULT`） | 2 |
| TE コード選択規則 | Ext の `resolveCode`（`if` の連鎖） | 構文エラーのコード | `diagnosticRules`（共通 14 + LSP 専用 1 + playground 専用 2） | 17 |
| 関数（snippet） | Ext の `FUNCTION_SNIPPETS`（説明なし） | 補完（snippet のみ） | `functions`（kind `function`、署名・説明・例を追加） | 26 |
| ドット形式メソッド | 文法（`.in` `.startsWith` など）と `P4TypedAstEvaluator` | 補完なし | `functions`（kind `method`） | 8 |
| キーワード | Ext の `COMPLETION_KEYWORDS` | 補完 | `keywords` | 23 |
| ブロック snippet | Ext の `BLOCK_SNIPPETS` | 補完 | `keywords[].snippet` | 6 |
| 閉じた値（曜日） | Ext の `COMPLETION_VALUES` | 補完 | `values` | 7 |
| FormulaInfo の項目 | Ext の `METADATA_FIELD_NAMES`、`RESULT_TYPE_VALUES`、`EXECUTION_BACKEND_VALUES`、`P4ParserEngine`（`p4Engine`）、`docs/language-guide.ja.md` の表 | メタデータ行の補完、FI002 | `settings`（scope `formulaInfo`） | 12 |
| CalculationContext の設定 | `EmbeddedFunction` / `CalculationContext`（`nowHour` `nowDayOfWeek` `angle`） | なし | `settings`（scope `context`） | 3 |
| external | 定義なし（`import … as` / `external returning as …` は文書内で宣言） | 文書内の宣言だけ | `externals`（差分テスト用のクラスを例として 4 件） | 4 |
| 評価時の例外 | `rust/tinyexpression-rs/src/runtime/mod.rs` の `ErrorKind`（Java の例外名） | なし | `runtimeErrors`（playground の結果パネル） | 9 |
| 文法の `@catalog(context='variable')` | `grammar/tinyexpression-p4.ubnf` の `VariableRef`（1 箇所） | 生成サーバに `CatalogResolver`（`.tecatalog` 読み込み、`catalogCompletion` / `catalogHover`）を生成させるだけ。文言は持たない | Ext が `CatalogProvider.variableResolver()` をこの口に差す | — |
| VS Code 設定 | `package.json` の `contributes.configuration` | 設定画面 | 対象外（拡張の設定は package.json が正本。`catalog.overridePath` を追加） | — |
| 言語ガイド | `docs/language-guide.ja.md`、`walkthrough/*.md` | コマンド・walkthrough | 対象外（文章。関数・変数の説明は含まない） | — |

旧データは日本語のみ（VS Code 設定の説明を除く）。カタログの文字列は `{"ja": …, "en": …}` で、今回書き足した
関数・キーワード・設定の説明には英語も付けた。`config/*.tecatalog` はカタログから**バイト単位で同一**に再生成できる
（移行で 1 行も失っていないことの検査を兼ねる）。TE001〜TE025 の完全な文言は
`tools/tinyexpression-p4-lsp-vscode/src/test/resources/catalog-golden/te-full-messages.txt`（移行前の Java から抽出）と
golden テストで一致を確認している。

## スキーマの概要

| 節 | 1 件の形 |
|---|---|
| `variableGroups` | `id`, `context`（FA / NIM）, `description`, `tecatalog`（派生ファイルの場所）, `upstream` |
| `variables` | `name`, `match`（`exact` / `prefixWithSuffix` + `separator` + `minSuffixLength`）, `type`, `description`, `context`, `group`, `examples?`, `source?` |
| `functions` | `name`（メソッドは `.name`）, `kind`（`function` / `method`）, `signature`, `returns`, `snippet?`（LSP snippet）, `description`, `examples`, `reads?`（読む context 変数） |
| `keywords` / `values` | `name`, `snippet?` / `type`, `value`, `description` |
| `externals` | `class`, `method`, `params[{name,type}]`, `returns`, `description`, `example?` |
| `errorCodes` | `code`（`TE\d{3}` / `FI\d{3}`）, `severity`, `message`, `fix`, `templates?`（`{placeholder}` 付きの全文）, `variants?`（旧文言） |
| `diagnosticRules` | 上から順に評価: `expected`（parser が期待した終端記号）/ `hintContains` / `snippetStartsWith` / `leadingEndsWith` / `snippetMatches` → `code`。`consumers` で LSP / playground に限定、`onlyIfNoExpressionStart` |
| `runtimeErrors` | `kind`（Java 例外名）, `description`, `fix` |
| `settings` | `name`, `scope`（`formulaInfo` / `context`）, `type`, `values?`, `default?`, `description`, `note?` |

`defaultErrorCode`（`TE020`）はどの規則にも当たらない構文エラーのコード。
