# tinyexpression playground

<https://opaopa6969.github.io/tinyexpression/> — 静的サイト（Vite + CodeMirror 6 + `tinyexpression.wasm`）。issue #201 の段階 2。

```sh
cd playground
npm ci
npm run build:wasm   # rust/ から tinyexpression.wasm（release-small）を作って public/ へコピー
npm run dev          # http://localhost:5173/
npm run parity       # Java 差分 golden を playground の評価経路で照合（node）
npm run check        # カタログのスキーマ検証、サンプル、TE コード付き診断
npm run build        # dist/（GitHub Pages に置くもの）
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
