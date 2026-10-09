// Fenced Java code blocks (```java:ClassName) in formulas (issue #216). Pure (no DOM):
// scripts/check.mjs tests it under node; the formula editor and the FormulaInfo editor share it.
//
// Java compiles such a block and `import ClassName#method as alias;` + `external returning as T
// alias(...)` calls it. The playground runs on the Rust evaluator (wasm, no JVM): the block is
// never compiled or run, only declares the class, and the calls are answered by the external
// stub values of the CalculationContext panel ("external（仮の値）"). Here: where the blocks are
// (a code-point line scanner with the rules of the Java CodeStartParser / CodeEndParser — a line
// that is exactly ```scheme:Class.Name opens a block, a line that is exactly ``` closes it; no
// regular expressions), the Java colouring of the body (@codemirror/lang-java's Lezer parser,
// no eval / new Function, so it runs under the VSIX webview CSP), its brackets, and the hover.
import { javaLanguage } from '@codemirror/lang-java';
import { highlightTree, tagHighlighter, tags } from '@lezer/highlight';

/** The id of the help section about code blocks (#214 puts it in the help page / panel). */
export const HELP_JAVA_CODE_BLOCK = 'help-java-code-block';
/** Where a help link goes while the page has no element with its id (before #214): the README. */
export const helpFallbackUrl = (id) => `https://github.com/opaopa6969/tinyexpression/blob/master/playground/README.md#${id}`;
export const ADR_003_URL = 'https://github.com/opaopa6969/tinyexpression/blob/master/docs/decisions/ADR-003-java-codeblock-safety.md';
export const CODE_SERVER_URL = 'https://code.unlaxer.org/';
/** What the Rust evaluator adds to the error of a code-block class without a stub. */
export const MISSING_STUB_HINT = 'コードブロックのクラスは externals で値を指定してください';

const isDigit = (c) => c >= 0x30 && c <= 0x39;
const isIdentStart = (c) => (c >= 0x41 && c <= 0x5a) || (c >= 0x61 && c <= 0x7a) || c === 0x5f || c === 0x24;
const isIdentPart = (c) => isIdentStart(c) || isDigit(c);
const isLineBreak = (c) => c === 0x0a || c === 0x0d;

/** End of the identifier starting at `i` (before `to`), or -1. */
function identifierEnd(text, i, to) {
  if (i >= to || !isIdentStart(text.codePointAt(i))) return -1;
  while (i < to && isIdentPart(text.codePointAt(i))) i++;
  return i;
}

/** The opening fence on the line [from, to): {scheme, classFrom, classTo} or null. */
function openingFence(text, from, to) {
  if (!text.startsWith('```', from)) return null;
  const schemeEnd = identifierEnd(text, from + 3, to);
  if (schemeEnd < 0 || text.codePointAt(schemeEnd) !== 0x3a) return null;
  const classFrom = schemeEnd + 1;
  let end = identifierEnd(text, classFrom, to);
  while (end >= 0 && end < to && text.codePointAt(end) === 0x2e) end = identifierEnd(text, end + 1, to);
  if (end !== to) return null;
  return { scheme: text.slice(from + 3, schemeEnd), classFrom, classTo: end };
}

/**
 * The code blocks of a formula: [{from, to, openTo, scheme, className, classFrom, classTo,
 * bodyFrom, bodyTo, closeFrom, closed}] (JS string indices, what CodeMirror uses). `openTo` is
 * the end of the opening line, the body is [bodyFrom, bodyTo) (line breaks included), the
 * closing line is [closeFrom, to). An unclosed block runs to the end (closed: false, closeFrom
 * -1); the parser reports it.
 */
export function codeBlocksOf(text) {
  const blocks = [];
  let open = null;
  let from = 0;
  for (;;) {
    let to = from;
    while (to < text.length && !isLineBreak(text.charCodeAt(to))) to++;
    const next = to >= text.length ? -1 : (text.charCodeAt(to) === 0x0d && text.charCodeAt(to + 1) === 0x0a ? to + 2 : to + 1);
    if (open) {
      if (to - from === 3 && text.startsWith('```', from)) {
        blocks.push({ ...open, bodyTo: from, closeFrom: from, to, closed: true });
        open = null;
      }
    } else {
      const fence = openingFence(text, from, to);
      if (fence) {
        open = {
          from, openTo: to, scheme: fence.scheme, className: text.slice(fence.classFrom, fence.classTo),
          classFrom: fence.classFrom, classTo: fence.classTo, bodyFrom: next < 0 ? text.length : next,
        };
      }
    }
    if (next < 0) break;
    from = next;
  }
  if (open) blocks.push({ ...open, bodyTo: text.length, closeFrom: -1, to: text.length, closed: false });
  return blocks;
}

/** The block that contains `pos` (fence lines included), or null. */
export function codeBlockAt(blocks, pos) {
  return blocks.find((b) => pos >= b.from && pos <= b.to) ?? null;
}

/**
 * The text with every code block replaced by spaces (line breaks kept, so every index still
 * points at the same place): what the tinyexpression-side scans (`$variables`, imports,
 * external calls) read, so that nothing inside the Java code counts.
 */
export function blankCodeBlocks(text, blocks = codeBlocksOf(text)) {
  if (!blocks.length) return text;
  let out = '';
  let at = 0;
  for (const block of blocks) {
    out += text.slice(at, block.from);
    for (let i = block.from; i < block.to; i++) out += isLineBreak(text.charCodeAt(i)) ? text[i] : ' ';
    at = block.to;
  }
  return out + text.slice(at);
}

