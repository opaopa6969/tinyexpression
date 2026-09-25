# tinyexpression playground

<https://opaopa6969.github.io/tinyexpression/> — 静的サイト（Vite + CodeMirror 6 + `tinyexpression.wasm`）。issue #201 の段階 2〜5
（段階 3: 評価トレース、段階 4: カタログ編集、段階 5: VSIX の webview に同じ build を同梱）。

```sh
cd playground
npm ci
npm run build:wasm   # rust/ から tinyexpression.wasm（release-small）を作って public/ へコピー
npm run dev          # http://localhost:5173/
npm run parity       # Java 差分 golden を playground の評価経路で照合（node）
npm run check        # カタログのスキーマ検証、サンプル、TE コード付き診断、trace、カタログ編集
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
