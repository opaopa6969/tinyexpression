// FormulaInfo documents for the FormulaInfo editor (issue #212): blocks, `key:value` entries,
// comment / blank / end mark lines, and each value as the loader normalises it, with the
// mapping between the normalised value (the formula text `te_check` sees) and the document.
// Pure (no DOM): scripts/check.mjs tests it under node.
//
// A line scanner over code points that follows grammar/formula-info.ubnf (no regular
// expressions): a line starting with KEY ':' (KEY = IDENT ('.' IDENT)*, IDENT =
// [A-Za-z_][A-Za-z0-9_]*) at column 0 starts an entry, whose value runs over the following
// lines up to the next entry, the end mark line or the end of input; '#' and blank lines are
// fillers only before the first entry of a block. The Rust loader (te_formula_info) stays the
// authority on what is accepted: this scanner only colours and locates.
import { analyzeFormula } from './te-lexer.js';

export const END_MARK = '---END_OF_PART---';

const isDigit = (c) => c >= 0x30 && c <= 0x39;
const isIdentStart = (c) => (c >= 0x41 && c <= 0x5a) || (c >= 0x61 && c <= 0x7a) || c === 0x5f;
const isIdentPart = (c) => isIdentStart(c) || isDigit(c);
/** SpaceChar of the grammar: ' ', '\t', VT, FF. */
const isBlankChar = (c) => c === 0x20 || c === 0x09 || c === 0x0b || c === 0x0c;

/** Java `Character.isWhitespace` (what `String.strip` / `stripTrailing` remove). */
export function isJavaWhitespace(c) {
  if (c === 0x09 || c === 0x0a || c === 0x0b || c === 0x0c || c === 0x0d) return true;
  if (c >= 0x1c && c <= 0x1f) return true;
  if (c === 0x20 || c === 0x1680 || c === 0x2028 || c === 0x2029 || c === 0x205f || c === 0x3000) return true;
  return (c >= 0x2000 && c <= 0x2006) || (c >= 0x2008 && c <= 0x200a);
}

function isJavaBlank(text) {
  for (const ch of text) if (!isJavaWhitespace(ch.codePointAt(0))) return false;
  return true;
}

function stripTrailingLength(text) {
  let end = text.length;
  while (end > 0) {
    const low = text.charCodeAt(end - 1);
    const start = low >= 0xdc00 && low <= 0xdfff && end >= 2 ? end - 2 : end - 1;
    if (!isJavaWhitespace(text.codePointAt(start))) break;
    end = start;
  }
  return end;
}

/**
 * Lines of the document: [{from, to, next}] (JS indices; `to` excludes the line break, `next`
 * is the start of the following line). Line breaks are \r\n, \r and \n, as in the grammar.
 */
export function linesOf(text) {
  const lines = [];
  let from = 0;
  let i = 0;
  while (i < text.length) {
    const c = text.charCodeAt(i);
    if (c === 0x0a || c === 0x0d) {
      const next = c === 0x0d && text.charCodeAt(i + 1) === 0x0a ? i + 2 : i + 1;
      lines.push({ from, to: i, next });
      from = next;
      i = next;
      continue;
    }
    i += 1;
  }
  lines.push({ from, to: text.length, next: text.length });
  // A final line break does not start a line of its own unless something follows it.
  if (lines.length > 1 && lines[lines.length - 1].from === text.length) lines.pop();
  return lines;
}

/** The KEY of `KEY:` at the start of the line, or null: {keyTo (index of ':')}. */
function keyOf(text, from, to) {
  let i = from;
  for (;;) {
    if (i >= to || !isIdentStart(text.codePointAt(i))) return null;
    while (i < to && isIdentPart(text.codePointAt(i))) i++;
    if (i < to && text.codePointAt(i) === 0x2e) {
      i++;
      continue;
    }
    break;
  }
  return i < to && text.codePointAt(i) === 0x3a ? i : null;
}

/**
 * The kind of an end mark line: 'end' (the exact mark), 'end-trailing-space' (the mark and
 * spaces / tabs), 'end-trailing' (the mark and other characters), or null.
 */
export function endMarkKind(line) {
  if (!line.startsWith(END_MARK)) return null;
  const rest = line.slice(END_MARK.length);
  if (rest === '') return 'end';
  let blank = true;
  // Spaces and tabs only (issue #211: VT / FF after the mark are rejected).
  for (const ch of rest) if (ch !== ' ' && ch !== '\t') blank = false;
  return blank ? 'end-trailing-space' : 'end-trailing';
}

/**
 * Whether an end mark line of `kind` closes a block. Issue #211: the Java/Rust loaders accept
 * trailing spaces after the mark (and reject other trailing characters); before #211 only the
 * exact mark closed a block. Set by `setEndMarkTrailingSpace` from what the wasm loader does.
 */
