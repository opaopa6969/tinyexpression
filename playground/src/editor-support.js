// Completion and hover for the CodeMirror editor from the language catalog, the parser's
// expected set at the cursor (`te_check` on the text before the word) and the variables of the
// CalculationContext panel (issue #201).
import { snippetCompletion } from '@codemirror/autocomplete';
import { hoverTooltip } from '@codemirror/view';
import { ja, en, lookupVariable } from '../../catalog/scripts/catalog-lib.mjs';
import { codePointToIndex, declaredVariables } from './context.js';

/** LSP snippet syntax (`$1`, `${1:x}`, `$0`, `\$`) -> CodeMirror snippet syntax. */
export function toCodeMirrorSnippet(snippet) {
  return snippet
    .replace(/\\\$/g, '\u0000')
    .replace(/\$0/g, '${}')
    .replace(/\$(\d+)/g, '${$1}')
    .replace(/\u0000/g, '$');
}

/** Terminals the parser accepts where the word at `wordStart` begins, or null when unknown. */
export function expectedAt(te, text, wordStart) {
  const before = text.slice(0, wordStart);
  const { result } = te.check(before);
  if (result.ok || result.stage !== 'parse') return null;
  const at = codePointToIndex(before, result.diagnostic.offset);
  if (at !== before.length && before.slice(at).trim() !== '') return null;
  return new Set(result.diagnostic.expected);
}

function doc(description, extra = '') {
  return () => {
    const box = document.createElement('div');
    box.className = 'cm-doc';
    const main = document.createElement('div');
    main.textContent = ja(description);
    box.append(main);
    if (description?.en && description?.ja) {
      const english = document.createElement('div');
      english.className = 'cm-doc-en';
      english.textContent = description.en;
      box.append(english);
    }
    if (extra) {
      const more = document.createElement('div');
      more.className = 'cm-doc-extra';
      more.textContent = extra;
      box.append(more);
    }
    return box;
  };
}

/**
 * @param getRuntime () => runtime (te) or null
 * @param getCatalog () => catalog
 * @param getContextVariables () => [{name, type, value}]
 */
export function completionSource(getRuntime, getCatalog, getContextVariables) {
  return (context) => {
    const catalog = getCatalog();
    const te = getRuntime();
    const word = context.matchBefore(/\$[A-Za-z0-9_]*|\.[A-Za-z]*|[A-Za-z_][A-Za-z0-9_]*/);
    if (!word && !context.explicit) return null;
    const from = word ? word.from : context.pos;
    const text = context.state.doc.toString();
    const typed = word ? word.text : '';

    if (typed.startsWith('$')) {
      const options = [];
      const seen = new Set();
      for (const v of getContextVariables()) {
        if (!v.name || seen.has(v.name)) continue;
        seen.add(v.name);
        options.push({ label: `$${v.name}`, type: 'variable', detail: `${v.type} = ${v.value}`, boost: 3, info: 'CalculationContext の変数' });
      }
      for (const name of declaredVariables(text)) {
        if (seen.has(name)) continue;
        seen.add(name);
        options.push({ label: `$${name}`, type: 'variable', detail: '式内で宣言', boost: 2 });
      }
      for (const v of catalog.variables) {
        const label = v.match === 'prefixWithSuffix' ? `$${v.name}${v.separator}` : `$${v.name}`;
        if (seen.has(label.slice(1))) continue;
        seen.add(label.slice(1));
        options.push({ label, type: 'variable', detail: `${v.type ?? ''} [${v.context}]`.trim(), info: doc(v.description, `group: ${v.group}`) });
      }
      return { from, options, validFor: /^\$[A-Za-z0-9_]*$/ };
    }

    if (typed.startsWith('.')) {
      const options = catalog.functions.filter((f) => f.kind === 'method').map((f) => ({
        label: f.name, type: 'method', detail: f.signature, info: doc(f.description, (f.examples ?? []).join('  ')),
        apply: `${f.name}()`,
      }));
      return { from, options, validFor: /^\.[A-Za-z]*$/ };
    }

    const expected = te ? expectedAt(te, text, from) : null;
    const allowed = (name) => !expected || expected.has(name);
    const options = [];
    for (const f of catalog.functions) {
      if (f.kind !== 'function' || !allowed(f.name)) continue;
      const option = { label: f.name, type: 'function', detail: f.signature, info: doc(f.description, (f.examples ?? []).join('  ')) };
      options.push(f.snippet ? snippetCompletion(toCodeMirrorSnippet(f.snippet), option) : option);
    }
    for (const k of catalog.keywords) {
      if (!allowed(k.name)) continue;
      options.push({ label: k.name, type: 'keyword', info: doc(k.description) });
      if (k.snippet) {
        options.push(snippetCompletion(toCodeMirrorSnippet(k.snippet), { label: k.name, type: 'text', detail: 'snippet', info: doc(k.description), boost: -1 }));
      }
    }
    for (const v of catalog.values) {
      if (!allowed(v.name)) continue;
      options.push({ label: v.name, type: 'enum', detail: v.type, info: doc(v.description) });
    }
    if (expected?.has('$')) {
      for (const v of getContextVariables()) {
        if (v.name) options.push({ label: `$${v.name}`, type: 'variable', detail: `${v.type} = ${v.value}`, boost: 3 });
      }
    }
    return { from, options, validFor: /^[A-Za-z_][A-Za-z0-9_]*$/ };
  };
}

