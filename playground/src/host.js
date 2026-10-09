// The VS Code webview host (issue #201, stage 5). The VSIX opens this same build in a webview
// ("TinyExpression: Open playground"); there `acquireVsCodeApi` exists and the page talks to
// the extension by messages:
//   extension → page  {type: 'init', formula, catalogOverride, catalogLabel, documentName}
//                     {type: 'saved', message}  (after a catalog export was written)
//   page → extension  {type: 'ready'}
//                     {type: 'saveCatalog', kind: 'override' | 'full', text}
//                     {type: 'openExternal', url}   {type: 'copy', text}
// The page state (formula, CalculationContext incl. the external stubs) is kept with
// vscode.setState, so it survives reopening the panel; `init` does not reset it.
// On GitHub Pages none of this exists and the page runs standalone.

const vscode = typeof acquireVsCodeApi === 'function' ? acquireVsCodeApi() : null; // eslint-disable-line no-undef

export const inVsCode = vscode != null;

export function postToHost(message) {
  vscode?.postMessage(message);
}

/**
 * The page state the webview keeps across reopening the panel (vscode.getState / setState,
 * issue #216: the CalculationContext with its external stubs), or null on the web.
 */
export function hostState() {
  return vscode?.getState() ?? null;
}

export function saveHostState(value) {
  vscode?.setState(value);
}

/** Registers the handler of extension messages and tells the extension the page is ready. */
export function connectHost(handlers) {
  if (!vscode) return;
  window.addEventListener('message', (event) => {
    const message = event.data;
    if (message && typeof message.type === 'string') handlers[message.type]?.(message);
  });
  vscode.postMessage({ type: 'ready' });
}

/** Opens a URL: a new tab on the web, the system browser from the webview. */
export function openExternal(url) {
  if (vscode) postToHost({ type: 'openExternal', url });
  else window.open(url, '_blank', 'noopener');
}

/** Copies text; the webview asks the extension (clipboard access varies there). */
export async function copyText(text) {
  if (vscode) {
    postToHost({ type: 'copy', text });
    return true;
  }
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

/** Offers text as a file: a download on the web, a save through the extension in VS Code. */
export function saveFile(name, text, { kind } = {}) {
  if (vscode && kind) {
    postToHost({ type: 'saveCatalog', kind, name, text });
    return;
  }
  const url = URL.createObjectURL(new Blob([text], { type: name.endsWith('.json') ? 'application/json' : 'text/plain' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
