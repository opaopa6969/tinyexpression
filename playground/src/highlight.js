// Span highlighting in the editor for the Trace panel (issue #201, stage 3): the selected /
// current step (`te-trace-mark`) and the failing step (`te-trace-error`).
import { StateEffect, StateField } from '@codemirror/state';
import { Decoration, EditorView } from '@codemirror/view';

const setMarks = StateEffect.define();

const marksField = StateField.define({
  create: () => Decoration.none,
  update(marks, tr) {
    marks = marks.map(tr.changes);
    for (const effect of tr.effects) if (effect.is(setMarks)) marks = effect.value;
    // Stale once the text changes: the trace belongs to the evaluated text.
    return tr.docChanged ? Decoration.none : marks;
  },
  provide: (field) => EditorView.decorations.from(field),
});

export const highlightExtension = [marksField];

/** Replaces the marks: [{from, to, kind: 'current' | 'error'}]. Empty ranges are widened by one. */
export function highlight(view, ranges, { scroll = true } = {}) {
  const length = view.state.doc.length;
  const decorations = [];
  for (const r of ranges) {
    let from = Math.max(0, Math.min(r.from, length));
    let to = Math.max(from, Math.min(r.to, length));
    if (to === from) {
      if (to < length) to++;
      else if (from > 0) from--;
      else continue;
    }
    decorations.push(Decoration.mark({ class: r.kind === 'error' ? 'te-trace-error' : 'te-trace-mark' }).range(from, to));
  }
  decorations.sort((a, b) => a.from - b.from || a.to - b.to);
  const effects = [setMarks.of(Decoration.set(decorations, true))];
  const focus = ranges.find((r) => r.kind !== 'error') ?? ranges[0];
  if (scroll && focus) effects.push(EditorView.scrollIntoView(Math.min(focus.from, length), { y: 'nearest' }));
  view.dispatch({ effects });
}
