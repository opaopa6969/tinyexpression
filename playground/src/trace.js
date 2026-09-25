// Evaluation trace (issue #201, stage 3): the tree `te_eval_trace` returns, prepared for the
// Trace panel and the step mode. Pure (no DOM): scripts/check.mjs tests it under node.
//
// A raw node is {kind, span:[start,end] (code points), leaf?, value?|error?, text?, children}.
// `prepareTrace` turns it into {id, kind, from, to, label, text, type, error, children, parent,
// depth} with JS string indices, and collapses chains of wrapper nodes that cover the same
// source with the same outcome (BinaryExpr{left} → BinaryExpr → Leaf "1" shows as one row).

/** Code point offset → JS string index, for every offset of `text` (length + 1 entries). */
export function codePointIndexMap(text) {
  const map = [0];
  let index = 0;
  while (index < text.length) {
    index += text.codePointAt(index) > 0xffff ? 2 : 1;
    map.push(index);
  }
  return map;
}

export function typeOf(value) {
  if (!value) return '';
  return ({ number: 'float', double: 'double' })[value.kind] ?? value.kind;
}

function sameOutcome(a, b) {
  return JSON.stringify(a.value ?? null) === JSON.stringify(b.value ?? null)
    && JSON.stringify(a.error ?? null) === JSON.stringify(b.error ?? null);
}

/**
 * @param raw the `trace.root` of a te_eval_trace response
 * @param formula the evaluated source (spans are code points into it)
 * @param options {collapse: true} merges same-span single-child chains
 * @returns {root, nodes (pre-order), failing (node | null)}
 */
export function prepareTrace(raw, formula, { collapse = true } = {}) {
  if (!raw) return { root: null, nodes: [], failing: null };
  const map = codePointIndexMap(formula);
  const at = (cp) => map[Math.min(Math.max(cp, 0), map.length - 1)];
  const nodes = [];
  const build = (source, parent, depth) => {
    let current = source;
    const merged = [current.kind];
    // A wrapper: exactly one child, same span, same outcome.
    while (collapse && current.children?.length === 1) {
      const child = current.children[0];
      if (child.span[0] !== current.span[0] || child.span[1] !== current.span[1] || !sameOutcome(child, current)) break;
      current = child;
      merged.push(current.kind);
    }
    let from = at(current.span[0]);
    let to = at(current.span[1]);
    // Text operands of a string concatenation carry the whole concatenation's span (the AST
    // keeps no span of their own): find the operand in the parent's source, after the previous
    // sibling.
    if (current.leaf != null && parent && from === parent.from && to === parent.to && parent.children.length + 1 < 64) {
      const cursor = parent.children.length ? parent.children[parent.children.length - 1].to : parent.from;
      const hay = formula.slice(cursor, parent.to);
      const candidates = [`'${current.leaf}'`, `"${current.leaf}"`, current.leaf];
      for (const c of candidates) {
        const i = c === '' ? -1 : hay.indexOf(c);
        if (i >= 0) {
          from = cursor + i;
          to = from + c.length;
          break;
        }
      }
    }
    const node = {
      id: nodes.length,
      kind: current.kind,
      kinds: merged,
      from,
      to,
      source: formula.slice(from, to),
      leaf: current.leaf ?? null,
      value: current.value ?? null,
      text: current.text ?? null,
      type: typeOf(current.value),
      error: current.error ?? null,
      parent,
      depth,
      children: [],
    };
    nodes.push(node);
    for (const child of current.children ?? []) node.children.push(build(child, node, depth + 1));
    return node;
  };
  // Iterative depth would be safer for giant traces, but the Rust side already bounds the
  // recorded steps (20,000) and the nesting follows the formula's own nesting.
  const root = build(raw, null, 0);
  return { root, nodes, failing: failingNode(root) };
}

/** The deepest failing node: failed itself, and none of its children failed. */
export function failingNode(root) {
  if (!root?.error) return null;
  let node = root;
  for (;;) {
    const failedChild = node.children.find((c) => c.error);
    if (!failedChild) return node;
    node = failedChild;
  }
}

/**
 * Step events in evaluation order: `enter` before a node's children, `exit` after them (with its
 * value). The walker evaluates children in order, so this is exactly the order of the steps.
 */
export function stepEvents(root) {
  const events = [];
  if (!root) return events;
  const visit = [[root, false]];
  while (visit.length) {
    const [node, exiting] = visit.pop();
    if (exiting) {
      events.push({ type: 'exit', node });
      continue;
    }
    events.push({ type: 'enter', node });
    visit.push([node, true]);
    for (let i = node.children.length - 1; i >= 0; i--) visit.push([node.children[i], false]);
  }
  return events;
}

/**
 * The evaluation stack after `events[index]`: one frame per node entered and not yet exited,
 * each with the values its finished children produced so far (the "partial stack of values").
 */
export function stackAt(events, index) {
  const frames = [];
  for (let i = 0; i <= index && i < events.length; i++) {
    const event = events[i];
    if (event.type === 'enter') {
      frames.push({ node: event.node, done: [] });
    } else {
      frames.pop();
      if (frames.length) frames[frames.length - 1].done.push(event.node);
    }
  }
  return frames;
}

/** `3.0 (float)`, `"abc" (string)`, `ArithmeticException` — a node's outcome in one line. */
export function outcomeLabel(node) {
  if (node.error) return `${node.error.kind}: ${node.error.message}`;
  if (!node.value) return '…';
  if (node.value.kind === 'string') return `"${node.text}" (string)`;
  return `${node.text} (${node.type})`;
}
