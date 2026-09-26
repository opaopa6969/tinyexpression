// Evaluation target switch (issue #221): "wasm（仮の値）" (the default, in the browser) or
// "サーバ（本物の Java）" — a host that embeds the playground (e.g. an internal server behind its
// own login) answers the same request JSON with org.unlaxer.tinyexpression.service.EvalContextService,
// which runs the real Java evaluator (code blocks only when the host's policy allows them).
//
// Pure (no DOM): shared by the browser UI and scripts/check.mjs, which drives it with a mocked
// fetch.
//
// - The URL is build-time `VITE_TE_SERVER_EVAL_URL` (main.js passes it in), else the same-origin
//   relative DEFAULT_SERVER_EVAL_URL. `off` / `none` / `false` disables the switch.
// - The switch appears only when the URL answers the probe as the Java service (`"evaluator":"java"`).
//   Where no host answers (GitHub Pages, `vite dev`) nothing changes.
// - Authentication and CSRF stay with the host: requests are same-origin with the host's cookies
//   (`credentials: 'same-origin'`), JSON bodies and the X-Requested-With header below, which a host
//   can require. The playground holds no token.

/** Relative to the playground page: `/s/playground/` → `/s/api/playground/eval`. */
export const DEFAULT_SERVER_EVAL_URL = '../api/playground/eval';

/** Sent with every request so that the host can tell playground calls from cross-site forms. */
export const REQUESTED_WITH = 'tinyexpression-playground';

/** The probe request: the smallest formula, answered by the Java service with `"evaluator":"java"`. */
export const PROBE_REQUEST = { operation: 'evalContext', formula: '1' };

const DISABLED = new Set(['', 'off', 'none', 'false', '0']);

/** The server URL of a build setting (undefined = default), or null when the switch is disabled. */
export function configuredServerUrl(setting) {
  if (setting === undefined || setting === null) return DEFAULT_SERVER_EVAL_URL;
  const value = String(setting).trim();
  return DISABLED.has(value.toLowerCase()) ? null : value;
}

async function post(fetchImpl, url, body, timeoutMs) {
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  const timer = controller && timeoutMs ? setTimeout(() => controller.abort(), timeoutMs) : null;
  try {
    const response = await fetchImpl(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-Requested-With': REQUESTED_WITH },
      body: JSON.stringify(body),
      signal: controller?.signal,
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const json = await response.json();
    if (!json || typeof json !== 'object' || typeof json.ok !== 'boolean') throw new Error('not an evaluation response');
    return json;
  } finally {
    if (timer) clearTimeout(timer);
  }
}

/** Whether `url` is the Java evaluation service (never throws). */
export async function probeServer(url, fetchImpl = globalThis.fetch, timeoutMs = 3000) {
  if (!url || typeof fetchImpl !== 'function') return false;
  try {
    const json = await post(fetchImpl, url, PROBE_REQUEST, timeoutMs);
    return json.evaluator === 'java';
  } catch {
    return false;
  }
}

/** A failure response of the playground itself (the server did not answer). */
export function serverFailure(error) {
  const message = String(error?.message ?? error);
  return {
    code: 70,
    result: { ok: false, stage: 'server', message: `サーバに接続できません: ${message}`, error: { kind: 'ServerUnavailable', message } },
  };
}

/**
 * The server runtime: the async counterparts of te-runtime.js `evalContext` / `evalTrace` /
 * `runContext`, returning `{ code, result }` like the wasm runtime (`code` is 0 for ok, else 5;
 * 70 when the server did not answer).
 */
export function createServerRuntime(url, fetchImpl = globalThis.fetch, timeoutMs = 30000) {
  const call = (operation) => async (request) => {
    try {
      const result = await post(fetchImpl, url, { ...request, operation }, timeoutMs);
      return { code: result.ok ? 0 : 5, result };
    } catch (error) {
      return serverFailure(error);
    }
  };
  return {
    url,
    evalContext: call('evalContext'),
    evalTrace: call('evalTrace'),
    runContext: call('runContext'),
  };
}

/**
 * The Java service reports a parse failure without the parser diagnostic the wasm build has;
 * the playground's position and TE code come from wasm `check` of the same formula.
 */
export function withWasmDiagnostic(result, te, formula) {
  if (result?.stage !== 'create' || result.diagnostic || !te) return result;
  const checked = te.check(formula)?.result;
  return checked?.diagnostic ? { ...result, diagnostic: checked.diagnostic } : result;
}

/**
 * The stub classes a server result did not use: with `codeBlocks.executed`, the code-block
 * classes ran for real, so their stubs were ignored.
 */
export function realClassesOf(result) {
  return result?.codeBlocks?.executed ? result.codeBlocks.classes ?? [] : [];
}

/** The label of the evaluation shown with a result. */
export function targetLabel(target) {
  return target === 'server' ? 'Java（本物）' : 'wasm';
}
