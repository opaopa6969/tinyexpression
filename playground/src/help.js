// The help panel (issue #214): a native <dialog> listing every entry of src/guide-content.js
// in order, so it reads the same content as the guided tour without following it step by step.
// Esc / the backdrop close a <dialog> natively, which also gives us a keyboard-usable modal
// for free. Section headings carry a stable `id="help-<entry.id>"` — issue #216 links hovers of
// the Java code-block UI to `#help-java-code-block`.
import { helpSections } from './guide-content.js';

function section(entry) {
  const box = document.createElement('section');
  box.className = 'help-section';
  box.id = `help-${entry.id}`;
  const h3 = document.createElement('h3');
  h3.textContent = entry.title.ja;
  const en = document.createElement('p');
  en.className = 'muted small';
  en.textContent = entry.title.en;
  box.append(h3, en);
  for (const text of entry.body.ja) {
    const p = document.createElement('p');
    p.textContent = text;
    box.append(p);
  }
  for (const text of entry.body.en) {
    const p = document.createElement('p');
    p.className = 'muted small';
    p.textContent = text;
    box.append(p);
  }
  if (entry.links?.length) {
    const links = document.createElement('p');
    links.className = 'small';
    entry.links.forEach((link, i) => {
      if (i > 0) links.append(' · ');
      const a = document.createElement('a');
      a.href = link.href; a.target = '_blank'; a.rel = 'noopener';
      a.setAttribute('data-external', '');
      a.textContent = link.label;
      links.append(a);
    });
    box.append(links);
  }
  return box;
}

export function createHelp({ onReplayTour } = {}) {
  const dialog = document.createElement('dialog');
  dialog.className = 'help-dialog';
  dialog.setAttribute('aria-label', 'tinyexpression playground help');

  const head = document.createElement('div');
  head.className = 'help-head';
  const h2 = document.createElement('h2');
  h2.textContent = 'ヘルプ';
  const replay = document.createElement('button');
  replay.className = 'link';
  replay.textContent = 'ツアーをもう一度見る';
  replay.addEventListener('click', () => { dialog.close(); onReplayTour?.(); });
  const close = document.createElement('button');
  close.className = 'icon';
  close.textContent = '×';
  close.setAttribute('aria-label', '閉じる');
  close.addEventListener('click', () => dialog.close());
  head.append(h2, replay, close);

  const body = document.createElement('div');
  body.className = 'help-body';
  body.append(...helpSections().map(section));

  dialog.append(head, body);
  document.body.append(dialog);

  return {
    open() { if (!dialog.open) dialog.showModal(); },
    destroy() { dialog.remove(); },
  };
}
