// Runs the wasm demo's binding under node, no browser (issue #181):
//   node rust/examples/wasm/node-smoke.mjs [path/to/tinyexpression.wasm]
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { loadTinyExpression } from './tinyexpression.mjs';

const here = fileURLToPath(new URL('.', import.meta.url));
const wasm = process.argv[2]
  ?? `${here}../../target/wasm32-unknown-unknown/release-small/tinyexpression.wasm`;
const te = await loadTinyExpression(await readFile(wasm));

const started = performance.now();
const sum = te.eval('1 + 2');
const firstEvalMs = performance.now() - started;
assert.equal(sum.code, 0);
assert.deepEqual(sum.result, { ok: true, value: { kind: 'number', value: '3', f32Bits: '0x40400000' } });

const branch = te.eval('if(3 > 2){(1 + 2) * 3}else{0}');
assert.equal(branch.result.value.value, '9');
const text = te.eval("'こんにちは' + '😀'");
assert.equal(text.result.value.value, 'こんにちは😀');

const broken = te.eval('1 +');
assert.equal(broken.code, 3);
assert.equal(broken.result.stage, 'parse');

assert.equal(te.check('$a * 2').result.ok, true);
assert.equal(te.parse('$a * 2').result.ast.type, 'FormulaExpr');

const run = te.run('calculatorName:base\nformula:\n1 + 2\n---END_OF_PART---\n');
assert.equal(run.code, 0);
assert.equal(run.result.formulas[0].value.value, '3');

const contextual = te.evalContext({
  formula: 'if($member){$price * 2}else{$price}',
  variables: [{ name: 'member', type: 'boolean', value: true }, { name: 'price', type: 'float', value: '1.5' }],
});
assert.equal(contextual.code, 0);
assert.equal(contextual.result.text, '3.0');
const contextRun = te.runContext({
  document: 'calculatorName:base\nformula:\n$x + 1\n---END_OF_PART---\n',
  variables: [{ name: 'x', type: 'float', value: 2 }],
});
assert.equal(contextRun.result.formulas[0].value.value, '3');

// Deep nesting has no stack escalation thread on wasm32: it fails with a diagnostic, not a trap.
const deep = te.eval('('.repeat(3000) + '1' + ')'.repeat(3000));
assert.ok(deep.code === 0 || deep.code === 3, `deep nesting: exit ${deep.code}`);

const version = te.version();
assert.equal(version.abi, 1);
console.log(`wasm demo OK: ${JSON.stringify(sum.result.value)}; first eval ${firstEvalMs.toFixed(2)} ms; ` +
  `deep nesting exit ${deep.code}; ${JSON.stringify(version)}`);
