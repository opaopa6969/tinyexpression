# tinyexpression playground

<https://opaopa6969.github.io/tinyexpression/> — 静的サイト（Vite + CodeMirror 6 + `tinyexpression.wasm`）。issue #201 の段階 2〜5
（段階 3: 評価トレース、段階 4: カタログ編集、段階 5: VSIX の webview に同じ build を同梱）。

```sh
cd playground
npm ci
npm run build:wasm   # rust/ から tinyexpression.wasm（release-small）を作って public/ へコピー
npm run dev          # http://localhost:5173/
npm run parity       # Java 差分 golden を playground の評価経路で照合（node）
npm run check        # カタログのスキーマ検証、サンプル、TE コード付き診断、trace、カタログ編集、エディタ（色分け・かっこ・FormulaInfo の補完/診断/hover）、Java コードブロックと external の仮の値（#216）
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
- 状態（式・CalculationContext（external の仮の値を含む）・FormulaInfo）はブラウザの localStorage にだけ保存する
  （VS Code の webview では `vscode.setState` にも保存し、パネルを開き直しても残る。#216）。
- 色分け（issue #212）: キーワード・関数・`$変数`・文字列・数値・コメント・```java ブロック（中身は Java として色分け、#216）。かっこは入れ子の深さごとに
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

<a id="help-java-code-block"></a>

## Java コードブロックと external の仮の値（issue #216）

FormulaInfo の `formula:`（と式エディタ）には ```` ```java:ClassName ```` の Java コードブロックを書け、式からは
`import ClassName#method as alias;` ＋ `external returning as T alias(...)` で呼ぶ（例:
[src/test/resources/formulaInfo-test/69/formulaInfo.txt](../src/test/resources/formulaInfo-test/69/formulaInfo.txt)）。
playground は wasm の Rust 評価器で動き JVM が無いので、**コードブロックはコンパイルも実行もしない**。

