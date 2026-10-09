// tinyexpression tokens for the editors' colouring and brackets (issue #212). Pure (no DOM):
// scripts/check.mjs tests it under node, the formula editor and the FormulaInfo editor (the
// `formula:` values) share it.
//
// A code-point scanner (no regular expressions): the text is walked one code point at a time,
// so a surrogate pair is never split, and every position is a JS string index (what CodeMirror
// uses). The token classes follow the VS Code grammar (tools/tinyexpression-p4-lsp-vscode/
// syntaxes/tinyexpression.tmLanguage.json): `//` and `/* */` comments, '…' / "…" strings with
// `\` escapes, numbers, `$variables`, keywords, `name(` / `.name` calls, ```java blocks.
// Issue #216: a ```java:ClassName block (code-block.js) is coloured as Java — the fences
// (`code-fence`, the class `type`) and the body's Java tokens, whose brackets pair among
// themselves (`group`); a stray ``` that opens no block keeps the plain `code` colour.
import { codeBlocksOf, codeBlockTokens } from './code-block.js';

/** The opening bracket of each closing one. */
const PAIRS = { ')': '(', ']': '[', '}': '{' };
const OPENING = new Set(['(', '[', '{']);

/** Keywords that are values (colored as constants). */
const CONSTANTS = new Set(['true', 'false']);

const isDigit = (c) => c >= 0x30 && c <= 0x39;
const isIdentStart = (c) => (c >= 0x41 && c <= 0x5a) || (c >= 0x61 && c <= 0x7a) || c === 0x5f;
const isIdentPart = (c) => isIdentStart(c) || isDigit(c);
const isSpace = (c) => c === 0x20 || c === 0x09 || c === 0x0a || c === 0x0d || c === 0x0b || c === 0x0c;

/** Width in UTF-16 units of the code point at `i`. */
const width = (text, i) => (text.codePointAt(i) > 0xffff ? 2 : 1);

function scanWhile(text, i, test) {
  while (i < text.length && test(text.codePointAt(i))) i += width(text, i);
  return i;
}

/** `from` points at `quote`; returns the index after the closing quote (or the end). */
function scanString(text, from, quote) {
  let i = from + 1;
  while (i < text.length) {
    const c = text.codePointAt(i);
    if (c === 0x5c) {
      i += 1;
      if (i < text.length) i += width(text, i);
      continue;
    }
    i += width(text, i);
    if (c === quote) break;
  }
  return i;
}

function startsWith(text, i, literal) {
  return text.startsWith(literal, i);
}

/**
 * Tokens of a formula: [{from, to, type}] with type one of comment, string, number, variable,
 * keyword, constant, function, method, identifier, operator, bracket, code. Whitespace is not a
 * token.
 * @param keywords names coloured as keywords (the catalog's keywords); `true`/`false` are constants
 * @param values names coloured as constants (the catalog's values: MONDAY …)
 */
