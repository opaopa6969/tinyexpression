// Colouring, depth-coloured (rainbow) brackets, unmatched brackets and the bracket pair at the
// cursor, for both editors (issue #212). `analyze(text)` returns {tokens, brackets} (te-lexer.js
// for the formula editor, formula-info-syntax.js for the FormulaInfo editor); this plugin only
// turns them into CodeMirror mark decorations (CSS classes in style.css, readable in the light
// and the dark theme).
//
// Issue #216: the lines of a ```java code block (analysis.codeBlocks) get a background
// (`te-code-line`), and the blocks fold (fold gutter / Ctrl+Shift+[) in both editors.
import { RangeSetBuilder, StateEffect } from '@codemirror/state';
import { Decoration, EditorView, ViewPlugin } from '@codemirror/view';
import { bracketMatching, foldService } from '@codemirror/language';
import { bracketAtCursor, BRACKET_COLOURS } from './te-lexer.js';
import { codeBlocksOf } from './code-block.js';

const refreshEffect = StateEffect.define();

const marks = new Map();
function mark(className) {
  let m = marks.get(className);
  if (!m) {
    m = Decoration.mark({ class: className });
    marks.set(className, m);
  }
  return m;
}

/** CSS class of a bracket: its depth colour, `te-bracket-unmatched`, `te-bracket-match`. */
export function bracketClass(bracket, atCursor) {
  const classes = [bracket.partner < 0 ? 'te-bracket-unmatched' : `te-bracket-d${bracket.depth % BRACKET_COLOURS}`];
  if (atCursor) classes.push(bracket.partner < 0 ? 'te-bracket-match-none' : 'te-bracket-match');
  return classes.join(' ');
}

function build(view, analysis) {
  const builder = new RangeSetBuilder();
  const pos = view.state.selection.main.head;
  const cursor = bracketAtCursor(analysis.brackets, pos);
  const atCursor = new Set();
  if (cursor) {
    atCursor.add(cursor.bracket.from);
    if (cursor.partner) atCursor.add(cursor.partner.from);
  }
  const byPos = new Map(analysis.brackets.map((b) => [b.from, b]));
  const length = view.state.doc.length;
  for (const token of analysis.tokens) {
    const from = Math.min(token.from, length);
    const to = Math.min(token.to, length);
    if (to <= from) continue;
    let className = `te-t-${token.type}`;
    if (token.type === 'bracket') {
      const bracket = byPos.get(token.from);
      if (bracket) className = bracketClass(bracket, atCursor.has(token.from));
    }
    builder.add(from, to, mark(className));
  }
  return builder.finish();
}

/** Line decorations of the code blocks' lines (fences included). */
function codeLines(view, analysis) {
  const builder = new RangeSetBuilder();
  const doc = view.state.doc;
  for (const block of analysis.codeBlocks ?? []) {
    const first = doc.lineAt(Math.min(block.from, doc.length)).number;
    const last = doc.lineAt(Math.min(block.to, doc.length)).number;
    for (let n = first; n <= last; n++) {
      const classes = ['te-code-line'];
      if (n === first) classes.push('te-code-first');
      if (n === last) classes.push('te-code-last');
      builder.add(doc.line(n).from, doc.line(n).from, Decoration.line({ class: classes.join(' ') }));
    }
  }
  return builder.finish();
}

const blocksOfDoc = new WeakMap();
/** Folds a code block from the end of its opening fence line to the end of the block. */
export const codeBlockFolding = foldService.of((state, lineStart, lineEnd) => {
  let blocks = blocksOfDoc.get(state.doc);
  if (!blocks) {
    blocks = codeBlocksOf(state.doc.toString());
    blocksOfDoc.set(state.doc, blocks);
  }
  const block = blocks.find((b) => b.from === lineStart);
  if (!block || block.to <= lineEnd) return null;
  return { from: lineEnd, to: block.to };
});

/**
 * @param analyze (text) => {tokens (sorted, non-overlapping), brackets, codeBlocks?}
 * @returns the extension, and `refresh(view)` to recolour after the analysis inputs changed
 *          (e.g. the catalog's keywords)
 */
export function syntaxView(analyze) {
  const plugin = ViewPlugin.fromClass(class {
    constructor(view) {
      this.text = view.state.doc.toString();
      this.analysis = analyze(this.text);
      this.decorations = build(view, this.analysis);
      this.lines = codeLines(view, this.analysis);
    }

    update(update) {
      const forced = update.transactions.some((tr) => tr.effects.some((e) => e.is(refreshEffect)));
      if (update.docChanged || forced) {
        this.text = update.state.doc.toString();
        this.analysis = analyze(this.text);
      }
      if (update.docChanged || update.selectionSet || forced) this.decorations = build(update.view, this.analysis);
      if (update.docChanged || forced) this.lines = codeLines(update.view, this.analysis);
    }
  }, { decorations: (v) => v.decorations });
  const lines = EditorView.decorations.of((view) => view.plugin(plugin)?.lines ?? Decoration.none);
  // The built-in bracket matching scans raw characters (brackets in strings, across
  // FormulaInfo lines); these editors mark the pair from their own scan instead.
  return [plugin, lines, codeBlockFolding, bracketMatching({ renderMatch: () => [] })];
}


/** Re-runs the analysis (after the catalog changed). */
export function refreshSyntax(view) {
  view.dispatch({ effects: refreshEffect.of(null) });
}
