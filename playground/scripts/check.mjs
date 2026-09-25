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
import { prepareTrace, stepEvents, stackAt, failingNode } from '../src/trace.js';
import { validate as validateGenerated } from '../src/generated/catalog-validator.js';
import { mergeOverride, overrideOf, clone } from '../../catalog/scripts/catalog-edit.mjs';
import { createCatalogPullRequest, compareUrlOf } from '../src/github-pr.js';

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

// 6. evaluation trace (stage 3): same result as evalContext, spans map to the source, steps in
//    evaluation order, the failing step found.
{
  const request = toRequest({ ...emptyState(), variables: [{ name: 'a', type: 'float', value: '3' }] },
    { formula: 'if($a > 1){ $a * 2 + 1 }else{ 0 }' });
  const plain = te.evalContext(request).result;
  const traced = te.evalTrace(request).result;
  assert.equal(traced.text, plain.text);
  assert.deepEqual({ ...traced, trace: undefined }, { ...plain, trace: undefined });
  const { root, nodes } = prepareTrace(traced.trace.root, request.formula);
  assert.equal(root.kind, 'IfExpr');
  assert.equal(root.text, '7.0');
  const comparison = nodes.find((n) => n.kind === 'ComparisonExpr');
  assert.equal(comparison.source, '$a > 1');
  assert.equal(comparison.text, 'true');
  assert.ok(!nodes.some((n) => n.source === '0'), 'the else branch is not evaluated');
  const events = stepEvents(root);
  assert.equal(events.length, nodes.length * 2);
  assert.equal(events[0].node, root);
  assert.equal(events.at(-1).node, root);
  // Right after the comparison finishes, the stack is the if → comparison's parents, holding it.
  const after = events.findIndex((e) => e.type === 'exit' && e.node === comparison);
  const frames = stackAt(events, after);
  assert.equal(frames[0].node, root);
  assert.ok(frames.at(-1).done.includes(comparison));
  // Non-BMP text before the spans: code points map to JS indices.
  const emoji = te.evalTrace({ formula: "'😀' + 'x'", resultType: 'string' }).result;
  const prepared = prepareTrace(emoji.trace.root, "'😀' + 'x'");
  assert.ok(prepared.nodes.some((n) => n.source === "'x'"), JSON.stringify(prepared.nodes.map((n) => n.source)));
  const failing = te.evalTrace({ formula: '1 + 10 / $zero', resultType: 'int', numberType: 'int', variables: [{ name: 'zero', type: 'int', value: '0' }] }).result;
  assert.equal(failing.stage, 'apply');
  const failed = failingNode(prepareTrace(failing.trace.root, '1 + 10 / $zero').root);
  assert.equal(failed.source, '10 / $zero');
  assert.equal(failed.error.kind, 'ArithmeticException');
  assert.equal(te.evalTrace({ formula: '1 +' }).result.trace, null);
}

// 7. catalog editing (stage 4): the generated standalone validator agrees with ajv, an override
//    of an edit merges back to the edit.
{
  assert.equal(validateGenerated(catalog), true);
  const broken = clone(catalog);
  broken.functions[0].returns = 'banana';
  assert.equal(validate(broken), false);
  assert.equal(validateGenerated(broken), false);
  const edited = clone(catalog);
  edited.functions.find((f) => f.name === 'sqrt').description.ja = 'edited';
  edited.variables.push({ name: 'riskScore', match: 'exact', type: 'float', description: { ja: 'added' }, context: 'FA', group: 'fa-variables' });
  const override = overrideOf(catalog, edited);
  assert.deepEqual(Object.keys(override), ['variables', 'functions']);
  assert.deepEqual(mergeOverride(catalog, override), edited);
}

// 8. the "Create PR" helper talks to the GitHub API in this order (mocked fetch, no network)
{
  const calls = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    const path = url.replace('https://api.github.com', '');
    const body = init.body ? JSON.parse(init.body) : null;
    calls.push([init.method, path, body, init.headers.Authorization]);
    const reply = {
      'GET /repos/o/r/git/ref/heads/master': { object: { sha: 'base1' } },
      'GET /repos/o/r/git/commits/base1': { tree: { sha: 'tree0' } },
      'POST /repos/o/r/git/trees': { sha: 'tree1' },
      'POST /repos/o/r/git/commits': { sha: 'commit1' },
      'POST /repos/o/r/git/refs': { ref: 'refs/heads/b' },
      'POST /repos/o/r/pulls': { number: 7, html_url: 'https://github.com/o/r/pull/7' },
    }[`${init.method} ${path}`];
    return { ok: reply != null, status: reply ? 200 : 404, text: async () => JSON.stringify(reply ?? { message: 'nope' }) };
  };
  try {
    const files = new Map([['catalog/tinyexpression-catalog.json', '{}\n']]);
    const done = await createCatalogPullRequest({ token: 't0k', owner: 'o', repo: 'r', base: 'master', branch: 'b', title: 'T', body: 'B', files });
    assert.equal(done.pullRequest.number, 7);
    assert.deepEqual(calls.map(([m, p]) => `${m} ${p}`), [
      'GET /repos/o/r/git/ref/heads/master', 'GET /repos/o/r/git/commits/base1', 'POST /repos/o/r/git/trees',
      'POST /repos/o/r/git/commits', 'POST /repos/o/r/git/refs', 'POST /repos/o/r/pulls',
    ]);
    assert.deepEqual(calls[2][2], { base_tree: 'tree0', tree: [{ path: 'catalog/tinyexpression-catalog.json', mode: '100644', type: 'blob', content: '{}\n' }] });
    assert.deepEqual(calls[4][2], { ref: 'refs/heads/b', sha: 'commit1' });
    assert.ok(calls.every(([, , , auth]) => auth === 'Bearer t0k'));
    assert.match(compareUrlOf({ owner: 'o', repo: 'r', branch: 'b', title: 'T', body: 'B' }), /^https:\/\/github.com\/o\/r\/compare\/master\.\.\.b\?expand=1&title=T&body=B$/);
  } finally {
    globalThis.fetch = realFetch;
  }
}

console.log(`playground check OK: catalog valid (${catalog.variables.length} variables, ${catalog.errorCodes.length} codes), ` +
  `${EXAMPLES.length} samples, diagnostics, FormulaInfo, trace, catalog editing, PR helper`);