// ── Java colouring ──

/** Lezer highlight tags → the editors' token types (the te-t-* classes, as in the VS Code look). */
const javaHighlighter = tagHighlighter([
  { tag: tags.comment, class: 'comment' },
  { tag: tags.string, class: 'string' },
  { tag: tags.number, class: 'number' },
  { tag: [tags.bool, tags.null], class: 'constant' },
  { tag: tags.keyword, class: 'keyword' },
  { tag: [tags.typeName, tags.className, tags.namespace], class: 'type' },
  { tag: tags.annotation, class: 'method' },
  { tag: tags.operator, class: 'operator' },
]);

const BRACKETS = new Set(['(', ')', '[', ']', '{', '}']);
const TYPE_DECLARATIONS = new Set(['ClassDeclaration', 'InterfaceDeclaration', 'EnumDeclaration', 'RecordDeclaration', 'AnnotationTypeDeclaration']);

/**
 * Tokens of a Java body: [{from, to, type}] with the te-lexer token types (comment, string,
 * number, constant, keyword, type, function, method, operator) and `bracket` tokens carrying
 * `group` (brackets of a block pair among themselves, not with the formula's). Positions are
 * offset by `offset`.
 */
export function javaTokens(body, offset = 0, group = 'java') {
  const tree = javaLanguage.parser.parse(body);
  const tokens = [];
  highlightTree(tree, javaHighlighter, (from, to, classes) => {
    tokens.push({ from, to, type: classes.split(' ')[0] });
  });
  // Declared / called method names and declared type names (plain identifiers to the tags).
  const names = [];
  tree.iterate({
    enter(node) {
      if (node.name === 'MethodName') {
        names.push({ from: node.from, to: node.to, type: 'function' });
      } else if (node.name === 'Definition') {
        const parent = node.node.parent?.name;
        if (parent === 'MethodDeclaration') names.push({ from: node.from, to: node.to, type: 'function' });
        else if (TYPE_DECLARATIONS.has(parent)) names.push({ from: node.from, to: node.to, type: 'type' });
      } else if (BRACKETS.has(node.name) && node.to === node.from + 1 && body[node.from] === node.name) {
        names.push({ from: node.from, to: node.to, type: 'bracket', group });
      }
    },
  });
  const overlaps = (t) => names.some((n) => n.from < t.to && n.to > t.from);
  const out = [...tokens.filter((t) => !overlaps(t)), ...names];
  out.sort((a, b) => a.from - b.from || a.to - b.to);
  return out.map((t) => ({ ...t, from: t.from + offset, to: t.to + offset }));
}

/**
 * Tokens of a whole block (positions in the formula): the fences (`code-fence`, the class name
 * `type`) and the Java body.
 */
export function codeBlockTokens(text, block, index = 0) {
  const tokens = [{ from: block.from, to: block.classFrom, type: 'code-fence' }, { from: block.classFrom, to: block.classTo, type: 'type' }];
  if (block.bodyTo > block.bodyFrom) tokens.push(...javaTokens(text.slice(block.bodyFrom, block.bodyTo), block.bodyFrom, `java${index}`));
  if (block.closed) tokens.push({ from: block.closeFrom, to: block.to, type: 'code-fence' });
  return tokens;
}

// ── hover ──

/**
 * Hover content of a code block ({title, rows}, rendered by editor-support.js `renderHover`):
 * that the playground does not run it, what stands in for it (the external stubs of its class),
 * and where it does run (VSIX, code-server) with the help link. A row value is a string or
 * {links: [{text, href} | {text, help: id}]}.
 */
export function codeBlockHoverContent(block, externals = []) {
  const stubs = externals.filter((x) => String(x.class ?? '').trim() === block.className);
  const stubText = stubs.length
    ? stubs.map((x) => `${x.method || '（メソッド未入力）'}${String(x.arity ?? '') !== '' ? `/${x.arity}` : ''} → ${x.returnType === 'null' ? 'null' : `${x.returnType ?? 'float'} ${x.value ?? ''}`}`).join('、')
    : '未設定 — 値を入れないと「実行」は明示エラーになります（CalculationContext の「式から external を追加」）';
  return {
    title: `\`\`\`${block.scheme}:${block.className}`,
    rows: [
      ['', 'この Java クラスは playground では実行されません（ブラウザの wasm に JVM は無く、コンパイルもしません）。'],
      ['代用', `このクラスへの external 呼び出しは、CalculationContext の「external（仮の値）」の値で代用されます。`],
      ['仮の値', stubText],
      ['本物の実行', 'VS Code 拡張（VSIX）で、launch 設定に allowJavaCodeBlocks: true を明示したときだけ（既定 off）。'],
      ['', { links: [
        { text: 'ADR-003（安全性）', href: ADR_003_URL },
        { text: 'code-server（要ログイン）', href: CODE_SERVER_URL },
        { text: 'ヘルプ', help: HELP_JAVA_CODE_BLOCK },
      ] }],
      ['English', 'Not executed in the playground (no JVM in wasm): calls to this class are answered by the external stub values. Real execution: the VSIX with allowJavaCodeBlocks: true (ADR-003).'],
    ],
  };
}