let trailingSpaceEnds = true;
export function setEndMarkTrailingSpace(accepted) {
  trailingSpaceEnds = accepted;
}
export function closesBlock(kind) {
  return kind === 'end' || (kind === 'end-trailing-space' && trailingSpaceEnds);
}

/**
 * Parses the document:
 * {lines, blocks: [{from, to, index, entries, fillers, end: {from, to, kind} | null}],
 *  junk: [{from, to}] (lines the grammar rejects: not an entry, filler or end mark),
 *  endLines: [{from, to, kind}] (every line starting with the end mark)}
 * An entry is {key, from, keyTo (index of ':'), valueFrom, valueTo (raw value end, line breaks
 * included), lines: [{from, to, comment, blank}], block (index)}.
 */
export function parseFormulaInfo(text) {
  const lines = linesOf(text);
  const blocks = [];
  const junk = [];
  const endLines = [];
  let block = null;
  let entry = null;
  const openBlock = (from) => {
    block = { from, to: from, index: blocks.length, entries: [], fillers: [], end: null };
    blocks.push(block);
  };
  for (const line of lines) {
    if (text.length === 0) break;
    const body = text.slice(line.from, line.to);
    const endKind = endMarkKind(body);
    if (endKind) endLines.push({ from: line.from, to: line.to, kind: endKind });
    if (!block) openBlock(line.from);
    if (endKind && closesBlock(endKind)) {
      if (entry) entry.valueTo = line.from;
      // A block needs a line before its end mark (a leading mark / a doubled mark is rejected).
      if (!block.entries.length && !block.fillers.length) junk.push({ from: line.from, to: line.to });
      block.end = { from: line.from, to: line.to, kind: endKind };
      block.to = line.next;
      entry = null;
      block = null;
      continue;
    }
    const keyTo = keyOf(text, line.from, line.to);
    if (keyTo != null) {
      if (entry) entry.valueTo = line.from;
      entry = {
        key: text.slice(line.from, keyTo), from: line.from, keyTo, valueFrom: keyTo + 1, valueTo: line.next,
        lines: [], block: block.index,
      };
      entry.lines.push(valueLine(text, keyTo + 1, line.to, true));
      block.entries.push(entry);
      block.to = line.next;
      continue;
    }
    if (entry) {
      entry.lines.push(valueLine(text, line.from, line.to, false));
      entry.valueTo = line.next;
      block.to = line.next;
      continue;
    }
    // Before the first entry of a block: '#' comment and blank lines, else junk.
    let blank = true;
    for (const ch of body) if (!isBlankChar(ch.codePointAt(0))) blank = false;
    if (body.startsWith('#') || blank) {
      block.fillers.push({ from: line.from, to: line.to, comment: body.startsWith('#') });
    } else {
      junk.push({ from: line.from, to: line.to });
    }
    block.to = line.next;
  }
  // Blocks of fillers only are dropped by the loader; keep them (for colouring) but mark them.
  return { lines, blocks, junk, endLines };
}

function valueLine(text, from, to, first) {
  const body = text.slice(from, to);
  return { from, to, first, comment: !first && body.startsWith('#'), blank: isJavaBlank(body) };
}

/**
 * The value as the loader normalises it (`Entry.value()` in Rust, `FormulaInfoElementParser`
 * in Java): the raw value split on '\n', lines starting with '#' and blank lines dropped, the
 * rest joined by '\n', trailing whitespace stripped. With the document mapping of every kept
 * line: {text, kept: [{docFrom, textFrom, length}]}. `null` for a zero-length raw value.
 */
export function normalizedValue(text, entry) {
  const raw = text.slice(entry.valueFrom, entry.valueTo);
  if (raw === '') return null;
  const kept = [];
  const parts = [];
  let start = 0;
  let textFrom = 0;
  for (;;) {
    const nl = raw.indexOf('\n', start);
    const end = nl < 0 ? raw.length : nl;
    const line = raw.slice(start, end);
    if (!line.startsWith('#') && !isJavaBlank(line)) {
      kept.push({ docFrom: entry.valueFrom + start, textFrom, length: line.length });
      parts.push(line);
      textFrom += line.length + 1;
    }
    if (nl < 0) break;
    start = nl + 1;
  }
  const joined = parts.join('\n');
  const length = stripTrailingLength(joined);
  const normalized = joined.slice(0, length);
  // Clip the kept lines to the stripped text.
  const clipped = [];
  for (const k of kept) {
    if (k.textFrom > length) break;
    clipped.push({ ...k, length: Math.min(k.length, length - k.textFrom) });
  }
  return { text: normalized, kept: clipped };
}

/** Index into a normalised value → document index (end of the line for the joining '\n'). */
export function valueToDoc(value, index) {
  if (!value.kept.length) return 0;
  for (const k of value.kept) {
    if (index <= k.textFrom + k.length) return k.docFrom + Math.max(0, index - k.textFrom);
  }
  const last = value.kept[value.kept.length - 1];
  return last.docFrom + last.length;
}