- 編集支援（式エディタと FormulaInfo エディタの両方）:
  - ブロックの範囲は Java の `CodeStartParser` / `CodeEndParser` と同じ規則（行全体が ```` ```scheme:a.b.Class ```` で開き、
    行全体が ```` ``` ```` で閉じる。`src/code-block.js`、code point 単位の走査で正規表現なし）。
  - 中身は `@codemirror/lang-java`（Lezer の Java parser）で Java として色分け（キーワード・型・メソッド名・文字列・数値・
    コメント）。色は tinyexpression 側と同じ `te-t-*` クラスで、VS Code の埋め込み Java ハイライトと同じ系統。
    eval / `new Function` を使わないので VSIX の webview の CSP でも動く。ブロックの行には背景と左の線。
  - かっこの深さ色・対応はブロックの中だけで数える（Java の不整合が式側のかっこに波及しない）。
  - ブロックは折りたためる（開き行の左の fold マーカー / Ctrl+Shift+[）。
  - tinyexpression の補完・`$変数` の診断（TE022）・「式から変数を追加」はブロックの中を見ない。
  - hover: 「playground では実行されない、external の仮の値で代用される」説明、そのクラスの仮の値（未設定なら
    「値を入れないと実行は明示エラー」）、本物の実行の案内（VSIX の launch 設定で `allowJavaCodeBlocks: true`、
    [ADR-003](../docs/decisions/ADR-003-java-codeblock-safety.md)、[code-server](https://code.unlaxer.org/)（要ログイン））とヘルプへのリンク。
- 仮実行（external（仮の値））:
  - CalculationContext パネルの「external（仮の値）」の行が、リクエストの `externals[]`（定数スタブ）になる。
    「式から external を追加」は式エディタと FormulaInfo の全 `formula:` から `import X#m as a` ＋
    `external returning as T a(...)`（引数の個数と戻り型も）、`external returning as T X#m(...)`、
    `external ... : X.m(...)`、コードブロックのクラスを拾い、仮の値が無いものだけ行を足す（既存の行は変えない。
    `src/externals.js`）。
  - Rust 評価器はコードブロックを「クラスを宣言するだけ」として扱い、そのクラスへの external 呼び出しを仮の値で解決する。
    仮の値が無ければ `Class.forName` 失敗相当の `UnsupportedOperationException` で、メッセージに
    「コードブロックのクラスは externals で値を指定してください」が入る（[rust/README.md](../rust/README.md#calculationcontext-付き評価issue-201)）。
    結果欄にはそのとき「式から external を追加」ボタンが出る。
  - 仮の値を使った結果・FormulaInfo の各結果・トレースの external ノードには「仮の値」の印が付く。
  - `formulaInfo-test/69` は仮の値を入れると「実行」で全ブロックが評価できる（入れなければ 2 つのコードブロックの式だけが
    上の明示エラー）。`npm run check` がこれを確かめる。サンプル「Java コードブロック（仮の値で代用）」も同じ形。
  - VS Code の webview でも external の行は保たれる（`vscode.setState`）。拡張からの `init` で式・FormulaInfo が
    差し替わっても行は消さず、開いた文書の external で仮の値が無いものだけ行を足す。
- Java との差: Java はコードブロックを実際にコンパイル・実行するので、仮の値（定数）での結果は一致しない。
  `npm run parity` はコードブロックを含む行を「Rust はスタブ必須」として別に扱う（スタブ無しで明示エラーになることを
  全行で確認、登録済み external の行は Java と比べない）。本物の実行（VSIX の中だけ）は別 issue。
- ヘルプへのリンク（#214 との取り決め）: リンク先は id `help-java-code-block` の要素（#214 のヘルプ `<dialog>` の
  「Java コードブロック」節）。リンクは `te:open-help` イベント（`detail.id`）を投げてヘルプを開き、その節へスクロールする。
  その要素が無い build では、この README の同じ anchor（この節）を開く。ツアーの「Java コードブロック」手順は
  「external（仮の値）」欄（`data-tour="java-code-block"`）を指す。

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

## ガイドツアーとヘルプ（issue #214）

ヘッダーの「ツアー」「ヘルプ」は `src/guide-content.js` の 1 つの配列（`GUIDE`）だけを内容源にする。
tour（`src/tour.js`）は要素を 1 つずつハイライト＋吹き出し（日本語＋英語）で進み、help（`src/help.js`）は
同じ配列をすべて `<dialog>` の節として並べる（節の見出しには `id="help-<id>"`。issue #216 の Java
コードブロックの hover と「external（仮の値）」欄のヘルプリンクは `help-java-code-block` を開く）。

- 手順: ①サンプル → ②式エディタ → ③CalculationContext → ④評価と結果 → ⑤評価トレース →
  ⑥FormulaInfo エディタ → ⑦カタログ編集 → Java コードブロック（issue #216: サンプル「Java コードブロック」を
  読み込んで CalculationContext の「external（仮の値）」欄を指す。必須の手順） → ⑧文法（UBNF）と鉄道図 → code-server（ブラウザで VS Code）。
- 対象要素が見つからない手順は（`optional: true` かどうかによらず）自動でスキップする。
  `npm run check` が全手順の対象を `index.html` に対して検証し、無ければ `optional: true` を要求する。
- 操作が要る手順（評価トレース）は自動でサンプル入力を読み込み、「トレースを取る」相当の処理を実行してから
  説明する（`autoAction`、ツアー終了時は開始前の式・CalculationContext・FormulaInfo に復元する）。
- 初回訪問時だけ「ツアーを見ますか」を出す（localStorage）。キーボード（←/→/Enter/Esc）で操作でき、
  外部リンクは `data-external` を付けて VS Code の webview からも開ける。CDN 依存・`eval`/`new Function` は無い。
- code-server（<https://code.unlaxer.org/>）はログインが要り常時起動ではない。VSIX
  （`opaopa6969.tinyexpression-p4-lsp`）はインフラ上の TinyExpression 専用 code-server コンテナには導入済みを
  確認済み。手元で入れる場合は Extensions ビューの「Install from VSIX…」、または
  `code-server --install-extension <file>.vsix`。VSIX 自体は
  `tools/tinyexpression-p4-lsp-vscode/`（`npm run package`）でビルドされ、タグ push (`v*`) で GitHub Release に添付される。

## VS Code の webview（段階 5）

VSIX は `npm run build` の出力（`dist/`）を `playground-dist/` として同梱し、コマンド「TinyExpression: Open playground」で
webview に開く（UI のフォークは無い）。拡張からは `init`（アクティブな式・FormulaInfo・有効なカタログの override。CalculationContext と
external の仮の値は webview 側の状態を保つ、#216）を送り、
playground からは `saveCatalog`（override / 全体 JSON をワークスペースへ書いて `catalog.overridePath` に設定）などを返す
（`src/host.js`）。
