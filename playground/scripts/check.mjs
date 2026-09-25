#!/usr/bin/env node
// Node checks for the playground's non-DOM logic (issue #201): the catalog validates against its
// schema, every sample evaluates with its CalculationContext, and diagnostics carry TE codes
// and catalog texts.
//   node scripts/check.mjs [path/to/tinyexpression.wasm]
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import assert from 'node:assert/strict';
import Ajv2020 from 'ajv/dist/2020.js';
import { loadTinyExpression } from '../../rust/examples/wasm/tinyexpression.mjs';
import { toRequest, emptyState } from '../src/context.js';
import { diagnose, describeFailure } from '../src/diagnostics.js';
import { EXAMPLES, FORMULA_INFO_SAMPLE } from '../src/examples.js';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..');
const te = await loadTinyExpression(await readFile(process.argv[2] ?? join(here, '..', 'public', 'tinyexpression.wasm')));
const catalog = JSON.parse(await readFile(join(root, 'catalog', 'tinyexpression-catalog.json'), 'utf8'));
const schema = JSON.parse(await readFile(join(root, 'catalog', 'tinyexpression-catalog.schema.json'), 'utf8'));

// 1. schema
const validate = new Ajv2020({ allErrors: true, strict: false }).compile(schema);
assert.ok(validate(catalog), JSON.stringify(validate.errors?.slice(0, 5), null, 2));

// 2. samples
const expected = {
  basic: '7.0', member: '100.0', grade: 'B', 'business-hours': '1.0', 'fraud-alert': '1.0', 'fraud-score': '5.0', external: '1100.0',
};
for (const example of EXAMPLES) {
  const context = { ...emptyState(), ...example.context };
  const { result } = te.evalContext(toRequest(context, { formula: example.formula }));
  if (example.id === 'broken') {
    assert.equal(result.ok, false);
    const failure = describeFailure(catalog, example.formula, result);
    assert.equal(failure.code, 'TE006', JSON.stringify(failure));
    continue;
  }
  assert.ok(result.ok, `${example.id}: ${JSON.stringify(result)}`);
  assert.equal(result.text, expected[example.id], example.id);
  const warnings = diagnose(te, catalog, example.formula, context.variables.map((v) => v.name));
  assert.deepEqual(warnings, [], `${example.id} has diagnostics: ${JSON.stringify(warnings)}`);
}

// 3. diagnostics carry TE codes and catalog texts
const codeOf = (formula, names = []) => diagnose(te, catalog, formula, names).map((d) => d.code);
assert.deepEqual(codeOf('(1 + 2'), ['TE004']);
assert.deepEqual(codeOf('if($a > 1){1', ['a']), ['TE005']);
assert.deepEqual(codeOf("var $x as number set 1 description='x'\n$x"), ['TE006']);
assert.deepEqual(codeOf('$a && $b', ['a', 'b']), ['TE023']);
assert.deepEqual(codeOf('$unknownOne + 1'), ['TE022']);
assert.deepEqual(codeOf('$accountBalance + 1'), [], 'catalog variables are known');
assert.deepEqual(codeOf('$acceptParams_ua == "x"'), [], 'prefixWithSuffix variables are known');
const [semi] = diagnose(te, catalog, "var $x as number set 1 description='x'\n$x");
assert.equal(semi.message, '[TE006] 文末のセミコロンが必要です。 修正例: 文の末尾に ; を追加');
// The parser reports the failure after the whitespace: at the start of the next line's `$x`.
assert.equal(semi.from, "var $x as number set 1 description='x'\n".length);

// 4. runtime error texts
const zero = te.evalContext({ formula: '1 / 0', resultType: 'int', numberType: 'int' }).result;
assert.equal(describeFailure(catalog, '1 / 0', zero).code, 'ArithmeticException');
assert.match(describeFailure(catalog, '1 / 0', zero).title, /0 除算/);

// 5. FormulaInfo with the context
const run = te.runContext(toRequest({ ...emptyState(), variables: [
  { name: 'bonus', type: 'float', value: '2' }, { name: 'member', type: 'boolean', value: 'true' }, { name: 'age', type: 'float', value: '20' },
] }, { document: FORMULA_INFO_SAMPLE })).result;
assert.ok(run.ok, JSON.stringify(run));
assert.deepEqual(run.formulas.map((f) => f.value.value), ['42', true]);

console.log(`playground check OK: catalog valid (${catalog.variables.length} variables, ${catalog.errorCodes.length} codes), ` +
  `${EXAMPLES.length} samples, diagnostics, FormulaInfo`);