export function tokenize(text, { keywords = new Set(), values = new Set() } = {}, blocks = codeBlocksOf(text)) {
  const tokens = [];
  const push = (from, to, type) => { if (to > from) tokens.push({ from, to, type }); };
  const blockAt = new Map(blocks.map((b, index) => [b.from, index]));
  let i = 0;
  while (i < text.length) {
    const c = text.codePointAt(i);
    const w = width(text, i);
    if (isSpace(c)) {
      i += w;
      continue;
    }
    if (blockAt.has(i)) {
      const index = blockAt.get(i);
      for (const t of codeBlockTokens(text, blocks[index], index)) if (t.to > t.from) tokens.push(t);
      i = blocks[index].to;
      continue;
    }
    if (startsWith(text, i, '```')) {
      const close = text.indexOf('```', i + 3);
      const end = close < 0 ? text.length : close + 3;
      push(i, end, 'code');
      i = end;
      continue;
    }
    if (startsWith(text, i, '//')) {
      const end = scanWhile(text, i, (x) => x !== 0x0a && x !== 0x0d);
      push(i, end, 'comment');
      i = end;
      continue;
    }
    if (startsWith(text, i, '/*')) {
      const close = text.indexOf('*/', i + 2);
      const end = close < 0 ? text.length : close + 2;
      push(i, end, 'comment');
      i = end;
      continue;
    }
    if (c === 0x27 || c === 0x22) {
      const end = scanString(text, i, c);
      push(i, end, 'string');
      i = end;
      continue;
    }
    if (isDigit(c)) {
      let end = scanWhile(text, i, isDigit);
      if (text.codePointAt(end) === 0x2e && isDigit(text.codePointAt(end + 1) ?? -1)) end = scanWhile(text, end + 1, isDigit);
      const e = text.codePointAt(end);
      if (e === 0x65 || e === 0x45) {
        let j = end + 1;
        const sign = text.codePointAt(j);
        if (sign === 0x2b || sign === 0x2d) j++;
        if (isDigit(text.codePointAt(j) ?? -1)) end = scanWhile(text, j, isDigit);
      }
      push(i, end, 'number');
      i = end;
      continue;
    }
    if (c === 0x24 && isIdentStart(text.codePointAt(i + 1) ?? -1)) {
      const end = scanWhile(text, i + 1, isIdentPart);
      push(i, end, 'variable');
      i = end;
      continue;
    }
    if (isIdentStart(c)) {
      const end = scanWhile(text, i, isIdentPart);
      const word = text.slice(i, end);
      const afterSpaces = scanWhile(text, end, (x) => x === 0x20 || x === 0x09);
      const previous = tokens[tokens.length - 1];
      const afterDot = previous && previous.type === 'operator' && previous.to === i && text[previous.from] === '.';
      let type = 'identifier';
      if (afterDot) type = 'method';
      else if (CONSTANTS.has(word) || values.has(word)) type = 'constant';
      else if (keywords.has(word)) type = 'keyword';
      else if (text.codePointAt(afterSpaces) === 0x28) type = 'function';
      push(i, end, type);
      i = end;
      continue;
    }
    if (OPENING.has(text[i]) || PAIRS[text[i]]) {
      push(i, i + 1, 'bracket');
      i += 1;
      continue;
    }
    push(i, i + w, 'operator');
    i += w;
  }
  return tokens;
}

/**
 * Brackets of the bracket tokens, with their nesting depth (0 = outermost) and partner:
 * [{from, char, depth, partner (index into the result) | -1}]. A closing bracket pairs with the
 * innermost open bracket of its kind (brackets opened after that one stay unmatched); with
 * none open it is unmatched (partner -1). Opening brackets left open at the end are unmatched.
 * Tokens with a `group` (a Java code block's) nest and pair only within their group.
 */
export function bracketsOf(text, tokens) {
  const brackets = [];
  const stacks = new Map();
  for (const token of tokens) {
    if (token.type !== 'bracket') continue;
    const char = text[token.from];
    const index = brackets.length;
    const group = token.group ?? '';
    if (!stacks.has(group)) stacks.set(group, []);
    const stack = stacks.get(group);
    if (OPENING.has(char)) {
      brackets.push({ from: token.from, char, depth: stack.length, partner: -1 });
      stack.push(index);
      continue;
    }
    // The innermost open bracket of the same kind; the ones opened after it stay unmatched.
    let k = stack.length - 1;
    while (k >= 0 && brackets[stack[k]].char !== PAIRS[char]) k--;
    if (k >= 0) {
      const open = stack[k];
      stack.length = k;
      brackets[open].partner = index;
      brackets.push({ from: token.from, char, depth: brackets[open].depth, partner: open });
    } else {
      brackets.push({ from: token.from, char, depth: stack.length, partner: -1 });
    }
  }
  return brackets;
}

/** The bracket at the cursor (just after it, else just before it) and its partner, or null. */
export function bracketAtCursor(brackets, pos) {
  const at = brackets.findIndex((b) => b.from === pos);
  const before = at >= 0 ? at : brackets.findIndex((b) => b.from === pos - 1);
  if (before < 0) return null;
  const bracket = brackets[before];
  return { bracket, partner: bracket.partner >= 0 ? brackets[bracket.partner] : null };
}

/** Number of depth colours (rainbow brackets): `te-bracket-d0` … `te-bracket-d5`. */
export const BRACKET_COLOURS = 6;

/** Keyword / value sets of a catalog, for `tokenize`. */
export function lexiconOf(catalog) {
  return {
    keywords: new Set((catalog?.keywords ?? []).map((k) => k.name)),
    values: new Set((catalog?.values ?? []).map((v) => v.name)),
  };
}

/** Tokens, brackets and code blocks (code-block.js) of a whole formula (the formula editor). */
export function analyzeFormula(text, lexicon) {
  const codeBlocks = codeBlocksOf(text);
  const tokens = tokenize(text, lexicon, codeBlocks);
  return { tokens, brackets: bracketsOf(text, tokens), codeBlocks };
}
