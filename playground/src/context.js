// CalculationContext panel state -> wasm request (issue #201). Pure: shared by the browser UI
// and scripts/parity-smoke.mjs, so the parity smoke exercises exactly the playground's mapping.
//
// state = {
//   resultType, numberType, angle, seed,
//   nowHour: '' | '13', nowDayOfWeek: '' | 'MONDAY' ... 'SUNDAY',
//   variables: [{ name, type: 'float'|'double'|'int'|'long'|'boolean'|'string', value, map? }],
//   externals: [{ class, method, arity: '' | '2', registered, returnType, value }],
// }

import { blankCodeBlocks } from './code-block.js';

export const VARIABLE_TYPES = ['float', 'double', 'int', 'long', 'short', 'byte', 'boolean', 'string'];
export const RESULT_TYPES = ['float', 'double', 'int', 'long', 'short', 'byte', 'boolean', 'string', 'object'];
export const NUMBER_TYPES = ['float', 'double', 'int', 'long', 'short', 'byte'];
export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export const RETURN_TYPES = ['float', 'double', 'int', 'long', 'boolean', 'string', 'null'];

export function emptyState() {
  return {
    resultType: 'float',
    numberType: 'float',
    angle: 'degree',
    seed: 1,
    nowHour: '',
    nowDayOfWeek: '',
    variables: [],
    externals: [],
  };
}

function variableOf(v) {
  const name = String(v.name ?? '').trim().replace(/^\$/, '');
  const out = { name, type: v.type || 'float', value: String(v.value ?? '') };
  if (v.map) out.map = v.map;
  return out;
}

/** The JSON request `te_eval_context` / `te_formula_info_context` read (see rust/README.md). */
export function toRequest(state, source) {
  const variables = state.variables
    .filter((v) => String(v.name ?? '').trim() !== '')
    .map(variableOf);
  if (String(state.nowHour ?? '').trim() !== '') {
    variables.push({ name: 'nowHour', type: 'float', value: String(state.nowHour).trim() });
  }
  if (state.nowDayOfWeek) {
    const day = DAYS.indexOf(state.nowDayOfWeek) + 1;
    if (day > 0) variables.push({ name: 'nowDayOfWeek', type: 'float', value: String(day) });
  }
  const externals = state.externals
    .filter((e) => String(e.class ?? '').trim() !== '')
    .map((e) => ({
      class: e.class.trim(),
      method: String(e.method ?? '').trim(),
      arity: String(e.arity ?? '').trim() === '' ? null : Number(e.arity),
      registered: e.registered !== false,
      result: e.returnType === 'null'
        ? { type: 'null' }
        : { type: e.returnType || 'float', value: String(e.value ?? '') },
    }));
  return {
    ...source,
    resultType: state.resultType,
    numberType: state.numberType,
    angle: state.angle,
    seed: Number(state.seed ?? 1),
    variables,
    externals,
  };
}

/** `$name` references in a formula (for "式から変数を追加"); ```java blocks do not count (#216). */
export function referencedVariables(formula) {
  const names = [];
  for (const m of blankCodeBlocks(formula).matchAll(/\$([A-Za-z_][A-Za-z0-9_]*)/g)) {
    if (!names.includes(m[1])) names.push(m[1]);
  }
  return names;
}

/** Variables declared in the formula itself (`var $x ...`), which need no context value. */
export function declaredVariables(formula) {
  const names = new Set();
  for (const m of blankCodeBlocks(formula).matchAll(/\b(?:var|variable)\s+\$([A-Za-z_][A-Za-z0-9_]*)/g)) names.add(m[1]);
  return names;
}

/** Converts a code-point offset (what the Rust diagnostics report) to a JS string index. */
export function codePointToIndex(text, codePoints) {
  let index = 0;
  let seen = 0;
  while (index < text.length && seen < codePoints) {
    const cp = text.codePointAt(index);
    index += cp > 0xffff ? 2 : 1;
    seen++;
  }
  return index;
}
