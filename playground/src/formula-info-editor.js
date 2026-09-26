// The FormulaInfo editor (issue #212): CodeMirror 6 with FormulaInfo colouring (formula-info-
// syntax.js, the tinyexpression colouring inside `formula:` values), rainbow brackets,
// completion, diagnostics (formula-info-diagnostics.js) and hover. Inside a `formula:` value
// the formula editor's completion and hover (editor-support.js) run on the formula text the
// loader sees, with positions mapped between it and the document.
import { CompletionContext, snippetCompletion } from '@codemirror/autocomplete';
import { EditorState } from '@codemirror/state';
import { hoverTooltip } from '@codemirror/view';
import { ja, en } from '../../catalog/scripts/catalog-lib.mjs';
import { completionSource, formulaHoverAt, renderHover } from './editor-support.js';
import { codeBlocksOf, codeBlockAt } from './code-block.js';
import {
  END_MARK, parseFormulaInfo, normalizedValue, docToValue, valueToDoc, entryAt, valuesOf, linesOf,
} from './formula-info-syntax.js';
import { formulaInfoKeys, settingOf, isLiteralKey } from './formula-info-diagnostics.js';

const isIdentPart = (c) => (c >= 0x41 && c <= 0x5a) || (c >= 0x61 && c <= 0x7a) || c === 0x5f || (c >= 0x30 && c <= 0x39);

/** The text from the line start to `pos` when it is only key characters (a key being typed). */
function keyPrefix(text, lineFrom, pos) {
  const prefix = text.slice(lineFrom, pos);
  for (const ch of prefix) {
    const c = ch.codePointAt(0);
    if (!isIdentPart(c) && c !== 0x2e) return null;
  }
  return prefix;
}

function isEndMarkPrefix(prefix) {
  return prefix.length > 0 && END_MARK.startsWith(prefix);
}

/** The completion info panel of a key: the catalog description (ja, en), type, values, note. */
function keyInfo(setting) {
  const box = document.createElement('div');
  box.className = 'cm-doc';
  const line = (text, className) => {
    if (!text) return;
    const div = document.createElement('div');
    if (className) div.className = className;
    div.textContent = text;
    box.append(div);
  };
  line(ja(setting.description));
  if (setting.description?.en && setting.description?.ja) line(setting.description.en, 'cm-doc-en');
  line(setting.values ? `値: ${setting.values.join(' | ')}${setting.default ? `（既定 ${setting.default}）` : ''}` : '', 'cm-doc-extra');
  line(setting.note ? `注意: ${ja(setting.note)}` : '', 'cm-doc-extra');
  return box;
}

function keyOptions(catalog) {
  const options = [];
  for (const [name, entry] of formulaInfoKeys(catalog)) {
    if (!isLiteralKey(name)) continue;
    const option = {
      label: `${name}:`,
      type: 'property',
      detail: entry.type ?? '',
      info: () => keyInfo(entry),
      boost: name === 'formula' || name === 'calculatorName' ? 2 : 0,
    };
    options.push(name === 'formula' ? snippetCompletion('formula:\n${}', option) : option);
  }
  return options;
}

/** The word at the end of `text` made of characters other than `,` and whitespace. */
function lastPiece(text) {
  let i = text.length;
  while (i > 0 && text[i - 1] !== ',' && text[i - 1] !== ' ' && text[i - 1] !== '\t') i--;
  return i;
}

/**
 * The completion source: `key:` and the end mark at the start of a line, the values of enum
 * keys (catalog settings: executionBackend, resultType …), `dependsOn:` names from the
 * document's calculatorNames, and the formula editor's completion inside `formula:` values.
 */
