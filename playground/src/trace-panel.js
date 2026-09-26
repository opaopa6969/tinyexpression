// Trace panel (issue #201, stage 3): the evaluation trace as a collapsible tree (sub-expression
// → value / type, click → highlight in the editor) and a step mode that walks the steps in
// evaluation order with the partial stack of values.
import { prepareTrace, stepEvents, stackAt, outcomeLabel } from './trace.js';
import { highlight } from './highlight.js';
import { isExternalTraceNode } from './externals.js';

const SNIPPET = 48;

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'class') node.className = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key in node) node[key] = value;
    else node.setAttribute(key, value);
  }
  for (const child of children.flat()) {
    if (child != null) node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

function snippet(node) {
  const text = (node.leaf ?? node.source).replace(/\s+/g, ' ').trim();
  return text.length > SNIPPET ? `${text.slice(0, SNIPPET)}…` : text;
}

function outcome(node) {
  if (node.error) return el('span', { class: 'trace-err' }, node.error.kind);
  if (!node.value) return el('span', { class: 'muted' }, '…');
  return [
    el('span', { class: 'trace-val' }, node.value.kind === 'string' ? JSON.stringify(node.text) : node.text),
    el('span', { class: 'trace-type' }, node.type),
  ];
}

/**
 * @param parts {tree, step, summary, failure} container elements
 * @param view the CodeMirror EditorView
 * @param describe (node) => {code, title, fix} catalog text for a failing step
 */
export function createTracePanel(parts, view, describe) {
  let formula = '';
  let raw = null;
  let prepared = { root: null, nodes: [], failing: null };
  let events = [];
  let index = -1;
  let selected = null;
  let collapse = true;
  const rows = new Map(); // node id -> row element
  const expanded = new Set();

  function marks(current) {
    const ranges = [];
    if (prepared.failing) ranges.push({ from: prepared.failing.from, to: prepared.failing.to, kind: 'error' });
    if (current) ranges.push({ from: current.from, to: current.to, kind: 'current' });
    if (view.state.doc.toString() === formula) highlight(view, ranges, { scroll: current != null });
  }

  function select(node, { fromStep = false } = {}) {
    if (selected) rows.get(selected.id)?.classList.remove('selected');
    selected = node;
    if (!node) return marks(null);
    // Reveal the row: expand its ancestors.
    let topmost = null;
    for (let p = node.parent; p; p = p.parent) {
      if (!expanded.has(p.id)) {
        expanded.add(p.id);
        topmost = p;
      }
    }
    if (topmost) renderChildren(topmost);
    const row = rows.get(node.id);
    row?.classList.add('selected');
    row?.scrollIntoView({ block: 'nearest' });
    if (!fromStep) {
      index = events.findIndex((e) => e.type === 'exit' && e.node === node);
      renderStep();
    }
    marks(node);
  }

  function row(node) {
    const hasChildren = node.children.length > 0;
    const toggle = el('button', {
      class: 'trace-toggle', title: hasChildren ? 'expand / collapse' : '', disabled: !hasChildren,
      'aria-label': hasChildren ? '展開' : '',
      onclick: (e) => {
        e.stopPropagation();
        if (expanded.has(node.id)) expanded.delete(node.id);
        else expanded.add(node.id);
        renderChildren(node);
      },
    }, hasChildren ? (expanded.has(node.id) ? '▾' : '▸') : '·');
    const line = el('div', {
      class: `trace-row${node.error ? ' failed' : ''}${node === prepared.failing ? ' failing' : ''}`,
      title: `${node.kinds.join(' › ')}  [${node.from}, ${node.to})`,
      tabIndex: -1,
      onclick: () => select(node),
    },
    toggle,
    el('span', { class: 'trace-kind' }, node.kind === 'Leaf' ? (node.leaf?.startsWith('$') ? 'var' : 'literal') : node.kind.replace(/Expr$/, '')),
    el('code', { class: 'trace-src' }, snippet(node)),
    el('span', { class: 'trace-arrow' }, '→'),
    outcome(node),
    // #216: an external call is answered by a stub value (code blocks are not run here).
    isExternalTraceNode(node) ? el('span', { class: 'stub-tag', title: 'answered by the external stub value of the CalculationContext (not executed)' }, '仮の値') : null);
    const item = el('li', {}, line, el('ul', { class: 'trace-children' }));
    rows.set(node.id, line);
    item.node = node;
    return item;
  }

  function renderChildren(node) {
    const line = rows.get(node.id);
    if (!line) return;
    const item = line.parentElement;
    const list = item.querySelector(':scope > ul');
    const toggle = line.querySelector('.trace-toggle');
    if (node.children.length) toggle.textContent = expanded.has(node.id) ? '▾' : '▸';
    list.replaceChildren();
    if (!expanded.has(node.id)) return;
    list.append(...node.children.map(row));
    for (const child of node.children) if (expanded.has(child.id)) renderChildren(child);
    if (selected) rows.get(selected.id)?.classList.add('selected');
  }

  function renderTree() {
    rows.clear();
    parts.tree.replaceChildren();
    if (!prepared.root) return;
    const top = el('ul', { class: 'trace-tree', role: 'tree' }, row(prepared.root));
    parts.tree.append(top);
    renderChildren(prepared.root);
  }

  function renderStep() {
    parts.step.replaceChildren();
    if (!events.length) return;
    const event = events[index] ?? null;
    const go = (i) => {
      index = Math.max(-1, Math.min(events.length - 1, i));
      renderStep();
      const current = events[index]?.node ?? null;
      select(current, { fromStep: true });
    };
    const nav = el('div', { class: 'step-nav' },
      el('button', { title: 'first step (Home)', 'aria-label': '最初', onclick: () => go(0) }, '⏮'),
      el('button', { title: 'previous step (←)', 'aria-label': '前へ', onclick: () => go(index - 1) }, '◀'),
      el('button', { title: 'next step (→)', 'aria-label': '次へ', onclick: () => go(index + 1) }, '▶'),
      el('button', { title: 'last step (End)', 'aria-label': '最後', onclick: () => go(events.length - 1) }, '⏭'),
      prepared.failing ? el('button', { class: 'link', title: 'jump to the failing step', onclick: () => go(events.findIndex((e) => e.type === 'exit' && e.node === prepared.failing)) }, '失敗したステップへ') : null,
      el('span', { class: 'muted small' }, index < 0 ? `開始前 / ${events.length} イベント` : `${index + 1} / ${events.length}`));
    nav.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight') { go(index + 1); e.preventDefault(); }
      if (e.key === 'ArrowLeft') { go(index - 1); e.preventDefault(); }
      if (e.key === 'Home') { go(0); e.preventDefault(); }
      if (e.key === 'End') { go(events.length - 1); e.preventDefault(); }
    });
    parts.step.append(nav);
    if (!event) {
      parts.step.append(el('p', { class: 'muted small' }, '▶ で最初のステップへ。評価順（子 → 親）に 1 ステップずつ進みます。'));
      return;
    }
    const node = event.node;
    parts.step.append(el('div', { class: `step-event ${event.type}` },
      el('span', { class: 'badge-soft' }, event.type === 'enter' ? '評価開始' : '評価完了'),
      ' ', el('span', { class: 'trace-kind' }, node.kind), ' ', el('code', {}, snippet(node)),
      event.type === 'exit' ? [' → ', el('strong', { class: node.error ? 'trace-err' : 'trace-val' }, outcomeLabel(node))] : null));
    const frames = stackAt(events, index);
    const stack = el('ol', { class: 'step-stack', reversed: true, title: 'evaluation stack: innermost first' });
    for (let i = frames.length - 1; i >= 0; i--) {
      const frame = frames[i];
      stack.append(el('li', {},
        el('span', { class: 'trace-kind' }, frame.node.kind), ' ', el('code', {}, snippet(frame.node)),
        frame.done.length
          ? el('div', { class: 'step-done' }, frame.done.map((d) => el('span', { class: 'step-value' },
            el('code', {}, snippet(d)), ' = ', el('span', { class: d.error ? 'trace-err' : 'trace-val' }, outcomeLabel(d)))))
          : el('div', { class: 'muted small' }, '（まだ値なし）')));
    }
    parts.step.append(el('div', { class: 'small muted' }, `スタック（${frames.length} 段、内側が上）`), stack);
  }

  function renderSummary(response) {
    parts.summary.replaceChildren();
    parts.failure.replaceChildren();
    const trace = response?.result?.trace;
    if (!trace) {
      if (response && !response.result.ok) parts.summary.append(el('p', { class: 'muted small' }, '式を生成できないため trace はありません（構文エラーは上の結果と診断を参照）。'));
      return;
    }
    parts.summary.append(el('p', { class: 'muted small' },
      `${trace.steps} ステップ${trace.truncated ? `（先頭 ${trace.recorded} ステップだけ記録）` : ''} · 表示 ${prepared.nodes.length} ノード`));
    if (prepared.nodes.some(isExternalTraceNode)) {
      parts.summary.append(el('p', { class: 'stub-note small' }, el('span', { class: 'stub-tag' }, '仮の値'),
        ' external 呼び出し（コードブロックのクラスを含む）は実行されず、CalculationContext の仮の値で代用しています。'));
    }
    if (prepared.failing) {
      const f = describe(prepared.failing);
      parts.failure.append(el('div', { class: 'result-error' },
        el('div', { class: 'error-head' }, el('span', { class: 'badge' }, f.code), ' このステップで失敗'),
        el('p', {}, el('code', {}, snippet(prepared.failing)), ` — ${prepared.failing.error.message}`),
        el('p', {}, f.title),
        f.fix ? el('p', { class: 'fix' }, `修正のヒント: ${f.fix}`) : null,
        el('button', { class: 'link', onclick: () => select(prepared.failing) }, 'エディタで表示')));
    }
  }

  let lastResponse = null;
  function build() {
    const trace = lastResponse?.result?.trace ?? null;
    raw = trace?.root ?? null;
    prepared = prepareTrace(raw, formula, { collapse });
    events = stepEvents(prepared.root);
    index = -1;
    selected = null;
    expanded.clear();
    for (const node of prepared.nodes) if (node.depth < 2) expanded.add(node.id);
    for (let p = prepared.failing; p; p = p.parent) expanded.add(p.id);
    renderTree();
    renderSummary(lastResponse);
    renderStep();
    marks(null);
  }

  return {
    /** Shows the trace of a te_eval_trace response for `source`. */
    show(response, source) {
      formula = source;
      lastResponse = response;
      build();
    },
    clear() {
      lastResponse = null;
      formula = '';
      prepared = { root: null, nodes: [], failing: null };
      events = [];
      rows.clear();
      parts.tree.replaceChildren();
      parts.step.replaceChildren();
      parts.summary.replaceChildren();
      parts.failure.replaceChildren();
    },
    setCollapse(value) {
      collapse = value;
      if (lastResponse) build();
    },
    /** For tests and the host: the prepared trace and the step events. */
    state: () => ({ prepared, events, index }),
  };
}
