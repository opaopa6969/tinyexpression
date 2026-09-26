// tinyexpression playground (issue #201): CodeMirror 6 editor, tinyexpression.wasm
// (parse / check / eval with a CalculationContext, FormulaInfo), the language catalog
// (catalog/tinyexpression-catalog.json) for completion, hover and diagnostic texts (stage 2),
// the evaluation trace and step mode (stage 3), catalog editing / export / PR (stage 4), and
// the VS Code webview host (stage 5, src/host.js).
import { EditorView, basicSetup } from 'codemirror';
import { autocompletion } from '@codemirror/autocomplete';
import { linter, lintGutter, forceLinting } from '@codemirror/lint';
import catalogJson from '../../catalog/tinyexpression-catalog.json';
import { ja } from '../../catalog/scripts/catalog-lib.mjs';
import { createRuntime } from './te-runtime.js';
import {
  emptyState, toRequest, referencedVariables, declaredVariables,
  VARIABLE_TYPES, RESULT_TYPES, NUMBER_TYPES, DAYS, RETURN_TYPES,
} from './context.js';
import { diagnose, describeFailure } from './diagnostics.js';
import { completionSource, hoverExtension } from './editor-support.js';
import { EXAMPLES, FORMULA_INFO_SAMPLE } from './examples.js';
import { highlightExtension } from './highlight.js';
import { analyzeFormula, lexiconOf } from './te-lexer.js';
import { syntaxView, refreshSyntax } from './syntax-view.js';
import { analyzeFormulaInfo, setEndMarkTrailingSpace } from './formula-info-syntax.js';
import { diagnoseFormulaInfo, formulaInfoKeys, isKnownKey } from './formula-info-diagnostics.js';
import { formulaInfoCompletionSource, formulaInfoHover } from './formula-info-editor.js';
import { createTracePanel } from './trace-panel.js';
import { createCatalogPanel } from './catalog-panel.js';
import { mergeOverride, overrideOf, formatOverride } from '../../catalog/scripts/catalog-edit.mjs';
import { inVsCode, connectHost } from './host.js';
import './style.css';

const STORAGE_KEY = 'tinyexpression-playground-v1';
const CATALOG_KEY = 'tinyexpression-playground-catalog-override-v1';
/** The repository catalog: the base of every diff, export and PR. */
const bundledCatalog = catalogJson;
/** The catalog the editor uses: the repository catalog plus the edits (Catalog panel). */
let catalog = mergeOverride(bundledCatalog, loadCatalogOverride());
let te = null;
let state = loadState();

// ── persistence (per-viewer convenience only) ──

function loadState() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    if (saved?.context && typeof saved.formula === 'string') {
      return { ...saved, context: { ...emptyState(), ...saved.context } };
    }
  } catch { /* storage unavailable */ }
  const example = EXAMPLES.find((e) => e.id === 'fraud-alert');
  return { formula: example.formula, formulaInfo: FORMULA_INFO_SAMPLE, context: contextOf(example) };
}

function loadCatalogOverride() {
  if (inVsCode) return {}; // the extension sends the active override
  try {
    return JSON.parse(localStorage.getItem(CATALOG_KEY) ?? '{}') ?? {};
  } catch {
    return {};
  }
}

function saveCatalogOverride() {
  if (inVsCode) return;
  try {
    const override = overrideOf(bundledCatalog, catalog);
    if (Object.keys(override).length) localStorage.setItem(CATALOG_KEY, formatOverride(override));
    else localStorage.removeItem(CATALOG_KEY);
  } catch { /* storage unavailable */ }
}

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...state, formula: view.state.doc.toString() }));
  } catch { /* storage unavailable */ }
}

function contextOf(example) {
  return { ...emptyState(), ...example.context, variables: example.context.variables.map((v) => ({ ...v })), externals: (example.context.externals ?? []).map((e) => ({ ...e })) };
}

// ── small DOM helpers ──

