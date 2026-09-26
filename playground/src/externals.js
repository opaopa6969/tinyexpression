// "external（仮の値）" of the CalculationContext panel (issue #216). Pure (no DOM): scripts/
// check.mjs tests it under node.
//
// The playground's evaluator (Rust, wasm) runs no Java: a ```java:ClassName code block only
// declares its class, and every `external` call is answered by a constant stub of the request's
// `externals[]` (rust/README.md "CalculationContext 付き評価"). This module finds the stubs a
// formula needs ("式から external を追加": `import X#m as a;` + `external returning as T a(...)`,
// `external returning as T X#m(...)`, the side-effect form `external returning as T : X.m(...)`,
// and the code-block classes), and
// which stubs an evaluation used (the "仮の値" marks on the result, the FormulaInfo results and
// the trace). The scan runs on tinyexpression tokens (te-lexer.js, code points, no regular
// expressions) of the text with the code blocks blanked, so nothing inside the Java counts.
import { tokenize } from './te-lexer.js';
import { codeBlocksOf, blankCodeBlocks, MISSING_STUB_HINT } from './code-block.js';

/** `external returning as <T>` → the stub's return type (context.js RETURN_TYPES). */
const RETURN_TYPE_OF = { boolean: 'boolean', number: 'float', string: 'string', object: 'string' };
const DEFAULT_VALUE = { boolean: 'true', float: '0', double: '0', int: '0', long: '0', string: '', null: '' };

/** Significant tokens (no comments) with their text. */
function words(text) {
  const blanked = blankCodeBlocks(text);
  return tokenize(blanked).filter((t) => t.type !== 'comment').map((t) => ({ ...t, text: blanked.slice(t.from, t.to) }));
}

/** `a.b.C` from `k`: {name, next} or null. */
function className(tokens, k) {
  const isName = (t) => t && /* identifiers, keywords-as-names, `.x` */ t.type !== 'operator' && t.type !== 'bracket' && t.type !== 'string' && t.type !== 'number' && t.type !== 'variable';
  if (!isName(tokens[k])) return null;
  let name = tokens[k].text;
  let next = k + 1;
  while (tokens[next]?.text === '.' && isName(tokens[next + 1])) {
    name += `.${tokens[next + 1].text}`;
    next += 2;
  }
  return { name, next };
}

/** Argument count of the call whose '(' is `tokens[open]`, and the index after its ')'. */
function argumentsOf(tokens, open) {
  let depth = 0;
  let commas = 0;
  let any = false;
  for (let k = open; k < tokens.length; k++) {
    const t = tokens[k];
    if (t.type === 'bracket' && (t.text === '(' || t.text === '[' || t.text === '{')) {
      depth++;
      if (k > open) any = true;
      continue;
    }
    if (t.type === 'bracket') {
      depth--;
      if (depth === 0) return { arity: any ? commas + 1 : 0, next: k + 1 };
      continue;
    }
    if (depth === 1 && t.text === ',') commas++;
    else any = true;
  }
  return { arity: any ? commas + 1 : 0, next: tokens.length };
}

/**
 * What one formula text needs: {imports: Map(alias → {class, method}), calls: [{alias} |
 * {class, method}, arity, returnType], codeBlockClasses: [name]}.
 */
export function externalUsesOf(text) {
  const tokens = words(text);
  const imports = new Map();
  const calls = [];
  for (let k = 0; k < tokens.length; k++) {
    const t = tokens[k];
    if (t.text === 'import') {
      const cls = className(tokens, k + 1);
      if (!cls) continue;
      let next = cls.next;
      let method = null;
      if (tokens[next]?.text === '#' && tokens[next + 1]) {
        method = tokens[next + 1].text;
        next += 2;
      }
      if (tokens[next]?.text === 'as' && tokens[next + 1]) imports.set(tokens[next + 1].text, { class: cls.name, method });
      continue;
    }
    if (t.text !== 'external') continue;
    let next = k + 1;
    let returnType = null;
    if (tokens[next]?.text === 'returning' && tokens[next + 1]?.text === 'as' && tokens[next + 2]) {
      returnType = RETURN_TYPE_OF[tokens[next + 2].text] ?? null;
      next += 3;
    }
    if (tokens[next]?.text === ':') {
      // [call] external [returning as T] : a.b.C.method(args)
      const qualified = className(tokens, next + 1);
      if (!qualified || tokens[qualified.next]?.text !== '(') continue;
      const dot = qualified.name.lastIndexOf('.');
      if (dot <= 0) continue;
      const { arity } = argumentsOf(tokens, qualified.next);
      calls.push({ class: qualified.name.slice(0, dot), method: qualified.name.slice(dot + 1), arity, returnType: returnType ?? 'float' });
      continue;
    }
    const qualified = className(tokens, next);
    if (qualified && tokens[qualified.next]?.text === '#' && tokens[qualified.next + 2]?.text === '(') {
      // external returning as T a.b.C#method(args)
      const { arity } = argumentsOf(tokens, qualified.next + 2);
      calls.push({ class: qualified.name, method: tokens[qualified.next + 1].text, arity, returnType: returnType ?? 'float' });
      continue;
    }
    const name = tokens[next];
    if (!name || tokens[next + 1]?.text !== '(') continue;
    const { arity } = argumentsOf(tokens, next + 1);
    calls.push({ alias: name.text, arity, returnType: returnType ?? 'float' });
  }
  return { imports, calls, codeBlockClasses: codeBlocksOf(text).map((b) => b.className) };
}

