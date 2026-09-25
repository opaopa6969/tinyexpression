// Editing the language catalog (issue #201, stage 4): the playground's Catalog panel, the
// round-trip check (playground/scripts/catalog-roundtrip.mjs) and the golden fixture of the
// VSIX test share these functions. Pure (no DOM, no node APIs).
//
// An *override* is a partial catalog: only the entries that differ from the base, per section.
// `mergeOverride` applies one exactly as the LSP's CatalogProvider.withOverride does (entries
// replaced by key in place, new keys appended, diagnosticRules / defaultErrorCode replaced),
// so what the playground shows is what the VSIX shows with the same override file.
import { formatCatalog } from './catalog-lib.mjs';

/** Keyed sections and the key CatalogProvider.withOverride merges them by. */
export const SECTIONS = {
  variables: (o) => `${o.group}/${o.match}/${o.name}`,
  variableGroups: (o) => o.id,
  functions: (o) => o.name,
  keywords: (o) => o.name,
  values: (o) => o.name,
  externals: (o) => `${o.class}#${o.method}/${o.params ? o.params.length : 0}`,
  errorCodes: (o) => o.code,
  runtimeErrors: (o) => o.kind,
  settings: (o) => `${o.scope}/${o.name}`,
};

/** Sections replaced as a whole by an override. */
export const WHOLE = ['diagnosticRules', 'defaultErrorCode'];

export const clone = (value) => JSON.parse(JSON.stringify(value));
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** `base` with `override` merged in (CatalogProvider.withOverride). Does not modify `base`. */
export function mergeOverride(base, override) {
  const merged = clone(base);
  for (const [section, keyOf] of Object.entries(SECTIONS)) {
    if (!Array.isArray(override?.[section])) continue;
    const byKey = new Map();
    for (const entry of merged[section] ?? []) {
      if (entry && typeof entry === 'object') byKey.set(keyOf(entry), entry);
    }
    for (const entry of override[section]) {
      if (entry && typeof entry === 'object') byKey.set(keyOf(entry), clone(entry));
    }
    merged[section] = [...byKey.values()];
  }
  for (const key of WHOLE) {
    if (override?.[key] !== undefined) merged[key] = clone(override[key]);
  }
  return merged;
}

/** The partial catalog that turns `base` into `edited` under mergeOverride (edits and additions). */
export function overrideOf(base, edited) {
  const out = {};
  for (const [section, keyOf] of Object.entries(SECTIONS)) {
    const before = new Map((base[section] ?? []).map((e) => [keyOf(e), e]));
    const changed = (edited[section] ?? []).filter((e) => !same(before.get(keyOf(e)), e));
    if (changed.length) out[section] = clone(changed);
  }
  for (const key of WHOLE) {
    if (!same(base[key], edited[key])) out[key] = clone(edited[key]);
  }
  return out;
}

/** Entries of `base` missing from `edited` (the override format cannot express removals). */
export function removedOf(base, edited) {
  const out = [];
  for (const [section, keyOf] of Object.entries(SECTIONS)) {
    const after = new Set((edited[section] ?? []).map(keyOf));
    for (const e of base[section] ?? []) if (!after.has(keyOf(e))) out.push(`${section}/${keyOf(e)}`);
  }
  return out;
}

/** Number of changed / added entries per section. */
export function changeSummary(base, edited) {
  const summary = [];
  for (const [section, keyOf] of Object.entries(SECTIONS)) {
    const before = new Map((base[section] ?? []).map((e) => [keyOf(e), e]));
    let changed = 0;
    let added = 0;
    for (const e of edited[section] ?? []) {
      const old = before.get(keyOf(e));
      if (old === undefined) added++;
      else if (!same(old, e)) changed++;
    }
    if (changed || added) summary.push({ section, changed, added });
  }
  for (const key of WHOLE) if (!same(base[key], edited[key])) summary.push({ section: key, changed: 1, added: 0 });
  return summary;
}

/** The override file's text: 2-space JSON, sections in catalog order. */
export function formatOverride(override) {
  return `${JSON.stringify(override, null, 2)}\n`;
}