/** Document index → index into the normalised value, or null outside the kept lines. */
export function docToValue(value, pos) {
  for (const k of value.kept) {
    if (pos >= k.docFrom && pos <= k.docFrom + k.length) return k.textFrom + (pos - k.docFrom);
  }
  return null;
}

/** The entry whose value contains `pos` (the key line after ':' or a continuation line). */
export function entryAt(doc, pos) {
  for (const block of doc.blocks) {
    for (const entry of block.entries) {
      if (pos >= entry.valueFrom && (pos < entry.valueTo || (pos === entry.valueTo && entry === block.entries[block.entries.length - 1] && !block.end))) {
        return entry;
      }
    }
  }
  return null;
}

/** The last value of `key` in each block: [{block, entry, value}] (the loader keeps the last). */
export function valuesOf(text, doc, key) {
  const out = [];
  for (const block of doc.blocks) {
    const entries = block.entries.filter((e) => e.key === key);
    if (!entries.length) continue;
    const entry = entries[entries.length - 1];
    out.push({ block, entry, value: normalizedValue(text, entry) });
  }
  return out;
}

/**
 * Colouring and brackets of a FormulaInfo document: tokens [{from, to, type}] with types
 * fi-key, fi-key-unknown, fi-colon, fi-comment, fi-end, fi-end-bad, fi-junk, fi-value and the
 * formula token types (te-lexer.js) inside `formula:` values; brackets (te-lexer.js
 * `bracketsOf`, one bracket set per formula, positions in the document).
 */
export function analyzeFormulaInfo(text, { lexicon, isKnownKey = () => true } = {}) {
  const doc = parseFormulaInfo(text);
  const tokens = [];
  const brackets = [];
  for (const block of doc.blocks) {
    for (const filler of block.fillers) {
      if (filler.comment) tokens.push({ from: filler.from, to: filler.to, type: 'fi-comment' });
    }
    for (const entry of block.entries) {
      tokens.push({ from: entry.from, to: entry.keyTo, type: isKnownKey(entry.key) ? 'fi-key' : 'fi-key-unknown' });
      tokens.push({ from: entry.keyTo, to: entry.keyTo + 1, type: 'fi-colon' });
      if (entry.key === 'formula') {
        const value = normalizedValue(text, entry);
        for (const line of entry.lines) if (line.comment) tokens.push({ from: line.from, to: line.to, type: 'fi-comment' });
        if (!value) continue;
        const analysis = analyzeFormula(value.text, lexicon);
        const offset = brackets.length;
        for (const t of analysis.tokens) {
          const from = valueToDoc(value, t.from);
          const to = valueToDoc(value, t.to);
          if (t.type === 'bracket') continue;
          // A token over dropped lines (a multi-line comment / string): colour each kept piece.
          for (const k of value.kept) {
            const a = Math.max(from, k.docFrom);
            const b = Math.min(to, k.docFrom + k.length);
            if (b > a) tokens.push({ from: a, to: b, type: t.type });
          }
        }
        for (const b of analysis.brackets) {
          brackets.push({ ...b, from: valueToDoc(value, b.from), partner: b.partner >= 0 ? b.partner + offset : -1 });
        }
      } else {
        for (const line of entry.lines) {
          if (line.comment) tokens.push({ from: line.from, to: line.to, type: 'fi-comment' });
          else if (line.to > line.from && !line.blank) tokens.push({ from: line.from, to: line.to, type: 'fi-value' });
        }
      }
    }
    if (block.end) tokens.push({ from: block.end.from, to: block.end.to, type: 'fi-end' });
  }
  for (const line of doc.endLines) {
    // An end mark line that did not close a block: with trailing characters it is a value line
    // (before #211) or a syntax error (after #211); either way not what the author meant.
    if (!doc.blocks.some((b) => b.end?.from === line.from)) {
      removeOverlapping(tokens, line.from, line.to);
      tokens.push({ from: line.from, to: line.to, type: 'fi-end-bad' });
    }
  }
  for (const j of doc.junk) {
    removeOverlapping(tokens, j.from, j.to);
    tokens.push({ from: j.from, to: j.to, type: 'fi-junk' });
  }
  tokens.sort((a, b) => a.from - b.from || a.to - b.to);
  for (const b of brackets) tokens.push({ from: b.from, to: b.from + 1, type: 'bracket' });
  tokens.sort((a, b) => a.from - b.from || a.to - b.to);
  return { doc, tokens, brackets };
}

function removeOverlapping(tokens, from, to) {
  for (let i = tokens.length - 1; i >= 0; i--) {
    if (tokens[i].from < to && tokens[i].to > from) tokens.splice(i, 1);
  }
}