const $ = (id) => document.getElementById(id);

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'class') node.className = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key in node && key !== 'list') node[key] = value;
    else node.setAttribute(key, value);
  }
  for (const child of children.flat()) {
    if (child != null) node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

function select(options, value, onChange, title) {
  return el('select', { title, onchange: (e) => onChange(e.target.value) },
    options.map((o) => {
      const [val, label] = Array.isArray(o) ? o : [o, o];
      return el('option', { value: val, selected: val === value }, label);
    }));
}

// ── editor ──

const contextVariables = () => state.context.variables;
const currentCatalog = () => catalog;
const toLint = (d) => ({
  from: d.from,
  to: d.to,
  severity: d.severity,
  source: d.code,
  message: [d.message, d.fix && !d.message.includes(d.fix) ? `修正のヒント: ${d.fix}` : '', d.detail].filter(Boolean).join('\n'),
});

const view = new EditorView({
  doc: state.formula,
  parent: $('editor'),
  extensions: [
    basicSetup,
    EditorView.lineWrapping,
    autocompletion({ override: [completionSource(() => te, currentCatalog, contextVariables)], activateOnTyping: true }),
    hoverExtension(currentCatalog, contextVariables),
    lintGutter(),
    highlightExtension,
    syntaxView((text) => analyzeFormula(text, lexiconOf(catalog))),
    linter((v) => {
      if (!te) return [];
      return diagnose(te, catalog, v.state.doc.toString(), state.context.variables.map((x) => x.name)).map(toLint);
    }, { delay: 250 }),
    EditorView.updateListener.of((update) => {
      if (update.docChanged) scheduleEvaluate();
    }),
    EditorView.contentAttributes.of({ 'aria-label': 'tinyexpression formula' }),
  ],
});

// ── evaluation ──

let timer = null;
function scheduleEvaluate() {
  clearTimeout(timer);
  timer = setTimeout(evaluate, 300);
}

function contextChanged() {
  renderContext();
  forceLinting(view);
  forceLinting(infoView);
  scheduleEvaluate();
}

function kindLabel(value) {
  return ({ number: 'float', double: 'double', int: 'int', long: 'long', short: 'short', byte: 'byte', boolean: 'boolean', string: 'string', object: 'object', null: 'null' })[value.kind] ?? value.kind;
}

async function evaluate() {
  saveState();
  if (!te) return;
  await te.ready();
  const formula = view.state.doc.toString();
  const out = $('result');
  out.replaceChildren();
  if (formula.trim() === '') {
    out.append(el('p', { class: 'muted' }, '式を入力してください。'));
    tracePanel.clear();
    return;
  }
  const started = performance.now();
  const tracing = state.trace === true;
  const response = tracing
    ? te.evalTrace(toRequest(state.context, { formula }))
    : te.evalContext(toRequest(state.context, { formula }));
  const { result } = response;
  const ms = performance.now() - started;
  if (tracing) tracePanel.show(response, formula);
  else tracePanel.clear();
  if (result.ok) {
    const value = result.value;
    out.append(
      el('div', { class: 'result-ok' },
        el('div', { class: 'result-value', title: 'String.valueOf(result)' }, result.text),
        el('dl', {},
          el('dt', { title: 'result kind' }, '型'), el('dd', {}, kindLabel(value)),
          value.f32Bits ? [el('dt', { title: 'IEEE 754 bits' }, 'ビット'), el('dd', {}, value.f32Bits)] : null,
          value.f64Bits ? [el('dt', { title: 'IEEE 754 bits' }, 'ビット'), el('dd', {}, value.f64Bits)] : null,
          el('dt', { title: 'evaluation time (wasm)' }, '時間'), el('dd', {}, `${ms.toFixed(2)} ms${tracing ? '（trace 込み）' : ''}`))));
    return;
  }
  const failure = describeFailure(catalog, formula, result);
  const stageLabel = { create: '生成（parse）', apply: '評価', request: 'リクエスト', internal: '内部' }[result.stage] ?? result.stage;
  out.append(el('div', { class: 'result-error' },
    el('div', { class: 'error-head' }, el('span', { class: 'badge' }, failure.code), ` ${stageLabel}で失敗`),
    el('p', {}, failure.title),
    failure.fix ? el('p', { class: 'fix' }, `修正のヒント: ${failure.fix}`) : null,
    result.error?.message ? el('p', { class: 'muted small', title: 'Java exception message' }, `${result.error.kind}: ${result.error.message}`) : null,
    failure.from != null ? el('button', { class: 'link', onclick: () => {
      view.dispatch({ selection: { anchor: failure.from, head: Math.max(failure.from, Math.min(failure.to, failure.from + 1)) }, scrollIntoView: true });
      view.focus();
    } }, 'エラー位置へ移動') : null));
}

// ── CalculationContext panel ──

function renderContext() {
  const c = state.context;
  const settings = $('settings');
  settings.replaceChildren(
    el('label', { title: 'resultType: the calculator result type' }, '結果型',
      select(RESULT_TYPES, c.resultType, (v) => { c.resultType = v; contextChanged(); }, 'resultType')),
    el('label', { title: 'numberType: the representation of arithmetic' }, '数値型',
      select(NUMBER_TYPES, c.numberType, (v) => { c.numberType = v; contextChanged(); }, 'numberType')),
    el('label', { title: 'angle unit of sin/cos/tan' }, '角度',
      select([['degree', '度'], ['radian', 'ラジアン']], c.angle, (v) => { c.angle = v; contextChanged(); }, 'angle')),
    el('label', { title: 'nowHour: read by inTimeRange / inDayTimeRange (empty = unset)' }, 'nowHour',
      el('input', { type: 'number', min: 0, max: 23, step: 1, value: c.nowHour, placeholder: '未設定',
        oninput: (e) => { c.nowHour = e.target.value; scheduleEvaluate(); } })),
    el('label', { title: 'nowDayOfWeek: read by inDayTimeRange (1 = MONDAY)' }, 'nowDayOfWeek',
      select([['', '未設定'], ...DAYS.map((d, i) => [d, `${d} (${i + 1})`])], c.nowDayOfWeek, (v) => { c.nowDayOfWeek = v; contextChanged(); }, 'nowDayOfWeek')),
  );

  const rows = $('variables');
  rows.replaceChildren(...c.variables.map((v, index) => {
    const known = catalogVariableHint(v.name);
    return el('tr', {},
      el('td', {}, el('input', { value: v.name, placeholder: 'name', title: 'variable name (without $)', 'aria-label': '変数名',
        onchange: (e) => { v.name = e.target.value.trim().replace(/^\$/, ''); contextChanged(); } }),
        known ? el('div', { class: 'hint', title: known }, known) : null),
      el('td', {}, select(VARIABLE_TYPES, v.type, (t) => { v.type = t; if (t === 'boolean' && !['true', 'false'].includes(v.value)) v.value = 'true'; contextChanged(); }, 'type')),
      el('td', {}, v.type === 'boolean'
        ? select(['true', 'false'], v.value, (x) => { v.value = x; scheduleEvaluate(); }, 'value')
        : el('input', { value: v.value, placeholder: 'value', 'aria-label': '値', oninput: (e) => { v.value = e.target.value; scheduleEvaluate(); } })),
      el('td', {}, el('button', { class: 'icon', title: 'remove', 'aria-label': '削除', onclick: () => { c.variables.splice(index, 1); contextChanged(); } }, '×')));
  }));

  const externals = $('externals');
  externals.replaceChildren(...c.externals.map((x, index) => el('tr', {},
    el('td', {}, el('input', { value: x.class, placeholder: 'sample.Fee', title: 'fully qualified class name (after import)', onchange: (e) => { x.class = e.target.value.trim(); contextChanged(); } })),
    el('td', {}, el('input', { value: x.method, placeholder: 'method', onchange: (e) => { x.method = e.target.value.trim(); contextChanged(); } })),
    el('td', {}, el('input', { class: 'narrow', value: x.arity ?? '', placeholder: '任意', title: 'argument count (empty = any)', onchange: (e) => { x.arity = e.target.value.trim(); contextChanged(); } })),
    el('td', {}, el('input', { type: 'checkbox', checked: x.registered !== false, title: 'registered: an instance is set in the context (unchecked = CalculationException)', onchange: (e) => { x.registered = e.target.checked; contextChanged(); } })),
    el('td', {}, select(RETURN_TYPES, x.returnType ?? 'float', (t) => { x.returnType = t; contextChanged(); }, 'return type')),
    el('td', {}, el('input', { value: x.value ?? '', placeholder: '戻り値', disabled: x.returnType === 'null', oninput: (e) => { x.value = e.target.value; scheduleEvaluate(); } })),
    el('td', {}, el('button', { class: 'icon', title: 'remove', 'aria-label': '削除', onclick: () => { c.externals.splice(index, 1); contextChanged(); } }, '×')))));
}

function catalogVariableHint(name) {
  if (!name) return '';
  const v = catalog.variables.find((x) => x.name === name);
  return v ? ja(v.description) : '';
}

function typeFromCatalog(name) {
  const v = catalog.variables.find((x) => x.name === name);
  const t = v?.type;
  if (t === 'boolean' || t === 'string') return t;
  return 'float';
}

$('add-variable').addEventListener('click', () => {
  state.context.variables.push({ name: '', type: 'float', value: '0' });
  contextChanged();
});
$('add-external').addEventListener('click', () => {
  state.context.externals.push({ class: '', method: '', arity: '', registered: true, returnType: 'float', value: '0' });
  contextChanged();
});
$('pick-variables').addEventListener('click', () => {
  const formula = view.state.doc.toString();
  const declared = declaredVariables(formula);
  const have = new Set(state.context.variables.map((v) => v.name));
  for (const name of referencedVariables(formula)) {
    if (have.has(name) || declared.has(name) || name === 'nowHour' || name === 'nowDayOfWeek') continue;
    const type = typeFromCatalog(name);
    state.context.variables.push({ name, type, value: type === 'boolean' ? 'false' : type === 'string' ? '' : '0' });
  }
  contextChanged();
});

// ── examples ──

const exampleSelect = $('examples');
exampleSelect.append(...EXAMPLES.map((e) => el('option', { value: e.id }, e.title)));
exampleSelect.addEventListener('change', () => {
  const example = EXAMPLES.find((e) => e.id === exampleSelect.value);
  if (!example) return;
  state.context = contextOf(example);
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: example.formula } });
  exampleSelect.value = '';
  contextChanged();
});

