// Colouring, depth-coloured (rainbow) brackets, unmatched brackets and the bracket pair at the
// cursor, for both editors (issue #212). `analyze(text)` returns {tokens, brackets} (te-lexer.js
// for the formula editor, formula-info-syntax.js for the FormulaInfo editor); this plugin only
// turns them into CodeMirror mark decorations (CSS classes in style.css, readable in the light
// and the dark theme).
import { RangeSetBuilder, StateEffect } from '@codemirror/state';
import { Decoration, ViewPlugin } from '@codemirror/view';
import { bracketMatching } from '@codemirror/language';
import { bracketAtCursor, BRACKET_COLOURS } from './te-lexer.js';

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

/**
 * @param analyze (text) => {tokens (sorted, non-overlapping), brackets}
 * @returns the extension, and `refresh(view)` to recolour after the analysis inputs changed
 *          (e.g. the catalog's keywords)
 */
export function syntaxView(analyze) {
  const plugin = ViewPlugin.fromClass(class {
    constructor(view) {
      this.text = view.state.doc.toString();
      this.analysis = analyze(this.text);
      this.decorations = build(view, this.analysis);
    }

    update(update) {
      const forced = update.transactions.some((tr) => tr.effects.some((e) => e.is(refreshEffect)));
      if (update.docChanged || forced) {
        this.text = update.state.doc.toString();
        this.analysis = analyze(this.text);
      }
      if (update.docChanged || update.selectionSet || forced) this.decorations = build(update.view, this.analysis);
    }
  }, { decorations: (v) => v.decorations });
  // The built-in bracket matching scans raw characters (brackets in strings, across
  // FormulaInfo lines); these editors mark the pair from their own scan instead.
  return [plugin, bracketMatching({ renderMatch: () => [] })];
}


/** Re-runs the analysis (after the catalog changed). */
export function refreshSyntax(view) {
  view.dispatch({ effects: refreshEffect.of(null) });
}
