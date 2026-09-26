// Diagnostics of the FormulaInfo editor (issue #212). Pure (no DOM): the editor's linter and
// scripts/check.mjs both call it.
//
// 1. The Rust loader (te_formula_info `load`, the one the 読み込み / 実行 buttons run): its
//    first error, at the `span` the loader returns (code points of the document →
//    JS indices with trace.js `codePointIndexMap`). A point span covers the rest of its line.
// 2. Every block's `formula:` through the formula editor's `diagnose` (te_check, TE codes,
//    texts and hints from the catalog `diagnosticRules`), mapped back to the document.
// 3. Unknown keys (warning), every unknown `dependsOn` name (FI001), an end mark line with
//    trailing characters that does not close its block.
import { ja } from '../../catalog/scripts/catalog-lib.mjs';
import { diagnose, errorEntry } from './diagnostics.js';
import { codePointIndexMap } from './trace.js';
import { normalizedValue, valueToDoc, valuesOf, END_MARK } from './formula-info-syntax.js';

/**
 * The FormulaInfo keys: the catalog settings of scope `formulaInfo` (the source of truth for
 * their descriptions, types and enum values; edits in the Catalog panel apply at once). A name
 * with `<…>` (`byteCode_<className>`) is a key prefix.
 */
export function formulaInfoKeys(catalog) {
  const keys = new Map();
  for (const s of catalog.settings ?? []) if (s.scope === 'formulaInfo' && !keys.has(s.name)) keys.set(s.name, s);
  return keys;
}

const prefixOf = (name) => {
  const at = name.indexOf('<');
  return at > 0 ? name.slice(0, at) : null;
};

/** The catalog setting of a key (exact name, else a `prefix<…>` pattern), or null. */
export function settingOf(keys, key) {
  const exact = keys.get(key);
  if (exact) return exact;
  for (const [name, setting] of keys) {
    const prefix = prefixOf(name);
    if (prefix && key.startsWith(prefix) && key.length > prefix.length) return setting;
  }
  return null;
}

export function isKnownKey(keys, key) {
  return settingOf(keys, key) != null;
}

/** Whether a setting name is a completable key (not a `prefix<…>` pattern). */
export function isLiteralKey(name) {
  return prefixOf(name) == null;
}

/** Japanese labels of the Rust `LoadError` kinds. */
const LOAD_KIND = {
  syntax: 'FormulaInfo の構文として読めません',
  empty_value_at_end: '入力の最後の `key:` に値がありません',
  unknown_execution_backend: '未知の executionBackend です',
  unknown_type: '未知の型です',
  odd_hex_length: 'byteCode の hex の長さが奇数です',
  invalid_hex_digit: 'byteCode に hex でない文字があります',
  missing_formula: 'formula がありません（空の formula を含む）',
  unsupported_type: 'この実行系（Rust / wasm）が対応していない型です',
  formula: '式を構築できません',
  unknown_depends_on: 'dependsOn が文書内のどの calculatorName とも一致しません',
  duplicate_calculator_name: 'calculatorName が重複しています',
};

const isLowSurrogate = (u) => u >= 0xdc00 && u <= 0xdfff;
const onlyLineBreaks = (t) => {
  for (const ch of t) if (ch !== '\n' && ch !== '\r') return false;
  return true;
};

const lineEndOf = (text, index) => {
  let i = index;
  while (i < text.length && text[i] !== '\n' && text[i] !== '\r') i++;
  return i;
};
const lineStartOf = (text, index) => {
  let i = index;
  while (i > 0 && text[i - 1] !== '\n' && text[i - 1] !== '\r') i--;
  return i;
};

/** A document range for a (code point) span; a point covers the rest of its line. */
export function spanToRange(text, span, map = codePointIndexMap(text)) {
  const at = (cp) => map[Math.min(Math.max(cp, 0), map.length - 1)];
  let from = at(span[0]);
  let to = at(span[1]);
  if (to <= from) {
    to = lineEndOf(text, from);
    if (to <= from) from = lineStartOf(text, from); // at the end of a line: the whole line
    if (to <= from) to = Math.min(from + 1, text.length);
  }
  return { from, to };
}

/** The Rust loader's error as a located diagnostic, or null when the document loads. */
export function loaderDiagnostic(catalog, text, result) {
  if (result.ok || result.stage !== 'load' || !result.error) return null;
  const error = result.error;
  const span = Array.isArray(result.span) ? result.span : [0, 0];
  const { from, to } = spanToRange(text, span);
  const label = LOAD_KIND[error.kind] ?? error.kind;
  let code = error.javaException ?? error.kind;
  let fix = '';
  if (error.kind === 'unknown_depends_on') {
    const e = errorEntry(catalog, 'FI001');
    code = e.code;
    fix = ja(e.fix);
  } else if (error.kind === 'unknown_execution_backend') {
    const e = errorEntry(catalog, 'FI002');
    code = e.code;
    fix = ja(e.fix);
  }
  const expected = (error.diagnostic?.expected ?? []).filter((x) => x !== 'EOF').slice(0, 12);
  return {
    from, to, severity: 'error', code, kind: error.kind, origin: 'loader',
    message: `[${code}] ${label}（${error.javaException}）: ${error.message}`,
    fix,
    detail: expected.length ? `期待される入力: ${expected.join(' ')}` : '',
  };
}

