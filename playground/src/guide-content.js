// The single content source for the guided tour (tour.js) and the help panel (help.js),
// issue #214. Every entry below can drive:
//   - a tour step (`tour: true`): highlights `selector` in the playground and shows a bubble
//     with `title` / `body` (Japanese first, English underneath — the same bilingual pattern
//     `.cm-doc-en` already uses for hover docs).
//   - a help section (always, regardless of `tour`): rendered in document order with an
//     `id="help-<id>"` heading, so both surfaces read from exactly one list and never drift.
//
// `selector` is only meaningful for tour steps. `optional: true` means the target may not
// exist yet (e.g. issue #216's Java code-block UI, still in progress at the time of writing) —
// the tour skips a step whose target is missing instead of breaking, and the build-time check
// (scripts/check.mjs) only requires non-optional selectors to resolve against the shipped
// index.html.
//
// `autoAction` names an entry of the `actions` map passed to `createTour` (tour.js): a small
// side effect run right before the step is shown, e.g. taking a trace so the trace panel has
// something to point at instead of being empty (the "auto-fill sample input for interactive
// steps" acceptance criterion).

export const GUIDE = [
  {
    id: 'sample',
    tour: true,
    selector: '#examples',
    title: { ja: '① サンプルを選ぶ', en: '① Pick a sample' },
    body: {
      ja: [
        'ここから題材となる式と、それが読む CalculationContext（変数・実行時刻など）をまとめて読み込めます。',
        'このツアーはいま開いている式をいったん脇に置き、"fraud-alert 級の判定式" を題材に進みます。ツアーが終わると元の内容に戻ります。',
      ],
      en: [
        'Loads a sample formula together with the CalculationContext it reads (variables, the current time, …).',
        'This tour sets the editors to the "fraud-alert" sample so every later step has something concrete to show, then restores whatever you had when the tour ends.',
      ],
    },
  },
  {
    id: 'formula-editor',
    tour: true,
    selector: '#editor',
    title: { ja: '② 式エディタ', en: '② The formula editor' },
    body: {
      ja: [
        'Ctrl+Space で補完、変数・関数・キーワードにカーソルを合わせるとカタログの説明が出ます（hover）。',
        '構文エラーは波線で、対応するかっこは深さごとに色分けされます（rainbow brackets）。どちらもタイプした瞬間に更新されます。',
      ],
      en: [
        'Ctrl+Space completes; hovering a variable, function or keyword shows its catalog description.',
        'Syntax errors get a wavy underline and matching brackets are coloured by nesting depth (rainbow brackets) — both update as you type.',
      ],
    },
  },
  {
    id: 'context',
    tour: true,
    selector: '.context-panel',
    title: { ja: '③ CalculationContext', en: '③ CalculationContext' },
    body: {
      ja: [
        '式が読む変数・external（外部呼び出しのスタブ）・resultType/numberType などの設定をここで編集します。',
        '「式から変数を追加」は式が参照する $variable のうち CalculationContext に無いものを自動で追加します（このサンプルは最初から揃っています）。',
      ],
      en: [
        'Edit the variables and external stubs the formula reads, plus settings like resultType / numberType.',
        '"Add variables from the formula" adds every $variable the formula reads that is not in the context yet (this sample already has them all).',
      ],
    },
  },
  {
    id: 'result',
    tour: true,
    selector: '#result',
    title: { ja: '④ 評価と結果', en: '④ Evaluation and the result' },
    body: {
      ja: [
        '式・CalculationContext を変えるたびに自動で評価され、値・型・IEEE 754 のビット表現（該当する型のみ）・評価時間が出ます。',
        '失敗した場合は TE コードとカタログの説明・修正のヒント、可能ならエラー位置へのジャンプが出ます。',
      ],
      en: [
        'Every edit to the formula or the CalculationContext re-evaluates automatically: value, type, IEEE 754 bits (where applicable) and timing.',
        'On failure you get the TE code, its catalog description and fix hint, and — when known — a jump to the error position.',
      ],
    },
  },
  {
    id: 'trace',
    tour: true,
    selector: '.trace-panel',
    autoAction: 'trace-once',
    title: { ja: '⑤ 評価トレース', en: '⑤ The evaluation trace' },
    body: {
      ja: [
        '部分式ごとの値・型を木で表示し、評価順に 1 ステップずつ進められます。結果は通常の評価と同じです（tree walker のトレース）。',
        'いまこの式でトレースを取りました。木の各行をクリックすると、式エディタ側の該当範囲がハイライトされます。',
      ],
      en: [
        'Shows every subexpression\'s value and type as a tree, and steps through evaluation order one node at a time — same result as a normal evaluation.',
        'A trace was just taken for this formula: click a row in the tree to highlight the matching span in the formula editor.',
      ],
    },
  },
  {
    id: 'formula-info',
    tour: true,
    selector: '.info-panel',
    title: { ja: '⑥ FormulaInfo エディタ', en: '⑥ The FormulaInfo editor' },
    body: {
      ja: [
        '複数の式を calculatorName・resultType・formula 等のキーでまとめた FormulaInfo ブロックを編集します。行頭でキーと ---END_OF_PART--- を補完し（Ctrl+Space）、書きながら loader のエラー・各 formula の構文診断・未知のキーを波線で示します。',
        '「読み込み」は構文だけを見て一覧にし（te_formula_info）、「実行」は上の CalculationContext で全式を評価します（結果列つき）。',
      ],
      en: [
        'Edits a FormulaInfo block: several formulas keyed by calculatorName / resultType / formula / … . Completes keys and ---END_OF_PART--- at the start of a line (Ctrl+Space), and underlines loader errors, per-formula syntax diagnostics and unknown keys as you type.',
        '"Load" only parses and lists the formulas (te_formula_info); "Run" evaluates every one of them against the CalculationContext above (with a result column).',
      ],
    },
  },
  {
    id: 'catalog',
    tour: true,
    selector: '.catalog-panel',
    title: { ja: '⑦ カタログ編集', en: '⑦ Editing the language catalog' },
    body: {
      ja: [
        '変数・関数・キーワード・エラーコード・設定の説明・例・修正のヒントをここで編集できます。編集は保存すると同じブラウザの補完・hover・診断にすぐ反映されます。',
        '書き出した override JSON は VSIX の設定 tinyExpressionP4Lsp.catalog.overridePath（コマンド「TinyExpression: Import catalog from playground export」）でそのまま読み込めます。',
      ],
      en: [
        'Edit the description, examples and fix hints of variables, functions, keywords, error codes and settings. Edits apply immediately to this browser\'s completion, hover and diagnostics once saved.',
        'The exported override JSON loads as-is via the VSIX setting tinyExpressionP4Lsp.catalog.overridePath (command "TinyExpression: Import catalog from playground export").',
      ],
    },
  },
  {
    id: 'java-code-block',
    tour: true,
    optional: true,
    selector: '[data-tour="java-code-block"]',
    title: { ja: 'Java コードブロック', en: 'Java code blocks' },
    body: {
      ja: [
        'FormulaInfo の formula: に ```java:ClassName でクラスを書き、式から import ClassName#method as alias; と external returning as T alias(...) で呼び出せます（例: src/test/resources/formulaInfo-test/69/formulaInfo.txt）。',
        'この playground（ブラウザ・wasm）には JVM が無いため実際には実行されません。色分けと、external を「仮の値」で代用する表示までです（issue #216）。',
        '本物の実行が必要なら VSIX（VS Code 拡張）を使います。実行は既定 off で、launch.json / DAP に allowJavaCodeBlocks: true を明示したときだけ動きます（ADR-003 のセキュリティモデル）。VSIX 内の playground から本物実行する経路は issue #217 で計画中です。',
        'Rust のコードブロックはありません。実行するには rustc によるその場コンパイルが要り、ADR-003 が Java コードブロックに課しているのと同じリスク（任意コード実行）を増やすだけだからです。',
      ],
      en: [
        'Write a class in FormulaInfo\'s formula: with ```java:ClassName, then call it from the expression with import ClassName#method as alias; and external returning as T alias(...) (example: src/test/resources/formulaInfo-test/69/formulaInfo.txt).',
        'This playground (browser, wasm) has no JVM, so it is never executed here — only highlighted, with externals answered by stub values (issue #216).',
        'Real execution needs the VSIX (VS Code extension). It is off by default and only runs when launch.json / the DAP sets allowJavaCodeBlocks: true (ADR-003\'s safety model). Real execution from the playground inside the VSIX is planned in issue #217.',
        'There is no Rust code block: running one would need compiling with rustc on the spot, adding the same arbitrary-code-execution risk ADR-003 already restricts for Java.',
      ],
    },
    links: [
      { href: 'https://github.com/opaopa6969/tinyexpression/blob/master/docs/decisions/ADR-003-java-codeblock-safety.md', label: 'ADR-003: Java code-block safety' },
      { href: 'https://github.com/opaopa6969/tinyexpression/issues/216', label: 'issue #216' },
      { href: 'https://github.com/opaopa6969/tinyexpression/issues/217', label: 'issue #217' },
    ],
  },
  {
    id: 'grammar',
    tour: true,
    selector: '.grammar-links',
    title: { ja: '⑧ 文法（UBNF）と鉄道図', en: '⑧ The grammar (UBNF) and railroad diagrams' },
    body: {
      ja: [
        'tinyexpression 本体と FormulaInfo ブロックの文法は UBNF で定義されています。鉄道図（railroad diagram）はその文法を図にしたものです。',
        'ヘッダーのこのリンクから両方の UBNF ファイルと鉄道図の一覧を開けます。',
      ],
      en: [
        'Both tinyexpression itself and the FormulaInfo block format are defined in UBNF grammars. The railroad diagrams are a picture of the same grammar.',
        'These header links open both UBNF files and the list of railroad diagrams.',
      ],
    },
  },
  {
    id: 'code-server',
    tour: true,
    selector: '#code-server-link',
    title: { ja: 'ブラウザで VS Code（code-server）', en: 'VS Code in the browser (code-server)' },
    body: {
      ja: [
        'VSIX を入れた VS Code をインストールなしでブラウザから使う入口です。ログインが必要で、常時起動しているわけではありません（開いていないときは少し待つか、後で開き直してください）。',
        'これでツアーはおしまいです。ヘッダーの「ツアー」からいつでも再生でき、「ヘルプ」から同じ内容を読み返せます。',
      ],
      en: [
        'An entry point to VS Code with the VSIX installed, no local install needed — from the browser. It requires logging in and is not always running (if it does not come up, wait a moment or come back later).',
        'That is the end of the tour. Replay it any time from "Tour" in the header, or re-read the same content from "Help".',
      ],
    },
    links: [
      { href: 'https://code.unlaxer.org/', label: 'code.unlaxer.org' },
    ],
  },
];

export function tourSteps() {
  return GUIDE.filter((entry) => entry.tour);
}

export function helpSections() {
  return GUIDE;
}

/**
 * Whether `selector` (as used by a GUIDE entry) matches something in a static HTML document —
 * used by scripts/check.mjs against the shipped index.html, and good enough for the three
 * selector shapes tour steps use: `#id`, `.class`, `[attr="value"]`. Not a general CSS engine.
 */
export function selectorPresentInHtml(html, selector) {
  const idMatch = selector.match(/^#([\w-]+)$/);
  if (idMatch) return new RegExp(`\\bid=["']${idMatch[1]}["']`).test(html);
  const classMatch = selector.match(/^\.([\w-]+)$/);
  if (classMatch) return new RegExp(`\\bclass=["'][^"']*\\b${classMatch[1]}\\b[^"']*["']`).test(html);
  const attrMatch = selector.match(/^\[([\w-]+)="([^"]*)"\]$/);
  if (attrMatch) return new RegExp(`\\b${attrMatch[1]}=["']${attrMatch[2]}["']`).test(html);
  throw new Error(`selectorPresentInHtml: unsupported selector shape: ${selector}`);
}
