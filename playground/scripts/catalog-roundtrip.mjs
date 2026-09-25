#!/usr/bin/env node
// Catalog round trip (issue #201, stage 4), run in CI:
//   1. the playground's editing model exports the repository catalog unchanged: loading
//      catalog/tinyexpression-catalog.json with no edits and exporting it gives the same bytes;
//   2. the export validates against tinyexpression-catalog.schema.json (the standalone validator
//      the browser uses, and ajv) and the structural rules of generate-derived.mjs;
//   3. the files derived from the export (.tecatalog groups, error-catalog.json) are
//      byte-identical to the committed ones;
//   4. the golden override the VSIX test imports
//      (tools/tinyexpression-p4-lsp-vscode/src/test/resources/catalog-golden/playground-export.override.json)
//      is exactly what the Catalog panel exports for the fixture edits below, and merging it
//      back reproduces the edited catalog.
//
//   node scripts/catalog-roundtrip.mjs           # check
//   node scripts/catalog-roundtrip.mjs --write   # rewrite the golden override
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import assert from 'node:assert/strict';
import Ajv2020 from 'ajv/dist/2020.js';
import { derivedFiles, structuralProblems } from '../../catalog/scripts/catalog-lib.mjs';
import {
  clone, mergeOverride, overrideOf, formatOverride, exportCatalog, unifiedDiff, jsonPatch, applyPatch,
} from '../../catalog/scripts/catalog-edit.mjs';
import { validate as validateStandalone } from '../src/generated/catalog-validator.js';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..');
const read = (path) => readFileSync(join(root, path), 'utf8');
const catalogText = read('catalog/tinyexpression-catalog.json');
const bundled = JSON.parse(catalogText);
const schema = JSON.parse(read('catalog/tinyexpression-catalog.schema.json'));
const ajv = new Ajv2020({ allErrors: true, strict: false }).compile(schema);

// 1. no edits: the export is the repository file, the override is empty
const model = mergeOverride(bundled, {}); // what the playground starts from without saved edits
const exported = exportCatalog(model);
assert.equal(exported, catalogText, 'exporting the unedited catalog changes the file');
assert.deepEqual(overrideOf(bundled, model), {});
assert.equal(unifiedDiff(catalogText, exported), '');
assert.deepEqual(jsonPatch(bundled, model), []);

// 2. validation
const reread = JSON.parse(exported);
assert.ok(validateStandalone(reread), JSON.stringify(validateStandalone.errors?.slice(0, 3)));
assert.ok(ajv(reread), JSON.stringify(ajv.errors?.slice(0, 3)));
assert.deepEqual(structuralProblems(reread), []);

// 3. derived files, byte for byte
const derived = derivedFiles(reread);
for (const [path, text] of derived) assert.equal(text, read(path), `${path} differs from its regeneration`);

// 4. the golden override of the fixture edits
function fixtureEdits(catalog) {
  const edited = clone(catalog);
  const sqrt = edited.functions.find((f) => f.name === 'sqrt');
  sqrt.description = { ja: 'playground で編集した平方根の説明', en: 'Square root, edited in the playground' };
  sqrt.examples = ['sqrt(81)'];
  const te006 = edited.errorCodes.find((e) => e.code === 'TE006');
  te006.fix = { ja: '行末に ; を付ける（playground で編集）' };
  const te004 = edited.errorCodes.find((e) => e.code === 'TE004');
  te004.variants = [...(te004.variants ?? []), { source: 'playground', message: { ja: '閉じ括弧 ) が足りません。' }, fix: { ja: ') を追加' } }];
  edited.variables.push({
    name: 'riskScore', match: 'exact', type: 'float',
    description: { ja: 'playground で追加したリスクスコア', en: 'Risk score added in the playground' },
    context: 'FA', group: 'fa-variables',
  });
  return edited;
}
const edited = fixtureEdits(bundled);
assert.ok(validateStandalone(edited));
assert.deepEqual(structuralProblems(edited), []);
const override = overrideOf(bundled, edited);
assert.deepEqual(mergeOverride(bundled, override), edited, 'the override does not reproduce the edits');
assert.deepEqual(applyPatch(bundled, jsonPatch(bundled, edited)), edited, 'the JSON Patch does not reproduce the edits');
const goldenPath = 'tools/tinyexpression-p4-lsp-vscode/src/test/resources/catalog-golden/playground-export.override.json';
const golden = formatOverride(override);
if (process.argv.includes('--write')) {
  writeFileSync(join(root, goldenPath), golden);
  console.log(`wrote ${goldenPath}`);
} else {
  assert.equal(read(goldenPath), golden, `${goldenPath} is stale: node scripts/catalog-roundtrip.mjs --write`);
}
// The edits change exactly the catalog and the derived files they touch.
const changed = [...derivedFiles(edited)].filter(([path, text]) => derived.get(path) !== text).map(([path]) => path);
assert.deepEqual(changed.sort(), [
  'catalog/tinyexpression-catalog.json',
  'tools/tinyexpression-p4-lsp-vscode/config/fa-variables.tecatalog',
  'tools/tinyexpression-p4-lsp-vscode/src/main/resources/error-catalog.json',
]);

console.log(`catalog round trip OK: unedited export is byte-identical (${catalogText.length} bytes), schema valid, `
  + `${derived.size} derived files identical, golden override (${Object.keys(override).join(', ')}) matches`);
