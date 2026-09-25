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
import { countsOf, derivedFiles, structuralProblems } from './catalog-lib.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const catalogPath = join(root, 'catalog', 'tinyexpression-catalog.json');
const check = process.argv.includes('--check');
const catalog = JSON.parse(readFileSync(catalogPath, 'utf8'));

// Structural checks the JSON schema cannot express (shared with the playground's editor).
const problems = structuralProblems(catalog);
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}

const outputs = new Map();
for (const [path, text] of derivedFiles(catalog)) outputs.set(join(root, path), text);

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