// ── FormulaInfo panel ──

const analyzeInfo = (text) => {
  const keys = formulaInfoKeys(catalog);
  return analyzeFormulaInfo(text, { lexicon: lexiconOf(catalog), isKnownKey: (key) => isKnownKey(keys, key) });
};
const infoView = new EditorView({
  doc: state.formulaInfo ?? FORMULA_INFO_SAMPLE,
  parent: $('formula-info'),
  extensions: [
    basicSetup,
    EditorView.lineWrapping,
    autocompletion({ override: [formulaInfoCompletionSource(() => te, currentCatalog, contextVariables)], activateOnTyping: true }),
    formulaInfoHover(currentCatalog, contextVariables),
    lintGutter(),
    syntaxView(analyzeInfo),
    linter((v) => {
      if (!te) return [];
      const text = v.state.doc.toString();
      return diagnoseFormulaInfo(te, catalog, text, state.context.variables.map((x) => x.name), analyzeInfo(text)).map(toLint);
    }, { delay: 300 }),
    EditorView.updateListener.of((update) => {
      if (!update.docChanged) return;
      state.formulaInfo = update.state.doc.toString();
      saveState();
    }),
    EditorView.contentAttributes.of({ 'aria-label': 'FormulaInfo' }),
  ],
});
const infoText = () => infoView.state.doc.toString();
function setInfoText(text) {
  infoView.dispatch({ changes: { from: 0, to: infoView.state.doc.length, insert: text } });
}

