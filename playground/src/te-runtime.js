// Loads tinyexpression.wasm through the shared JS binding (rust/examples/wasm/tinyexpression.mjs)
// and keeps it usable after a trap: a wasm trap (e.g. the host stack running out) leaves the
// instance's stack pointer mid-call, so the instance is rebuilt from the same bytes.
import { loadTinyExpression } from '../../rust/examples/wasm/tinyexpression.mjs';

export async function createRuntime(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  const bytes = await response.arrayBuffer();
  let te = await loadTinyExpression(bytes);
  let rebuilding = null;

  const guard = (name) => (...args) => {
    try {
      return te[name](...args);
    } catch (error) {
      rebuilding = loadTinyExpression(bytes).then((fresh) => { te = fresh; rebuilding = null; });
      return {
        code: 70,
        result: { ok: false, stage: 'internal', message: `wasm trap: ${error.message}`, error: { kind: 'InternalError', message: String(error.message) } },
      };
    }
  };

  return {
    check: guard('check'),
    parse: guard('parse'),
    load: guard('load'),
    evalContext: guard('evalContext'),
    evalTrace: guard('evalTrace'),
    runContext: guard('runContext'),
    version: () => te.version(),
    ready: () => rebuilding ?? Promise.resolve(),
    size: bytes.byteLength,
  };
}
