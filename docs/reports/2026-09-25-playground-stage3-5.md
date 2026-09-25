# playground 段階 3〜5: 評価トレース、カタログ編集、VSIX への同梱（issue #201）

2026-09-25。段階 1〜2（#203: 言語カタログ、playground MVP）の続き。ここは repo 内に残す根拠（API・判断・計測）。

## 1. 段階 3 — 評価トレース / debug

### Rust

| 物 | 場所 |
|---|---|
| hook と記録器 | `rust/tinyexpression-rs/src/runtime/trace.rs`（`TraceHook { on_enter(TraceSite), on_exit(Result<&Value,&EvalError>) }`、`TraceRecorder`） |
| walker への差し込み | `runtime/walk.rs`: `eval` を `eval_node` と「hook があれば `traced` で包む」に分け、`BinaryExpr` の文字列オペランド（`operand` / `string_leaf`）も `TraceSite::Leaf` として記録 |
| API | `Program::eval_tree_traced(context, host, &mut dyn TraceHook)`、`api::eval_trace_json`（= `eval_context_json` + `"trace"`）、`api::eval_formula_trace_json` |
| 出口 | CLI `eval --trace [FILE|-]` / `eval-context --trace [FILE|-]`、C ABI・wasm `te_eval_trace`（追加のみ、`TE_ABI_VERSION` は 1 のまま）、JS binding `evalTrace` |

trace の形: `{"steps","recorded","truncated","root":{"kind","span":[start,end],"leaf"?,"value"?|"error"?,"text"?,"children"}}`。
`span` は code point。評価されなかった分岐は出ない（子の順 = 評価順）。記録は 20,000 ステップまで（`truncated`）。
JSON は反復で書く（深い式で wasm のスタックを使わない）。ノード名は `Debug` 出力の先頭だけを読む（部分木を整形しない）。

- **closure 版は無変更**。hook は tree walker にだけあり、trace を頼まない評価（`eval-context`・playground の通常評価）は
  `trace: None` の分岐 1 つだけ増える。
- **結果を変えない証拠**: `tests/java_differential.rs` が全行を tree / closure / **trace 付き tree** の 3 通りで評価し、
  trace 付きと無しの outcome・例外メッセージの一致と、trace の整合（根の outcome = 結果、全ステップが閉じている、
  失敗は失敗した子から伝わる）を要求する。12,609 行中、生成できる 12,453 行で一致。
  playground の parity smoke も Java golden 11,409 行すべてを `te_eval_trace` でも評価し、`trace` 以外の応答が
  `te_eval_context` と同一であることを確認（307,554 ステップ）。
- **性能**（`examples/eval-bench`、release、tree walker の µs/評価、2 回ずつ）: complex.tiny 1.55 → 1.54–1.55、
  complex-x16 26.6–26.7 → 27.7–28.4（+4% 程度、同一マシンの揺れと同程度）。closure 版は差なし。
- ubnfc の文法デバッガ（D-067）との対応: `on_enter` の順が D-067 のステップ列（型付き AST の前順）、入ったまま出ていない
  ステップの鎖が `stackTrace`、`on_exit` の値は D-067 が `EvaluatorBridge` に任せている部分。

### playground

- **Trace パネル**: 「トレースを取る」/「評価ごとに記録」（既定 off = trace を頼んだときだけ `te_eval_trace`）。
  木は遅延描画、同じ範囲・同じ値のラッパー（`BinaryExpr` の入れ子など）は畳む（切替可）。行クリックでエディタの範囲を強調。
- **step モード**: ⏮ ◀ ▶ ⏭（←/→）。イベントは「評価開始（前順）」と「評価完了（値つき）」で、各時点の評価スタック
  （外側の式と済んだ子の値）を表示。ブレークポイントは無し（issue の段階 3 の範囲）。
- **失敗**: 例外を出したいちばん内側のステップを赤枠・波線で示し、例外名とカタログ `runtimeErrors` の説明・修正のヒント。
  構文エラーは従来どおり TE コード付き診断（trace は `null`）。
- 文字列連結のオペランドは AST が自分の span を持たない（連結全体の span になる、`generated/compat.rs` の仕様）ため、
  UI 側で親のソース内を前の兄弟の後ろから探して範囲を補う（`src/trace.js`）。

## 2. 段階 4 — カタログ編集と VSIX への流れ

- **Catalog パネル**（`src/catalog-panel.js`）: 節ごとの一覧・検索、説明（ja / en）・メッセージ・修正のヒント・例の編集、
  追加（変数・関数・エラーコードほか全節）、エラーコードの variant 追加、その他の項目は「JSON で編集」。編集は
  補完・hover・診断に即反映し、override として localStorage に保存（自分のブラウザだけ）。
- **検証**: スキーマを build 時に ajv standalone で JS 化（`scripts/build-validator.mjs` → `src/generated/`、commit しない）。
  実行時に `new Function` を使わないので webview の CSP（`script-src 'nonce-…' 'wasm-unsafe-eval'`）でも動く。
  整合性規則は `generate-derived.mjs` から `catalog-lib.mjs` の `structuralProblems` に移して共用。
