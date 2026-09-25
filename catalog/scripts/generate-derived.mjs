#!/usr/bin/env node
// Regenerates the files derived from catalog/tinyexpression-catalog.json (issue #201):
//   - tools/tinyexpression-p4-lsp-vscode/config/<group>.tecatalog  (variables, legacy format)
//   - tools/tinyexpression-p4-lsp-vscode/src/main/resources/error-catalog.json  (TE codes)
// and re-formats the catalog itself into its canonical text.
//
//   node catalog/scripts/generate-derived.mjs          # write
//   node catalog/scripts/generate-derived.mjs --check  # fail when anything is out of date (CI)
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { formatCatalog, tecatalogOf, errorCatalogJsonOf, countsOf } from './catalog-lib.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const catalogPath = join(root, 'catalog', 'tinyexpression-catalog.json');
const check = process.argv.includes('--check');
const catalog = JSON.parse(readFileSync(catalogPath, 'utf8'));

// Structural checks the JSON schema cannot express.
const problems = [];
const seen = new Set();
for (const e of catalog.errorCodes) {
  if (seen.has(e.code)) problems.push(`duplicate error code ${e.code}`);
  seen.add(e.code);
}
for (const r of catalog.diagnosticRules ?? []) {
  if (!seen.has(r.code)) problems.push(`diagnostic rule points to unknown code ${r.code}`);
}
if (!seen.has(catalog.defaultErrorCode)) problems.push(`defaultErrorCode ${catalog.defaultErrorCode} is not defined`);
const groupIds = new Set(catalog.variableGroups.map((g) => g.id));
const variableKeys = new Set();
for (const v of catalog.variables) {
  if (!groupIds.has(v.group)) problems.push(`variable ${v.name}: unknown group ${v.group}`);
  const key = `${v.group}/${v.match}/${v.name}`;
  if (variableKeys.has(key)) problems.push(`duplicate variable ${key}`);
  variableKeys.add(key);
}
const functionNames = new Set();
for (const f of catalog.functions) {
  if (functionNames.has(f.name)) problems.push(`duplicate function ${f.name}`);
  functionNames.add(f.name);
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}

const outputs = new Map();
outputs.set(catalogPath, formatCatalog(catalog));
for (const group of catalog.variableGroups) {
  if (group.tecatalog) outputs.set(join(root, group.tecatalog), tecatalogOf(catalog, group.id));
}
outputs.set(
  join(root, 'tools/tinyexpression-p4-lsp-vscode/src/main/resources/error-catalog.json'),
  errorCatalogJsonOf(catalog),
);

let stale = 0;
for (const [path, text] of outputs) {
  const current = existsSync(path) ? readFileSync(path, 'utf8') : null;
  if (current === text) continue;
  if (check) {
    console.error(`out of date: ${path.slice(root.length + 1)}`);
    stale++;
  } else {
    writeFileSync(path, text);
    console.log(`wrote ${path.slice(root.length + 1)}`);
  }
}
console.log(`catalog: ${JSON.stringify(countsOf(catalog))}`);
if (stale) {
  console.error('run: node catalog/scripts/generate-derived.mjs');
  process.exit(1);
}
