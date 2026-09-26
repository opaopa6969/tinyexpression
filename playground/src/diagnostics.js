// Diagnostics from `te_check` with TE codes and texts from the language catalog (issue #201).
// Pure (no DOM): the editor's linter and scripts/check.mjs both call it.
import { resolveErrorCode, lookupVariable, ja } from '../../catalog/scripts/catalog-lib.mjs';
import { codePointToIndex, declaredVariables } from './context.js';
import { blankCodeBlocks } from './code-block.js';

const ALWAYS_KNOWN = new Set(['nowHour', 'nowDayOfWeek']);

function lineEnd(text, index) {
  const end = text.indexOf('\n', index);
  return end < 0 ? text.length : end;
}

/** Catalog entry of a TE/FI code, falling back to the default code. */
export function errorEntry(catalog, code) {
  return catalog.errorCodes.find((e) => e.code === code)
    ?? catalog.errorCodes.find((e) => e.code === catalog.defaultErrorCode);
}

/** `[TE006] 文末のセミコロンが必要です。 修正例: 文の末尾に ; を追加` (the LSP's wording). */
export function fullMessage(entry) {
  return `[${entry.code}] ${ja(entry.message)} 修正例: ${ja(entry.fix)}`;
}

/** A parse diagnostic (Rust JSON) as a located, coded diagnostic. */
export function parseDiagnostic(catalog, formula, diagnostic) {
  const from = Math.min(codePointToIndex(formula, diagnostic.offset), formula.length);
  const snippet = formula.slice(from, from + 20).trim();
  const leading = formula.slice(0, from);
  const code = resolveErrorCode(catalog, { expected: diagnostic.expected ?? [], snippet, leading });
  const entry = errorEntry(catalog, code);
  let to = lineEnd(formula, from);
  if (to <= from) to = Math.min(from + 1, formula.length);
  const expected = (diagnostic.expected ?? []).filter((e) => e !== 'EOF').slice(0, 12);
  return {
    from, to, severity: 'error', code: entry.code,
    message: fullMessage(entry),
    fix: ja(entry.fix),
    detail: expected.length ? `期待される入力: ${expected.join(' ')}` : '',
  };
}

/**
 * All diagnostics of a formula: the parser's (TE code via the catalog's diagnosticRules), type
 * and mapping errors, and TE022 for `$variables` that neither the context panel, the formula's
 * own declarations nor the catalog know.
 */
export function diagnose(te, catalog, formula, contextNames = []) {
  const out = [];
  if (formula.trim() === '') return out;
  const { result } = te.check(formula);
  if (!result.ok) {
    if (result.stage === 'parse') {
      out.push(parseDiagnostic(catalog, formula, result.diagnostic));
    } else if (result.stage === 'type' && Array.isArray(result.span)) {
      const from = codePointToIndex(formula, result.span[0]);
      const to = Math.max(codePointToIndex(formula, result.span[1]), from + 1);
      out.push({ from, to: Math.min(to, formula.length), severity: 'error', code: 'type', message: `型エラー: ${result.message}`, fix: '', detail: '' });
    } else {
      out.push({ from: 0, to: formula.length, severity: 'error', code: result.stage, message: `${result.stage}: ${result.message ?? ''}`, fix: '', detail: '' });
    }
  }
  const known = new Set([...contextNames, ...declaredVariables(formula), ...ALWAYS_KNOWN]);
  const te022 = errorEntry(catalog, 'TE022');
  const template = te022.templates?.UNKNOWN_VARIABLE ? ja(te022.templates.UNKNOWN_VARIABLE) : '{fullMessage} ({symbol})';
  // Issue #216: a ```java block is Java, not tinyexpression (no TE022 for `$` in its strings).
  for (const m of blankCodeBlocks(formula).matchAll(/\$([A-Za-z_][A-Za-z0-9_]*)/g)) {
    const name = m[1];
    if (known.has(name) || lookupVariable(catalog, name)) continue;
    out.push({
      from: m.index, to: m.index + m[0].length, severity: 'warning', code: 'TE022',
      message: template.replace('{fullMessage}', fullMessage(te022)).replace('{symbol}', `$${name}`),
      fix: `${ja(te022.fix)}（CalculationContext パネルで変数を追加）`,
      detail: '',
    });
  }
  return out;
}

/** Catalog text for an evaluation failure (`stage` create/apply, `error.kind` Java exception). */
export function describeFailure(catalog, formula, result) {
  if (result.stage === 'create' && result.diagnostic) {
    const d = parseDiagnostic(catalog, formula, result.diagnostic);
    return { code: d.code, title: d.message, fix: d.fix, from: d.from, to: d.to };
  }
  const kind = result.error?.kind ?? result.stage;
  const runtime = (catalog.runtimeErrors ?? []).find((r) => r.kind === kind);
  return {
    code: kind,
    title: runtime ? ja(runtime.description) : (result.message ?? kind),
    fix: runtime?.fix ? ja(runtime.fix) : '',
  };
}