/**
 * Stub candidates of formula texts: [{class, method, arity (null = any), returnType, codeBlock}]
 * — every external call resolved through its imports, every `import X#m` without a call (arity
 * any), and every code-block class no call or import names (method to fill in).
 */
export function externalCandidates(texts) {
  const out = [];
  const add = (candidate) => {
    if (out.some((c) => c.class === candidate.class && c.method === candidate.method && c.arity === candidate.arity)) return;
    out.push(candidate);
  };
  for (const text of texts) {
    if (!text) continue;
    const uses = externalUsesOf(text);
    const blocks = new Set(uses.codeBlockClasses);
    const resolved = new Set();
    for (const call of uses.calls) {
      let cls = call.class;
      let method = call.method;
      if (call.alias != null) {
        const target = uses.imports.get(call.alias);
        if (!target) continue;
        cls = target.class;
        method = target.method ?? call.alias;
        resolved.add(call.alias);
      }
      add({ class: cls, method, arity: call.arity, returnType: call.returnType, codeBlock: blocks.has(cls) });
    }
    for (const [alias, target] of uses.imports) {
      if (resolved.has(alias) || target.method == null) continue;
      add({ class: target.class, method: target.method, arity: null, returnType: 'float', codeBlock: blocks.has(target.class) });
    }
    for (const cls of blocks) {
      if (!out.some((c) => c.class === cls)) add({ class: cls, method: '', arity: null, returnType: 'float', codeBlock: true });
    }
  }
  return out;
}

const sameMethod = (row, candidate) => String(row.class ?? '').trim() === candidate.class
  && String(row.method ?? '').trim() === candidate.method;

/**
 * Appends a stub row (context.js externals shape) for every candidate no row answers yet
 * (same class and method); existing rows are kept as they are. Returns the added rows.
 */
export function addExternalCandidates(externals, candidates) {
  const added = [];
  for (const c of candidates) {
    if (externals.some((row) => sameMethod(row, c) || (c.method === '' && String(row.class ?? '').trim() === c.class))) continue;
    const row = {
      class: c.class, method: c.method, arity: c.arity == null ? '' : String(c.arity), registered: true,
      returnType: c.returnType, value: DEFAULT_VALUE[c.returnType] ?? '0',
    };
    externals.push(row);
    added.push(row);
  }
  return added;
}

/** The stub rows the external calls of `texts` would be answered by (for the "仮の値" marks). */
export function stubsUsedBy(texts, externals) {
  const used = [];
  for (const c of externalCandidates(texts)) {
    if (!c.method) continue;
    for (const row of externals) {
      const arity = String(row.arity ?? '').trim();
      if (sameMethod(row, c) && (arity === '' || c.arity == null || Number(arity) === c.arity) && !used.includes(row)) used.push(row);
    }
  }
  return used;
}

/** `CheckDigits#check → boolean true` */
export function stubLabel(row) {
  const arity = String(row.arity ?? '').trim();
  const value = row.returnType === 'null' ? 'null' : `${row.returnType ?? 'float'} ${row.value ?? ''}`.trim();
  return `${row.class}#${row.method || '?'}${arity ? `/${arity}` : ''} → ${value}${row.registered === false ? '（未登録）' : ''}`;
}

/** Whether an evaluation failed because a code-block class has no stub (the Rust hint). */
export function isMissingStubFailure(error) {
  return typeof error?.message === 'string' && error.message.includes(MISSING_STUB_HINT);
}

/** Whether a trace node is an external call (answered by a stub in the playground). */
export function isExternalTraceNode(node) {
  const kinds = node?.kinds ?? (node?.kind ? [node.kind] : []);
  return kinds.some((k) => k.startsWith('External') || k.startsWith('SideEffect'));
}