/**
 * Every diagnostic of a FormulaInfo document.
 * @param te the wasm runtime (`load`, `check`)
 * @param contextNames the CalculationContext variable names (for TE022)
 * @param analysis `analyzeFormulaInfo(text, …)` of the same text
 * @param keys `formulaInfoKeys(catalog)`
 */
export function diagnoseFormulaInfo(te, catalog, text, contextNames, analysis, keys = formulaInfoKeys(catalog)) {
  const out = [];
  if (text.trim() === '') return out;
  const { doc } = analysis;

  // 2. each formula, as the loader normalises it
  const formulaRanges = [];
  for (const block of doc.blocks) {
    for (const entry of block.entries) {
      if (entry.key !== 'formula') continue;
      const value = normalizedValue(text, entry);
      formulaRanges.push({ from: entry.valueFrom, to: entry.valueTo });
      if (!value || value.text.trim() === '') continue;
      for (const d of diagnose(te, catalog, value.text, contextNames)) {
        let from = valueToDoc(value, d.from);
        let to = Math.min(Math.max(valueToDoc(value, d.to), from + 1), text.length);
        // At the end of a line (an unclosed bracket at the end of the formula): underline the
        // last character instead of the invisible line break.
        if (onlyLineBreaks(text.slice(from, to)) && from > entry.valueFrom) {
          to = from;
          from -= from >= 2 && isLowSurrogate(text.charCodeAt(from - 1)) ? 2 : 1;
        }
        out.push({ ...d, from, to, origin: 'formula', block: block.index });
      }
    }
  }

  // 1. the loader
  const loader = loaderDiagnostic(catalog, text, te.load(text).result);
  if (loader) {
    // The formula's own parse / type error is already there with its TE code.
    const covered = loader.kind === 'formula'
      && out.some((d) => d.severity === 'error' && formulaRanges.some((r) => d.from >= r.from && d.from <= r.to && loader.from >= r.from && loader.from <= r.to));
    if (!covered) out.unshift(loader);
  }

  // 3. keys, dependsOn names, end mark lines
  for (const block of doc.blocks) {
    for (const entry of block.entries) {
      if (isKnownKey(keys, entry.key)) continue;
      out.push({
        from: entry.from, to: entry.keyTo, severity: 'warning', code: 'unknown-key', origin: 'keys',
        message: `未知のキー '${entry.key}'（カタログの FormulaInfo 設定にありません。loader は追加属性として保持するだけで、式には使われません）`,
        fix: '既知のキー（補完の一覧）か確認', detail: '',
      });
    }
  }
  const names = new Set(valuesOf(text, doc, 'calculatorName').map((v) => v.value?.text).filter(Boolean));
  const fi001 = errorEntry(catalog, 'FI001');
  for (const { value } of valuesOf(text, doc, 'dependsOn')) {
    if (!value) continue;
    let start = 0;
    for (const piece of value.text.split(',')) {
      const name = piece;
      if (name.trim() !== '' && !names.has(name)) {
        const from = valueToDoc(value, start);
        const to = valueToDoc(value, start + name.length);
        if (!out.some((d) => d.origin === 'loader' && d.from === from && d.to === to)) {
          const template = fi001.templates?.DEFAULT ? ja(fi001.templates.DEFAULT) : '';
          out.push({
            from, to, severity: 'error', code: fi001.code, origin: 'depends',
            message: `[${fi001.code}] ${ja(fi001.message)}${template ? ` ${template.replace('{value}', name)}` : ''}`,
            fix: ja(fi001.fix), detail: names.size ? `calculatorName: ${[...names].join(', ')}` : '',
          });
        }
      }
      start += piece.length + 1;
    }
  }
  for (const line of doc.endLines) {
    if (doc.blocks.some((b) => b.end?.from === line.from)) continue;
    if (out.some((d) => d.severity === 'error' && d.from <= line.to && d.to >= line.from && d.origin === 'loader')) continue;
    out.push({
      from: line.from, to: line.to, severity: 'warning', code: 'end-mark', origin: 'end',
      message: `${END_MARK} の後ろに文字があるため、この行はブロックを閉じません（前の値の続きとして読まれます）`,
      fix: `行を ${END_MARK} だけにする`, detail: '',
    });
  }
  out.sort((a, b) => a.from - b.from || a.to - b.to);
  return out;
}