- **書き出し**: 全体 JSON（`formatCatalog` = リポジトリの整形）、override JSON（変更・追加だけ）、unified diff（Myers、
  `patch` で当たることを確認）、JSON Patch（RFC 6902、entry 単位の replace / add）。override のマージは LSP の
  `CatalogProvider.withOverride` と同じ規則（key で置換・新規は末尾・`diagnosticRules`/`defaultErrorCode` は丸ごと）を
  `catalog/scripts/catalog-edit.mjs` に実装。override は削除を表せないので、名前変更・削除はパネルで警告し全体 JSON / PR へ誘導。
- **PR**: token（`input type=password`、変数にだけ保持、保存しない、送信先は api.github.com のみ）があれば git data API で
  カタログと**派生ファイル**（`.tecatalog`、`error-catalog.json`）を 1 commit にして新ブランチ → PR。PR 作成に失敗しても
  compare ページ（title / body 付き）を出す。token 無しは全体 JSON をコピーして GitHub の Web エディタを開く
  （派生ファイルは CI の `--check` が要求するので手元で再生成するよう案内）。API の呼び順は `check.mjs` が mock で固定。
- **VSIX**: コマンド「TinyExpression: Import catalog from playground export」（Explorer の `.json` 右クリックにも）が
  書き出しを検査（`checkCatalogExport`: 既知の節は配列、未知キーは拒否、全体 / 部分を判定）して
  `.vscode/tinyexpression-catalog.override.json` に書き、`catalog.overridePath` を workspace 設定にし、LSP を再起動する。
  相対パスの `overridePath` は最初の workspace folder 基準に解決するよう変更。ステータスバー `TE catalog: bundled | bundled + <file>`
  （クリックで playground・import・override を開く・bundled に戻す）。
- **golden**: `TinyExpressionP4CatalogProviderTest.anImportedPlaygroundOverrideChangesHover`（パネルの書き出しそのものである
  `catalog-golden/playground-export.override.json` を overridePath にすると sqrt の hover / 補完 doc、`$riskScore` の hover、
  TE006 の文言が変わり、触っていない TE004 は同梱のまま）と `anImportedFullPlaygroundExportChangesHover`（全体 JSON でも可）。
  モジュールのテスト 11 件 green（ローカル `mvn test -Dtest=TinyExpressionP4CatalogProviderTest`）。
- **round trip（CI）**: `playground/scripts/catalog-roundtrip.mjs` — 無編集の書き出しがリポジトリのファイルとバイト一致、
  override / diff / patch が空、standalone validator と ajv で valid、派生 6 ファイルがバイト一致、fixture 編集の override が
  golden と一致し、マージ・patch で編集後に戻る、編集で変わる派生ファイルがちょうど 3 つ。

## 3. 段階 5 — VSIX の webview

- 「TinyExpression: Open playground」（エディタタイトルの ▶）: `playground-dist/`（= `playground/dist`、UI のフォーク無し）を
  webview に出す。`rewritePlaygroundHtml` が `<base href>`（`asWebviewUri`）・CSP・nonce を入れ `crossorigin` を外す。
  アクティブな文書から、選択範囲 / FormulaInfo ならカーソルのブロックの `formula:` / それ以外は全文を読み込み、
  FormulaInfo 全体は FormulaInfo パネルへ。有効なカタログ（override 込み）も送る。
- メッセージ: `ready` → `init`、`saveCatalog`（override / 全体はワークスペースへ保存して適用、diff 等は保存ダイアログ）、
  `openExternal`（https のみ）、`copy`。playground 側は `src/host.js`（`acquireVsCodeApi` が無ければ何もしない）。
- 検証: 実ブラウザ（headless Chromium）で、同じ書き換え関数と CSP の下に `acquireVsCodeApi` の代役を置いて開き、
  wasm 読み込み・trace・override 付きの hover・`saveCatalog` 送信までエラー無しを確認。VS Code 本体での起動確認はしていない
  （GUI 無しの環境のため）。拡張の純粋部分は `npm test`（node:test、7 件）。
- **サイズ**: VSIX 12,106,177 B（playground 無し 11,509,564 B、+0.6 MB）。同梱物は `tinyexpression.wasm` 2,124,172 B、
  bundle 811 KB、CSS 10 KB。server jar 11.8 MB が大半。
- 同梱の仕組み: `vscode:prepublish` が `scripts/copy-playground.mjs` を実行。`playground/dist` が無いと案内ページだけ入る
  （`TE_REQUIRE_PLAYGROUND=1` なら失敗）。CI の playground ジョブが VSIX を作ってサイズを step summary に出し、
  `playground-dist` を artifact にし、Full verify（self-hosted、Rust 無し）はそれを受け取って `mvn verify` で VSIX を作る。
  `release-vsix.yml` は自分で wasm と playground を build する。

## 4. 判断（戻しやすさ）

- trace は tree walker だけ（closure 版に hook を入れると最適化の前提が崩れる。playground の評価経路も tree walker）。
- trace 既定 off（「頼んだときだけ」）。記録上限 20,000 ステップ。
- override の書き出し先は `.vscode/tinyexpression-catalog.override.json` 固定（workspace 設定と一緒に commit / 無視を選べる場所）。
- PR 作成は派生ファイル込みの 1 commit（CI の `generate-derived --check` を通すため）。