export function formulaInfoCompletionSource(getRuntime, getCatalog, getContextVariables) {
  const formulaSource = completionSource(getRuntime, getCatalog, getContextVariables);
  return async (context) => {
    const text = context.state.doc.toString();
    const catalog = getCatalog();
    const pos = context.pos;
    const line = context.state.doc.lineAt(pos);
    const doc = parseFormulaInfo(text);
    const entry = entryAt(doc, pos);
    const options = [];
    let from = pos;

    // Issue #216: nothing inside a ```java block of a formula (it is Java, not keys or formula).
    if (entry?.key === 'formula') {
      const value = normalizedValue(text, entry);
      const at = value ? docToValue(value, pos) : null;
      if (at != null && codeBlockAt(codeBlocksOf(value.text), at)) return null;
    }

    // At the start of a line: keys and the end mark (also inside a value: a key line starts a
    // new entry there).
    const prefix = keyPrefix(text, line.from, pos);
    const dashes = text.slice(line.from, pos);
    if (isEndMarkPrefix(dashes) && dashes.startsWith('-')) {
      return { from: line.from, options: [{ label: END_MARK, type: 'keyword', info: 'ブロックの終わり（行に単独で書く）' }], validFor: (t) => END_MARK.startsWith(t) };
    }
    const keyLine = prefix != null && (prefix !== '' || context.explicit);
    const inKeyPart = !entry || entry.from !== line.from || pos <= entry.keyTo;
    if (keyLine && inKeyPart) {
      options.push(...keyOptions(catalog));
      if (prefix === '') options.push({ label: END_MARK, type: 'keyword', info: 'ブロックの終わり（行に単独で書く）', boost: -1 });
      from = line.from;
    }

    if (entry && !(entry.from === line.from && pos <= entry.keyTo)) {
      const setting = settingOf(formulaInfoKeys(catalog), entry.key);
      const valueStart = entry.from === line.from ? entry.valueFrom : line.from;
      if (entry.key === 'formula') {
        const value = normalizedValue(text, entry);
        const at = value ? docToValue(value, pos) : null;
        if (value && at != null) {
          const inner = new CompletionContext(EditorState.create({ doc: value.text }), at, context.explicit);
          const result = await formulaSource(inner);
          if (result) {
            const mappedFrom = valueToDoc(value, result.from);
            if (!options.length) return { ...result, from: mappedFrom };
            // A word at the line start: both keys and formula words.
            return { from: Math.min(from, mappedFrom), options: [...options, ...result.options] };
          }
        }
      } else if (entry.key === 'dependsOn') {
        const own = valuesOf(text, doc, 'calculatorName').find((v) => v.block.index === entry.block)?.value?.text;
        const start = Math.max(valueStart, line.from + lastPiece(text.slice(line.from, pos)));
        for (const { value } of valuesOf(text, doc, 'calculatorName')) {
          if (value?.text && value.text !== own) options.push({ label: value.text, type: 'variable', detail: 'calculatorName' });
        }
        return { from: start, options, validFor: (t) => !t.includes(',') };
      } else if (setting?.values && entry.from === line.from) {
        const start = entry.valueFrom + (text.slice(entry.valueFrom, pos).length - text.slice(entry.valueFrom, pos).trimStart().length);
        const values = setting.values.map((v) => ({ label: v, type: 'enum', detail: entry.key, info: ja(setting.description) }));
        return { from: start, options: values };
      }
    }
    if (!options.length) return null;
    return { from, options };
  };
}

/** 1-based line number of `pos`. */
function lineNumber(text, pos) {
  return linesOf(text.slice(0, pos)).length + (pos > 0 && (text[pos - 1] === '\n' || text[pos - 1] === '\r') ? 1 : 0);
}

/** Hover content at `pos` of a FormulaInfo document ({title, rows}) or null. */
export function formulaInfoHoverAt(catalog, contextVariables, text, pos, externals = []) {
  const doc = parseFormulaInfo(text);
  const keys = formulaInfoKeys(catalog);
  for (const block of doc.blocks) {
    if (block.end && pos >= block.end.from && pos <= block.end.to) {
      return { from: block.end.from, to: block.end.to, content: { title: END_MARK, rows: [['', 'ブロックの終わり。この行の次から新しい FormulaInfo。'], ['English', 'End of a FormulaInfo block.']] } };
    }
    for (const entry of block.entries) {
      if (pos >= entry.from && pos <= entry.keyTo) {
        const setting = settingOf(keys, entry.key);
        const rows = [];
        if (setting) {
          rows.push(['説明', ja(setting.description)]);
          if (setting.description?.en) rows.push(['English', en(setting.description)]);
          if (setting.type) rows.push(['型', setting.type]);
          if (setting.values) rows.push(['値', `${setting.values.join(' | ')}${setting.default ? `（既定 ${setting.default}）` : ''}`]);
          if (setting.note) rows.push(['注意', ja(setting.note)]);
        } else {
          rows.push(['未知のキー', 'カタログの FormulaInfo 設定にありません。loader は追加属性として保持するだけで、式には使われません。']);
        }
        return { from: entry.from, to: entry.keyTo, content: { title: `${entry.key}:`, rows } };
      }
    }
  }
  const entry = entryAt(doc, pos);
  if (!entry) return null;
  if (entry.key === 'formula') {
    const value = normalizedValue(text, entry);
    const at = value ? docToValue(value, pos) : null;
    if (at == null) return null;
    const found = formulaHoverAt(catalog, contextVariables, value.text, at, externals);
    if (!found) return null;
    return {
      from: valueToDoc(value, found.from), to: valueToDoc(value, found.to),
      anchor: found.anchor == null ? undefined : valueToDoc(value, found.anchor), content: found.content,
    };
  }
  if (entry.key === 'dependsOn') {
    const value = normalizedValue(text, entry);
    const at = value ? docToValue(value, pos) : null;
    if (at == null) return null;
    let start = at;
    let end = at;
    while (start > 0 && value.text[start - 1] !== ',') start--;
    while (end < value.text.length && value.text[end] !== ',') end++;
    const name = value.text.slice(start, end);
    const target = valuesOf(text, doc, 'calculatorName').find((v) => v.value?.text === name);
    const rows = target
      ? [['calculatorName', `ブロック ${target.block.index + 1}（${lineNumber(text, target.entry.from)} 行目）`]]
      : [['未定義', '文書内にこの calculatorName はありません（FI001）']];
    return { from: valueToDoc(value, start), to: valueToDoc(value, end), content: { title: name, rows } };
  }
  return null;
}

export function formulaInfoHover(getCatalog, getContextVariables, getExternals = () => []) {
  return hoverTooltip((view, pos) => {
    const found = formulaInfoHoverAt(getCatalog(), getContextVariables(), view.state.doc.toString(), pos, getExternals());
    if (!found) return null;
    return { pos: found.anchor ?? found.from, end: found.to, above: true, create: () => ({ dom: renderHover(found.content) }) };
  });
}