/** The `$name` / `.method` / word around `pos`: {start, end, word}. */
export function wordAround(text, pos) {
  let start = pos;
  let end = pos;
  const isWord = (c) => /[A-Za-z0-9_$.]/.test(c);
  while (start > 0 && isWord(text[start - 1])) start--;
  while (end < text.length && isWord(text[end])) end++;
  let word = text.slice(start, end);
  // `$name.trim` -> hover the part under the cursor.
  const dot = word.indexOf('.', 1);
  if (dot > 0) {
    if (start + dot <= pos) {
      start += dot;
      word = text.slice(start, end);
    } else {
      end = start + dot;
      word = text.slice(start, end);
    }
  }
  return { start, end, word };
}

/** Markdown-free hover content (DOM) for a word, from the catalog and the context panel. */
export function hoverContent(catalog, contextVariables, formula, word) {
  const rows = [];
  if (word.startsWith('$')) {
    const name = word.slice(1);
    const ctx = contextVariables.find((v) => v.name === name);
    if (ctx) rows.push(['CalculationContext', `${ctx.type} = ${ctx.value}`]);
    if (declaredVariables(formula).has(name)) rows.push(['宣言', '式内の var 宣言']);
    const v = lookupVariable(catalog, name);
    if (v) {
      rows.push(['説明', ja(v.description)]);
      rows.push(['カタログ', `${v.group} [${v.context}]${v.type ? ` ${v.type}` : ''}`]);
    }
    if (!rows.length) rows.push(['未定義', 'CalculationContext にもカタログにもありません（TE022）']);
    return { title: word, rows };
  }
  const bare = word.replace(/^\./, '');
  const f = catalog.functions.find((x) => x.name === word)
    ?? catalog.functions.find((x) => x.name === bare)
    ?? catalog.functions.find((x) => x.name === `.${bare}`);
  if (f) {
    rows.push(['', f.signature]);
    rows.push(['説明', ja(f.description)]);
    if (f.description?.en) rows.push(['English', en(f.description)]);
    if (f.examples?.length) rows.push(['例', f.examples.join('   ')]);
    if (f.reads?.length) rows.push(['読む変数', f.reads.map((r) => `$${r}`).join(', ')]);
    return { title: f.name, rows };
  }
  const k = catalog.keywords.find((x) => x.name === word);
  if (k) return { title: word, rows: [['キーワード', ja(k.description)], ...(k.description?.en ? [['English', k.description.en]] : [])] };
  const value = catalog.values.find((x) => x.name === word);
  if (value) return { title: word, rows: [[value.type, ja(value.description)]] };
  return null;
}

/** The hover DOM of a `hoverContent` result ({title, rows: [[label, value]]}). */
export function renderHover(content) {
  const dom = document.createElement('div');
  dom.className = 'cm-hover-doc';
  const title = document.createElement('div');
  title.className = 'cm-hover-title';
  title.textContent = content.title;
  dom.append(title);
  for (const [label, value] of content.rows) {
    const row = document.createElement('div');
    row.className = 'cm-hover-row';
    if (label) {
      const l = document.createElement('span');
      l.className = 'cm-hover-label';
      l.textContent = label;
      row.append(l);
    }
    const v = document.createElement('span');
    v.textContent = value;
    row.append(v);
    dom.append(row);
  }
  return dom;
}

export function hoverExtension(getCatalog, getContextVariables) {
  return hoverTooltip((view, pos) => {
    const text = view.state.doc.toString();
    const { start, end, word } = wordAround(text, pos);
    if (!word || word === '$' || word === '.') return null;
    const content = hoverContent(getCatalog(), getContextVariables(), text, word);
    if (!content) return null;
    return { pos: start, end, above: true, create: () => ({ dom: renderHover(content) }) };
  });
}