/** RFC 6902 JSON Patch from `base` to `edited` (entry-level replace / add). */
export function jsonPatch(base, edited) {
  const ops = [];
  const pointer = (s) => s.replace(/~/g, '~0').replace(/\//g, '~1');
  for (const key of Object.keys(edited)) {
    const keyOf = SECTIONS[key];
    if (!keyOf || !Array.isArray(base[key])) {
      if (!same(base[key], edited[key])) ops.push({ op: base[key] === undefined ? 'add' : 'replace', path: `/${pointer(key)}`, value: clone(edited[key]) });
      continue;
    }
    const index = new Map(base[key].map((e, i) => [keyOf(e), i]));
    for (const entry of edited[key]) {
      const i = index.get(keyOf(entry));
      if (i === undefined) ops.push({ op: 'add', path: `/${pointer(key)}/-`, value: clone(entry) });
      else if (!same(base[key][i], entry)) ops.push({ op: 'replace', path: `/${pointer(key)}/${i}`, value: clone(entry) });
    }
  }
  return ops;
}

/** Applies a patch made by jsonPatch (replace / add only). */
export function applyPatch(document, ops) {
  const out = clone(document);
  for (const op of ops) {
    const parts = op.path.split('/').slice(1).map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~'));
    let target = out;
    for (const part of parts.slice(0, -1)) target = target[part];
    const last = parts[parts.length - 1];
    if (Array.isArray(target) && last === '-') target.push(clone(op.value));
    else target[last] = clone(op.value);
  }
  return out;
}

/**
 * Unified diff of two texts (Myers, line based, 3 lines of context), in `diff -u` format.
 * Returns '' when the texts are equal.
 */
export function unifiedDiff(aText, bText, aName = 'a/catalog/tinyexpression-catalog.json', bName = 'b/catalog/tinyexpression-catalog.json') {
  if (aText === bText) return '';
  const a = aText.split('\n');
  const b = bText.split('\n');
  // Common prefix and suffix first: catalog edits are local, so the Myers part stays small.
  let start = 0;
  while (start < a.length && start < b.length && a[start] === b[start]) start++;
  let endA = a.length;
  let endB = b.length;
  while (endA > start && endB > start && a[endA - 1] === b[endB - 1]) { endA--; endB--; }
  const script = myers(a.slice(start, endA), b.slice(start, endB)); // [op, text] with op ' ', '-', '+'
  const lines = [];
  for (let i = 0; i < start; i++) lines.push([' ', a[i], i, null]);
  let ai = start;
  let bi = start;
  for (const [op, text] of script) {
    if (op === ' ') lines.push([' ', text, ai++, bi++]);
    else if (op === '-') lines.push(['-', text, ai++, null]);
    else lines.push(['+', text, null, bi++]);
  }
  for (let i = endA; i < a.length; i++) lines.push([' ', a[i], i, bi++]);
  // Number the new-side lines of the prefix too.
  for (let i = 0; i < start; i++) lines[i][3] = i;
  const out = [`--- ${aName}`, `+++ ${bName}`];
  const changed = lines.map((l, i) => (l[0] !== ' ' ? i : -1)).filter((i) => i >= 0);
  let h = 0;
  while (h < changed.length) {
    let from = Math.max(0, changed[h] - 3);
    let to = Math.min(lines.length, changed[h] + 4);
    while (h + 1 < changed.length && changed[h + 1] - 3 <= to) {
      h++;
      to = Math.min(lines.length, changed[h] + 4);
    }
    h++;
    const hunk = lines.slice(from, to);
    const aStart = firstNumber(lines, from, 2);
    const bStart = firstNumber(lines, from, 3);
    const aCount = hunk.filter((l) => l[0] !== '+').length;
    const bCount = hunk.filter((l) => l[0] !== '-').length;
    out.push(`@@ -${aCount ? aStart + 1 : aStart},${aCount} +${bCount ? bStart + 1 : bStart},${bCount} @@`);
    for (const [op, text] of hunk) out.push(`${op}${text}`);
  }
  return `${out.join('\n')}\n`;
}

function firstNumber(lines, from, column) {
  for (let i = from; i < lines.length; i++) if (lines[i][column] != null) return lines[i][column];
  for (let i = from - 1; i >= 0; i--) if (lines[i][column] != null) return lines[i][column] + 1;
  return 0;
}

/** Myers' O(ND) diff; falls back to "delete all, insert all" beyond 4000 differences. */
function myers(a, b) {
  const n = a.length;
  const m = b.length;
  const max = n + m;
  if (max === 0) return [];
  const limit = Math.min(max, 4000);
  const offset = limit + 1;
  let v = new Int32Array(2 * limit + 3);
  const trace = [];
  let found = -1;
  for (let d = 0; d <= limit && found < 0; d++) {
    trace.push(v.slice());
    for (let k = -d; k <= d; k += 2) {
      let x = (k === -d || (k !== d && v[offset + k - 1] < v[offset + k + 1])) ? v[offset + k + 1] : v[offset + k - 1] + 1;
      let y = x - k;
      while (x < n && y < m && a[x] === b[y]) { x++; y++; }
      v[offset + k] = x;
      if (x >= n && y >= m) { found = d; break; }
    }
  }
  if (found < 0) return [...a.map((t) => ['-', t]), ...b.map((t) => ['+', t])];
  const script = [];
  let x = n;
  let y = m;
  for (let d = found; d > 0; d--) {
    const prev = trace[d];
    const k = x - y;
    const prevK = (k === -d || (k !== d && prev[offset + k - 1] < prev[offset + k + 1])) ? k + 1 : k - 1;
    const prevX = prev[offset + prevK];
    const prevY = prevX - prevK;
    while (x > prevX && y > prevY) { script.push([' ', a[x - 1]]); x--; y--; }
    if (x === prevX) script.push(['+', b[y - 1]]);
    else script.push(['-', a[x - 1]]);
    x = prevX;
    y = prevY;
  }
  while (x > 0 && y > 0) { script.push([' ', a[x - 1]]); x--; y--; }
  return script.reverse();
}

/** The catalog's canonical text (what the repository file must contain). */
export function exportCatalog(catalog) {
  return formatCatalog(catalog);
}