function simpleType(javaName) {
  if (!javaName) return null;
  const simple = javaName.split('.').pop().toLowerCase();
  return ({ integer: 'int' })[simple] ?? simple;
}

function renderFormulaInfo(result, evaluated) {
  const out = $('formula-info-result');
  out.replaceChildren();
  if (!result.ok && !result.formulas) {
    const error = result.error ?? {};
    out.append(el('div', { class: 'result-error' },
      el('div', { class: 'error-head' }, el('span', { class: 'badge' }, error.kind ?? result.stage), ' 読み込みに失敗'),
      el('p', {}, error.message ?? result.message ?? JSON.stringify(result))));
    return;
  }
  const table = el('table', { class: 'grid' },
    el('thead', {}, el('tr', {}, el('th', {}, 'calculatorName'), el('th', {}, 'resultType'), el('th', {}, 'backend'),
      el('th', {}, '式'), evaluated ? el('th', {}, '結果') : null, el('th', {}, ''))),
    el('tbody', {}, result.formulas.map((f) => {
      const info = f.info;
      let cell = null;
      if (evaluated) {
        cell = f.value
          ? el('td', { class: 'ok' }, f.value.value === undefined ? kindLabel(f.value) : String(f.value.value))
          : el('td', { class: 'ng', title: f.error?.message ?? '' }, `${f.error?.kind ?? 'error'}`);
      }
      return el('tr', {},
        el('td', {}, info.calculatorName ?? info.name ?? ''),
        el('td', {}, simpleType(info.resultType) ?? ''),
        el('td', {}, info.executionBackend ?? ''),
        el('td', {}, el('code', {}, info.formulaText.length > 80 ? `${info.formulaText.slice(0, 80)}…` : info.formulaText)),
        cell,
        el('td', {}, el('button', { class: 'link', title: 'open this formula in the editor with its result / number type', onclick: () => {
          const rt = simpleType(info.resultType);
          const nt = simpleType(info.numberType);
          if (RESULT_TYPES.includes(rt)) state.context.resultType = rt;
          if (NUMBER_TYPES.includes(nt)) state.context.numberType = nt;
          view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: info.formulaText } });
          contextChanged();
        } }, 'エディタで開く')));
    })));
  out.append(table);
}

