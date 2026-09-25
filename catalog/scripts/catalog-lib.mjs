// Shared helpers for catalog/tinyexpression-catalog.json (issue #201): the canonical text
// format, and the files derived from the catalog for older consumers. Used by
// generate-derived.mjs (repo), the playground (browser) and its parity smoke (node).

/** The catalog's canonical text: 2-space JSON, one line per variable entry. */
export function formatCatalog(catalog) {
  const lines = ['{'];
  const keys = Object.keys(catalog);
  keys.forEach((key, index) => {
    const comma = index === keys.length - 1 ? '' : ',';
    const value = catalog[key];
    if (key === 'variables' && Array.isArray(value)) {
      lines.push(`  ${JSON.stringify(key)}: [`);
      value.forEach((entry, i) => {
        lines.push(`    ${JSON.stringify(entry)}${i === value.length - 1 ? '' : ','}`);
      });
      lines.push(`  ]${comma}`);
    } else {
      const body = JSON.stringify(value, null, 2).replace(/\n/g, '\n  ');
      lines.push(`  ${JSON.stringify(key)}: ${body}${comma}`);
    }
  });
  lines.push('}');
  return lines.join('\n') + '\n';
}

/** Japanese text of a localized field (falls back to English). */
export function ja(text) {
  if (text == null) return '';
  if (typeof text === 'string') return text;
  return text.ja ?? text.en ?? '';
}

/** English text of a localized field (falls back to Japanese). */
export function en(text) {
  if (text == null) return '';
  if (typeof text === 'string') return text;
  return text.en ?? text.ja ?? '';
}

/** The `.tecatalog` file of one variable group, byte-for-byte what the LSP shipped before #201. */
export function tecatalogOf(catalog, groupId) {
  const lines = ['tinyexpression-catalog-v1'];
  for (const v of catalog.variables) {
    if (v.group !== groupId) continue;
    if (v.match === 'prefixWithSuffix') {
      lines.push(['prefixWithSuffix', v.name, v.separator, String(v.minSuffixLength), ja(v.description), v.context].join('|'));
    } else {
      lines.push(['exact', v.name, ja(v.description), v.context].join('|'));
    }
  }
  return lines.join('\n') + '\n';
}

/** `error-catalog.json` (code -> {message, fix}), derived from the catalog's error codes. */
export function errorCatalogJsonOf(catalog) {
  const out = {};
  for (const e of catalog.errorCodes) {
    if (!/^TE\d+$/.test(e.code)) continue;
    out[e.code] = { message: ja(e.message), fix: ja(e.fix) };
  }
  return JSON.stringify(out, null, 2) + '\n';
}

/** Counts per section, printed by the generator and asserted by tests. */
export function countsOf(catalog) {
  const counts = {};
  for (const [key, value] of Object.entries(catalog)) {
    if (Array.isArray(value)) counts[key] = value.length;
  }
  return counts;
}

/**
 * Maps a parser failure to a TE code with the catalog's diagnosticRules. `expected` is the
 * list of terminals the parser could accept at the failure (without quotes), `snippet` the
 * text from the failure offset, `leading` the text before it. Rules apply in order; a rule
 * with `expected` matches when that terminal is in the list (keyword rules are skipped when
 * an expression could also start there, see the rule's `onlyIfNoExpressionStart`).
 */
export function resolveErrorCode(catalog, { expected = [], snippet = '', leading = '' }) {
  const rules = catalog.diagnosticRules ?? [];
  const expressionStart = expected.includes('$') || expected.includes('NUMBER');
  for (const rule of rules) {
    if (rule.consumers && !rule.consumers.includes('playground')) continue;
    if (rule.expected != null) {
      if (rule.onlyIfNoExpressionStart && expressionStart) continue;
      const tokens = Array.isArray(rule.expected) ? rule.expected : [rule.expected];
      if (tokens.some((t) => expected.includes(t))) return rule.code;
    } else if (rule.snippetStartsWith != null) {
      if (snippet.startsWith(rule.snippetStartsWith)) return rule.code;
    } else if (rule.leadingEndsWith != null) {
      if (leading.trimEnd().endsWith(rule.leadingEndsWith)) return rule.code;
    } else if (rule.snippetMatches != null) {
      if (new RegExp(`^(?:${rule.snippetMatches})$`).test(snippet)) return rule.code;
    }
  }
  return catalog.defaultErrorCode ?? 'TE020';
}

/** Finds a variable entry for a name (exact first, then prefixWithSuffix). */
export function lookupVariable(catalog, name) {
  const bare = name.startsWith('$') ? name.slice(1) : name;
  const exact = catalog.variables.find((v) => v.match !== 'prefixWithSuffix' && v.name === bare);
  if (exact) return exact;
  return catalog.variables.find((v) => v.match === 'prefixWithSuffix'
    && bare.startsWith(v.name + v.separator)
    && bare.length - v.name.length - v.separator.length >= (v.minSuffixLength ?? 1));
}
