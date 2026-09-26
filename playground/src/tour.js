// The guided tour (issue #214): highlights one playground element at a time with a bubble
// (Japanese text, English underneath — see src/guide-content.js). No CDN, nothing beyond plain
// DOM APIs (no eval / new Function), so it also runs inside the VSIX webview (CSP).
//
// createTour({ hooks }) returns { start, maybeAutoPrompt, destroy }. `hooks` lets the tour
// reach into main.js without importing it:
//   snapshotState()      -> opaque value, later passed back to restoreState
//   restoreState(value)
//   loadSampleForTour()  -> loads the demo formula/context/FormulaInfo the steps talk about
//   actions: { [name]: () => void }  -- see guide-content.js `autoAction`
import { tourSteps } from './guide-content.js';

const SEEN_KEY = 'tinyexpression-playground-tour-seen-v1';

function paragraphs(lang, texts) {
  return texts.map((text) => {
    const p = document.createElement('p');
    p.className = lang === 'en' ? 'muted small tour-en' : 'tour-ja';
    p.textContent = text;
    return p;
  });
}

export function createTour({ hooks = {} } = {}) {
  const steps = tourSteps();
  let index = -1;
  let overlay = null;
  let highlight = null;
  let bubble = null;
  let snapshot = null;
  let resizeHandler = null;

  function targetOf(step) {
    try {
      return document.querySelector(step.selector);
    } catch {
      return null;
    }
  }

  function buildDom() {
    overlay = document.createElement('div');
    overlay.className = 'tour-overlay';
    highlight = document.createElement('div');
    highlight.className = 'tour-highlight';
    bubble = document.createElement('div');
    bubble.className = 'tour-bubble';
    bubble.setAttribute('role', 'dialog');
    bubble.setAttribute('aria-modal', 'false');
    bubble.setAttribute('aria-label', 'tinyexpression playground tour');
    bubble.tabIndex = -1;
    document.body.append(overlay, highlight, bubble);
    resizeHandler = () => place(steps[index]);
    window.addEventListener('resize', resizeHandler);
    window.addEventListener('scroll', resizeHandler, true);
    document.addEventListener('keydown', onKey);
  }

  function teardownDom() {
    overlay?.remove();
    highlight?.remove();
    bubble?.remove();
    overlay = highlight = bubble = null;
    if (resizeHandler) {
      window.removeEventListener('resize', resizeHandler);
      window.removeEventListener('scroll', resizeHandler, true);
      resizeHandler = null;
    }
    document.removeEventListener('keydown', onKey);
  }

  function onKey(event) {
    if (event.key === 'Escape') { event.preventDefault(); stop(); }
    else if (event.key === 'ArrowRight' || event.key === 'Enter') { event.preventDefault(); next(); }
    else if (event.key === 'ArrowLeft') { event.preventDefault(); back(); }
  }

  // Called after the bubble's content for the step is already in the DOM (render() appends it
  // first), so `bubble.getBoundingClientRect().height` is this step's real height — a step with
  // more text must not push its own "next" button past the bottom of the viewport (that broke a
  // Playwright smoke test: the button existed but was below `window.innerHeight`).
  function place(step) {
    const target = targetOf(step);
    if (!target) return;
    // `behavior: 'instant'` matters here too: 'smooth' animates, so a getBoundingClientRect()
    // taken right after would read the pre-scroll position and place the highlight off-screen.
    target.scrollIntoView({ block: 'center', behavior: 'instant' });
    const rect = target.getBoundingClientRect();
    const pad = 6;
    Object.assign(highlight.style, {
      left: `${rect.left - pad}px`, top: `${rect.top - pad}px`,
      width: `${rect.width + pad * 2}px`, height: `${rect.height + pad * 2}px`,
    });
    // `.tour-bubble` has `max-height: 70vh`, so this is already clamped to the viewport.
    const height = bubble.getBoundingClientRect().height;
    const below = rect.bottom + pad * 2;
    const above = rect.top - pad * 2 - height;
    const top = below + height <= window.innerHeight ? below : Math.max(8, above);
    Object.assign(bubble.style, {
      left: `${Math.max(8, Math.min(rect.left, window.innerWidth - 380))}px`,
      top: `${Math.max(8, Math.min(top, window.innerHeight - height - 8))}px`,
    });
  }

  function render(step) {
    hooks.actions?.[step.autoAction]?.();
    bubble.replaceChildren();
    const head = document.createElement('div');
    head.className = 'tour-head';
    const h3 = document.createElement('h3');
    h3.textContent = step.title.ja;
    const progress = document.createElement('span');
    progress.className = 'tour-progress';
    progress.textContent = `${index + 1} / ${steps.length}`;
    head.append(h3, progress);
    const body = document.createElement('div');
    body.className = 'tour-body';
    body.append(...paragraphs('ja', step.body.ja), ...paragraphs('en', step.body.en));
    if (step.links?.length) {
      const links = document.createElement('p');
      links.className = 'tour-links small';
      step.links.forEach((link, i) => {
        if (i > 0) links.append(' · ');
        const a = document.createElement('a');
        a.href = link.href; a.target = '_blank'; a.rel = 'noopener';
        a.setAttribute('data-external', '');
        a.textContent = link.label;
        links.append(a);
      });
      body.append(links);
    }
    const nav = document.createElement('div');
    nav.className = 'tour-nav';
    const back = document.createElement('button');
    back.textContent = '← 戻る'; back.disabled = index === 0;
    back.addEventListener('click', () => goBack());
    const skip = document.createElement('button');
    skip.className = 'link'; skip.textContent = 'スキップ';
    skip.addEventListener('click', () => stop());
    const forward = document.createElement('button');
    forward.textContent = index === steps.length - 1 ? '終わる' : '次へ →';
    forward.addEventListener('click', () => goNext());
    const close = document.createElement('button');
    close.className = 'icon tour-close'; close.textContent = '×';
    close.title = 'Esc'; close.setAttribute('aria-label', '閉じる');
    close.addEventListener('click', () => stop());
    nav.append(back, skip, forward);
    bubble.append(close, head, body, nav);
    place(step);
    bubble.focus();
  }

  function goNext() { advance(1); }
  function goBack() { advance(-1); }

  function advance(direction) {
    let i = index;
    do {
      i += direction;
      if (i < 0 || i >= steps.length) { stop(); return; }
    } while (!targetOf(steps[i]));
    index = i;
    render(steps[index]);
  }

  function start() {
    if (index >= 0) return;
    snapshot = hooks.snapshotState?.() ?? null;
    hooks.loadSampleForTour?.();
    buildDom();
    index = -1;
    advance(1);
  }

  function stop() {
    if (index < 0) return;
    index = -1;
    teardownDom();
    markSeen();
    if (snapshot != null) hooks.restoreState?.(snapshot);
    snapshot = null;
  }

  function markSeen() {
    try { localStorage.setItem(SEEN_KEY, '1'); } catch { /* storage unavailable */ }
  }

  /** Shows a one-time "want a tour?" prompt on first visit (localStorage, issue #214). */
  function maybeAutoPrompt() {
    let seen = true;
    try { seen = localStorage.getItem(SEEN_KEY) === '1'; } catch { /* storage unavailable */ }
    if (seen) return;
    const banner = document.createElement('div');
    banner.className = 'tour-banner';
    banner.setAttribute('role', 'note');
    const text = document.createElement('span');
    text.textContent = '初めての方へ: 画面の使い方をガイドツアーで見られます。';
    const go = document.createElement('button');
    go.textContent = 'ツアーを見る';
    go.addEventListener('click', () => { banner.remove(); markSeen(); start(); });
    const dismiss = document.createElement('button');
    dismiss.className = 'link'; dismiss.textContent = '閉じる';
    dismiss.addEventListener('click', () => { banner.remove(); markSeen(); });
    banner.append(text, go, dismiss);
    document.body.append(banner);
  }

  function destroy() {
    stop();
  }

  return { start, maybeAutoPrompt, destroy };
}
