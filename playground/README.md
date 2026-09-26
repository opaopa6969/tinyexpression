# tinyexpression playground

<https://opaopa6969.github.io/tinyexpression/> — 静的サイト（Vite + CodeMirror 6 + `tinyexpression.wasm`）。issue #201 の段階 2〜5
（段階 3: 評価トレース、段階 4: カタログ編集、段階 5: VSIX の webview に同じ build を同梱）。

```sh
cd playground
npm ci
npm run build:wasm   # rust/ から tinyexpression.wasm（release-small）を作って public/ へコピー
npm run dev          # http://localhost:5173/
npm run parity       # Java 差分 golden を playground の評価経路で照合（node）
npm run check        # カタログのスキーマ検証、サンプル、TE コード付き診断、trace、カタログ編集、エディタ（色分け・かっこ・FormulaInfo の補完/診断/hover）
npm run roundtrip    # カタログの無編集書き出しがバイト一致、派生ファイル一致、VSIX 用 golden override
npm run build        # dist/（GitHub Pages に置くもの、VSIX の playground-dist/ にもなる）
```

- 評価は `rust/examples/wasm/tinyexpression.mjs` の binding 経由で `te_eval_context` / `te_formula_info_context`
  （CalculationContext 付き評価の JSON リクエスト、[rust/README.md](../rust/README.md#calculationcontext-付き評価issue-201)）。
  パネルの状態からリクエストへの変換は `src/context.js` の `toRequest` だけで、parity smoke も同じ関数を通す。
- 補完: カタログの関数（snippet）・キーワード・曜日、カーソル位置で parser が期待する終端記号（`te_check` の
  `expected`）で絞り込み、`$` では CalculationContext の変数・式内の宣言・カタログの変数。
- hover: 変数（CalculationContext の値とカタログの説明）、関数（署名・説明・例）、キーワード。
- 診断: `te_check` の parse 失敗をカタログの `diagnosticRules` で TE コードにし、文言・修正のヒントもカタログから。
  CalculationContext にもカタログにも無い `$変数` は TE022（警告）。
- 状態（式・CalculationContext・FormulaInfo）はブラウザの localStorage にだけ保存する。
- 色分け（issue #212）: キーワード・関数・`$変数`・文字列・数値・コメント・```java ブロック。かっこは入れ子の深さごとに
  6 色（rainbow brackets）、対応しないかっこは赤の波線、カーソル位置のかっこと相手を枠で強調する。字句は
  `src/te-lexer.js`（code point 単位の走査、正規表現なし。文字列・コメント内のかっこは数えない）、装飾は
  `src/syntax-view.js`（FormulaInfo エディタと共通）。色は明暗どちらのテーマでも本文色とのコントラスト比 4.5 以上。

## FormulaInfo エディタ（issue #212）

FormulaInfo パネルは式エディタと同じ CodeMirror 6 のエディタ（VSIX の webview でも同じ build、`new Function`/eval なし）。
「読み込み」「実行」と localStorage 保存は従来どおり。

- 色分け: 既知のキー（カタログ `settings` の `scope: formulaInfo` と loader が読む `formula`・`hash`・`byteCode` 系・
  `siteId`・`checkKind`）と未知のキー（別の色＋点線）、`:`、`#` コメント行、`---END_OF_PART---` 行、値。
  `formula:` の値には式エディタと同じ tinyexpression の色分けとかっこの深さ色（式ごとに独立）。
  ブロックの外のゴミ行・閉じない終端行（`---END_OF_PART---` の後ろに文字がある行）は赤。
- 補完: 行頭でキー（`key:`）と `---END_OF_PART---`、`dependsOn:` の値で文書内の他ブロックの `calculatorName`、
  `executionBackend:` / `backend:` / `resultType:` / `numberType:` / `p4Engine:` でカタログの列挙値、
  `formula:` の値の中では式エディタと同じ補完（関数・キーワード・CalculationContext／カタログの `$変数`）。
- 診断（波線と gutter）:
  - loader（`te_formula_info` の Rust loader）の失敗を、loader が返す `span`（文書の code point 位置、下記）に出す。
    構文エラー（ブロック間のゴミ行など）、入力末尾の `key:` の空値、未知の `dependsOn`（FI001）、未知の
    executionBackend（FI002）、未知の型、formula なし、bytecode の hex など。位置が 1 点のエラーはその行の残り、
    位置が取れないエラーは該当ブロックの先頭のキー行。
  - 各ブロックの `formula:` を loader と同じ正規化（`#` 行・空行を除いて連結）で `te_check` し、式エディタと同じ
    TE コード・文言・修正のヒント（カタログ `diagnosticRules`）を文書上の位置に戻して出す。同じ式の loader エラーは
    重ねて出さない。
  - 未知のキーは警告、`dependsOn` の未知の名前は（loader が最初の 1 つで止まっても）すべて FI001。
- hover: キー（カタログの説明・型・値の候補）、`---END_OF_PART---`、`formula:` の中の変数・関数・キーワード
  （式エディタと同じ）、`dependsOn:` の名前（どのブロックの `calculatorName` か）。
- 位置: Rust の `span` は code point、JS の文字列位置への変換は `src/trace.js` の `codePointIndexMap`。
- 純粋関数は `src/formula-info-syntax.js`（行・ブロック・キー・値の走査、loader と同じ値の正規化と位置の対応）と
  `src/formula-info-diagnostics.js`、CodeMirror 部分は `src/formula-info-editor.js`。`npm run check` が node で補完・
  診断位置・かっこの深さ・hover を確かめる。
- `---END_OF_PART---` 行（issue #211）: 後ろが空白・タブだけなら終端（起動時に wasm の loader に 1 回問い合わせて
  loader に合わせる）。空白以外の文字が続く行は赤で、loader の構文エラーを行全体に出す。1 ブロックに `calculatorName` が
  2 つ（区切り行の書き忘れ）は 2 つ目の `calculatorName:` に loader のエラー。
- キーの説明・型・列挙値はカタログ `settings`（`scope: formulaInfo`）が正本。Java loader
  （`FormulaInfoParser.extractFormulaInfo`）が読むキーはすべて載せてある（`formula`・`hash`・`hashByByteCode`・`javaCode`・
  `byteCode`・`byteCode_<className>`（接頭辞で判定）・`siteId`・`checkKind` を #212 で追加）。カタログパネルの「設定」の編集は
  FormulaInfo エディタの補完・hover・未知キー判定にすぐ反映され、VSIX の override にも従来どおり書き出される。

## 文法へのリンク

ヘッダと各パネルの見出しの横に UBNF の文法定義へのリンクがある（ブラウザでは新しいタブ、VS Code の webview では
拡張の `openExternal` 経由でシステムのブラウザ）。

- tinyexpression（既定の ubnfc パーサの生成元）:
  [tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf](../tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf)
- FormulaInfo: [grammar/formula-info.ubnf](../grammar/formula-info.ubnf)
- 鉄道図（railroad、tinyexpression の文法）: [docs/railroad/](../docs/railroad/)（GitHub Pages には載せていないので GitHub のページへリンク）

## 評価トレース（段階 3）

- 「トレースを取る」/「評価ごとに記録」で `te_eval_trace`（`evalContext` と同じリクエスト、応答に `trace`）を呼ぶ。
  記録しないときは従来の `te_eval_context` だけ（trace のコストはかからない）。
- 木: 1 行 = tree walker の 1 ステップ（部分式 → 値 / 型）。同じ範囲・同じ値のラッパー（`BinaryExpr` の入れ子など）は
  まとめて表示（チェックを外すと全ノード）。行をクリックするとエディタの該当範囲を強調する。
- ステップ: ⏮ ◀ ▶ ⏭（←/→ キー）で評価順（子 → 親）に進み、そのときの評価スタック（外側の式と、済んだ子の値）を表示。
- 失敗: 例外を出したいちばん内側のステップを赤枠と波線で示し、例外名とカタログ `runtimeErrors` の説明・修正のヒントを出す。
- 純粋関数は `src/trace.js`（code point → JS 文字列位置、ラッパーの畳み込み、ステップ列、スタック）、UI は `src/trace-panel.js`。

## カタログ編集（段階 4）

- 節（変数・関数・キーワード・値・エラーコード・実行時エラー・設定・external・変数グループ）ごとに一覧と検索。
  説明（ja / en）・メッセージ・修正のヒント・例を編集、「追加」で新しい項目、エラーコードには variant を追加できる。
  そのほかの項目（名前・型・snippet など）は「JSON で編集」。
- 検証はブラウザ内: `catalog/tinyexpression-catalog.schema.json` を build 時に ajv の standalone validator
  （`src/generated/catalog-validator.js`、`new Function` を使わないので webview の CSP でも動く）にし、
  `generate-derived.mjs` と同じ整合性規則（`structuralProblems`）も確認する。
- 書き出し: 全体 JSON（リポジトリのファイルと同じ整形）、override JSON（変更・追加した項目だけ。VSIX の
  `catalog.overridePath` がそのまま読む）、unified diff、JSON Patch（RFC 6902）。マージの規則は LSP の
  `CatalogProvider.withOverride` と同じ（`catalog/scripts/catalog-edit.mjs`）。
- PR: GitHub token（このページのメモリにだけ置く）で、カタログと派生ファイル（`.tecatalog`、`error-catalog.json`）を
  新しいブランチに commit して PR を作る。token 無しなら全体 JSON をコピーして GitHub の Web エディタを開く。
- 編集は補完・hover・診断にすぐ反映し、ブラウザの localStorage に override として保存する（自分のブラウザだけ）。

## VS Code の webview（段階 5）

VSIX は `npm run build` の出力（`dist/`）を `playground-dist/` として同梱し、コマンド「TinyExpression: Open playground」で
webview に開く（UI のフォークは無い）。拡張からは `init`（アクティブな式・FormulaInfo・有効なカタログの override）を送り、
playground からは `saveCatalog`（override / 全体 JSON をワークスペースへ書いて `catalog.overridePath` に設定）などを返す
（`src/host.js`）。