$('load-info').addEventListener('click', () => {
  if (!te) return;
  renderFormulaInfo(te.load(infoText()).result, false);
});
$('run-info').addEventListener('click', () => {
  if (!te) return;
  renderFormulaInfo(te.runContext(toRequest(state.context, { document: infoText() })).result, true);
});
$('sample-info').addEventListener('click', () => {
  setInfoText(FORMULA_INFO_SAMPLE);
});

// ── Trace panel (stage 3) ──

function describeStep(node) {
  const runtime = (catalog.runtimeErrors ?? []).find((r) => r.kind === node.error.kind);
  return {
    code: node.error.kind,
    title: runtime ? ja(runtime.description) : node.error.message,
    fix: runtime?.fix ? ja(runtime.fix) : '',
  };
}

const tracePanel = createTracePanel({
  tree: $('trace-tree'), step: $('trace-step'), summary: $('trace-summary'), failure: $('trace-failure'),
}, view, describeStep);

const traceToggle = $('trace-enabled');
traceToggle.checked = state.trace === true;
traceToggle.addEventListener('change', () => {
  state.trace = traceToggle.checked;
  $('trace-hint').hidden = state.trace;
  evaluate();
});
$('trace-hint').hidden = state.trace === true;
$('trace-once').addEventListener('click', () => {
  traceToggle.checked = true;
  state.trace = true;
  $('trace-hint').hidden = true;
  evaluate();
});
$('trace-collapse').addEventListener('change', (e) => tracePanel.setCollapse(e.target.checked));

// ── Catalog panel (stage 4) ──

function renderCatalogCounts() {
  $('catalog-counts').textContent = `カタログ: 変数 ${catalog.variables.length} / 関数 ${catalog.functions.length} / エラーコード ${catalog.errorCodes.length}`
    + `${hostCatalogLabel ? ` · ${hostCatalogLabel}` : ''}`;
}

let hostCatalogLabel = '';
const catalogPanel = createCatalogPanel($('catalog'), {
  bundled: bundledCatalog,
  edited: catalog,
  onChange(edited) {
    catalog = edited;
    saveCatalogOverride();
    renderCatalogCounts();
    renderContext();
    forceLinting(view);
    forceLinting(infoView);
    refreshSyntax(view);
    refreshSyntax(infoView);
    scheduleEvaluate();
  },
});

// ── VS Code webview host (stage 5) ──

connectHost({
  init(message) {
    if (typeof message.formula === 'string' && message.formula.trim() !== '') {
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: message.formula } });
    }
    if (typeof message.formulaInfo === 'string' && message.formulaInfo.trim() !== '') {
      setInfoText(message.formulaInfo);
    }
    $('host-status').textContent = message.documentName ? `VS Code: ${message.documentName} から読み込みました。` : '';
    hostCatalogLabel = message.catalogLabel ?? '';
    catalog = mergeOverride(bundledCatalog, message.catalogOverride ?? {});
    catalogPanel.load(catalog);
    renderCatalogCounts();
    renderContext();
    refreshSyntax(view);
    refreshSyntax(infoView);
    if (te) {
      forceLinting(view);
      forceLinting(infoView);
    }
    scheduleEvaluate();
  },
  saved(message) {
    hostCatalogLabel = message.catalogLabel ?? hostCatalogLabel;
    renderCatalogCounts();
    $('host-status').textContent = message.message ?? '';
  },
});
if (inVsCode) document.documentElement.classList.add('in-vscode');

// ── start ──

renderContext();
renderCatalogCounts();
createRuntime(new URL('tinyexpression.wasm', document.baseURI).href).then((runtime) => {
  te = runtime;
  const version = te.version();
  $('version').textContent = `tinyexpression ${version.version} (wasm ${(te.size / 1024 / 1024).toFixed(1)} MB, ubnfc ${version.ubnfc.slice(0, 7)})`;
  // Issue #211: whether this loader closes a block at `---END_OF_PART---` + trailing spaces.
  const probe = te.load('calculatorName:a\nformula:\n1\n---END_OF_PART--- \n').result;
  setEndMarkTrailingSpace(probe.ok === true && probe.formulas?.[0]?.info?.formulaText === '1');
  refreshSyntax(infoView);
  forceLinting(view);
  forceLinting(infoView);
  evaluate();
}).catch((error) => {
  $('result').replaceChildren(el('div', { class: 'result-error' }, `tinyexpression.wasm を読み込めませんでした: ${error.message}`));
});
