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
import { ja } from '../../catalog/scripts/catalog-lib.mjs';
import { createCatalogPullRequest, compareUrlOf } from '../src/github-pr.js';
import { CompletionContext } from '@codemirror/autocomplete';
import { EditorState } from '@codemirror/state';
import { analyzeFormula, lexiconOf, bracketAtCursor } from '../src/te-lexer.js';
import { analyzeFormulaInfo, parseFormulaInfo, normalizedValue, setEndMarkTrailingSpace } from '../src/formula-info-syntax.js';
import { diagnoseFormulaInfo, formulaInfoKeys, isKnownKey } from '../src/formula-info-diagnostics.js';
import { formulaInfoCompletionSource, formulaInfoHoverAt } from '../src/formula-info-editor.js';

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


// 9. editors (issue #212): tokens and bracket depth, the FormulaInfo scanner, completion,
//    diagnostics at the loader's / te_check's positions, hover.
{
  const lexicon = lexiconOf(catalog);
  const keys = formulaInfoKeys(catalog);
  const analyzeInfo = (text) => analyzeFormulaInfo(text, { lexicon, isKnownKey: (k) => isKnownKey(keys, k) });
  const typed = (text, tokens) => tokens.map((t) => `${t.type}:${text.slice(t.from, t.to)}`);

  // Tokens: strings / comments hide their brackets, keywords vs calls, variables.
  const f = "if($a > 1){ max(1, (2)) }else{ 'x(' } // (";
  const { tokens, brackets } = analyzeFormula(f, lexicon);
  assert.deepEqual(typed(f, tokens).slice(0, 7), ['keyword:if', 'bracket:(', 'variable:$a', 'operator:>', 'number:1', 'bracket:)', 'bracket:{']);
  assert.ok(typed(f, tokens).includes('function:max'));
  assert.ok(typed(f, tokens).includes("string:'x('"));
  assert.ok(typed(f, tokens).includes('comment:// ('));
  // Depth: `{` 0, `max(` 1, `(2)` 2; each pair shares its depth.
  assert.deepEqual(brackets.map((b) => `${b.char}${b.depth}`).join(' '), '(0 )0 {0 (1 (2 )2 )1 }0 {0 }0');
  assert.ok(brackets.every((b) => b.partner >= 0 && brackets[b.partner].partner === brackets.indexOf(b)));
  // Unmatched: `)` closes `(` past the unclosed `[`; a stray `)` / `}` has no partner.
  const bad = analyzeFormula('(1 + [2) ) }', lexicon).brackets;
  assert.deepEqual(bad.map((b) => `${b.char}${b.partner < 0 ? '!' : ''}`).join(' '), '( [! ) )! }!');
  // The pair at the cursor: just after it, else just before it.
  const pair = bracketAtCursor(brackets, f.indexOf('(2)') + 2);
  assert.equal(pair.bracket.char, ')');
  assert.equal(pair.partner.from, f.indexOf('(2)'));
  assert.equal(bracketAtCursor(brackets, f.indexOf('2)) }') + 1).partner.from, f.indexOf('(2)'), 'after the cursor first');
  assert.equal(bracketAtCursor(brackets, f.indexOf(' }else') + 1).partner.from, f.indexOf('{ max'), 'the closing brace at the cursor');
  // Non-BMP text before a bracket: JS indices, never inside a surrogate pair.
  const emoji = analyzeFormula("'😀' + (1)", lexicon);
  assert.deepEqual(emoji.brackets.map((b) => b.from), ["'😀' + ".length, "'😀' + (1".length]);

  // The scanner: blocks, keys, the value the loader sees (# and blank lines dropped).
  const doc = 'tags:NORMAL\nfoo:1\ncalculatorName:a\nformula:\n# note\n\n(1 +\n 2)\n---END_OF_PART---\n'
    + 'calculatorName:b\ndependsOn:a,zz\nformula:\n$x + (\n---END_OF_PART---\n';
  const parsed = parseFormulaInfo(doc);
  assert.equal(parsed.blocks.length, 2);
  assert.deepEqual(parsed.blocks[0].entries.map((e) => e.key), ['tags', 'foo', 'calculatorName', 'formula']);
  const formula = normalizedValue(doc, parsed.blocks[0].entries[3]);
  assert.equal(formula.text, '(1 +\n 2)');
  // The Rust loader normalises the same way.
  assert.equal(te.load(doc.split('---END_OF_PART---\n')[0]).result.formulas[0].info.formulaText, formula.text);
  const info = analyzeInfo(doc);
  const infoTokens = typed(doc, info.tokens);
  assert.ok(infoTokens.includes('fi-key:tags') && infoTokens.includes('fi-key-unknown:foo') && infoTokens.includes('fi-colon::'));
  assert.ok(infoTokens.includes('fi-comment:# note') && infoTokens.includes('fi-end:---END_OF_PART---'));
  assert.ok(infoTokens.includes('variable:$x'), 'formula values carry the tinyexpression colouring');
  // Brackets per formula: the first pair matches across its lines, the second `(` is open.
  assert.deepEqual(info.brackets.map((b) => [doc[b.from], b.depth, b.partner >= 0]), [['(', 0, true], [')', 0, true], ['(', 0, false]]);

  // Diagnostics: unknown key (warning), TE code of each formula at its document position,
  // every unknown dependsOn name (FI001).
  const found = diagnoseFormulaInfo(te, catalog, doc, [], info);
  const summary = found.map((d) => `${d.severity}:${d.code}:${doc.slice(d.from, d.to)}`);
  assert.deepEqual(summary, ['warning:unknown-key:foo', 'error:FI001:zz', 'warning:TE022:$x', 'error:TE020:(']);
  const te020 = found.find((d) => d.code === 'TE020');
  assert.match(te020.message, /^\[TE020\] /);
  assert.equal(te020.fix, ja(catalog.errorCodes.find((e) => e.code === 'TE020').fix));

  // The loader's error at the span Rust returns (code points → JS indices, as trace.js).
  const emojiDoc = 'description:😀😀\ncalculatorName:a\nresultType:Nope\nformula:\n1\n';
  const loadFailure = te.load(emojiDoc).result;
  assert.equal(loadFailure.error.kind, 'unknown_type');
  assert.deepEqual(loadFailure.span, [43, 47], 'code points');
  const [typeError] = diagnoseFormulaInfo(te, catalog, emojiDoc, [], analyzeInfo(emojiDoc));
  assert.equal(emojiDoc.slice(typeError.from, typeError.to), 'Nope');
  assert.match(typeError.message, /未知の型です（RuntimeException）/);
  // Syntax errors (junk between blocks) point at the rejected line.
  const junk = 'calculatorName:a\nformula:\n1\n---END_OF_PART---\ngarbage\n';
  const [syntax] = diagnoseFormulaInfo(te, catalog, junk, [], analyzeInfo(junk));
  assert.equal(junk.slice(syntax.from, syntax.to), 'garbage');
  assert.equal(syntax.kind, 'syntax');
  assert.ok(analyzeInfo(junk).tokens.some((t) => t.type === 'fi-junk' && junk.slice(t.from, t.to) === 'garbage'));
  // A blank formula / empty value at the end: the loader's error at the entry.
  const empty = 'calculatorName:a\nformula:';
  const [atEnd] = diagnoseFormulaInfo(te, catalog, empty, [], analyzeInfo(empty));
  assert.equal(empty.slice(atEnd.from, atEnd.to), 'formula:');
  // A formula error both the loader and te_check see is reported once, with the TE code.
  const broken = 'calculatorName:a\nformula:\n(1 + 2\n';
  const brokenFound = diagnoseFormulaInfo(te, catalog, broken, [], analyzeInfo(broken));
  assert.deepEqual(brokenFound.map((d) => d.code), ['TE004']);
  // The sample loads cleanly.
  assert.deepEqual(diagnoseFormulaInfo(te, catalog, FORMULA_INFO_SAMPLE, ['bonus', 'member', 'age'], analyzeInfo(FORMULA_INFO_SAMPLE)), []);

  // End mark with trailing characters: not a block end (issue #211 decides spaces).
  const probe = te.load('calculatorName:a\nformula:\n1\n---END_OF_PART--- \n').result;
  const spaceEnds = probe.ok === true && probe.formulas?.[0]?.info?.formulaText === '1';
  setEndMarkTrailingSpace(spaceEnds);
  const trailing = 'calculatorName:a\nformula:\n1\n---END_OF_PART---x\n';
  assert.ok(analyzeInfo(trailing).tokens.some((t) => t.type === 'fi-end-bad'));
  assert.ok(diagnoseFormulaInfo(te, catalog, trailing, [], analyzeInfo(trailing)).length > 0);
  const spaced = 'calculatorName:a\nformula:\n1\n---END_OF_PART---  \n';
  assert.equal(parseFormulaInfo(spaced).blocks[0].end != null, spaceEnds);

  // Completion.
  const vars = [{ name: 'bonus', type: 'float', value: '1' }];
  const source = formulaInfoCompletionSource(() => te, () => catalog, () => vars);
  const complete = async (marked) => {
    const pos = marked.indexOf('|');
    const text = marked.replace('|', '');
    const result = await source(new CompletionContext(EditorState.create({ doc: text }), pos, true));
    return result && { from: result.from, labels: result.options.map((o) => o.label) };
  };
  const keysAt = await complete('calculatorName:a\nres|');
  assert.equal(keysAt.from, 17);
  assert.ok(keysAt.labels.includes('resultType:') && keysAt.labels.includes('formula:'));
  assert.ok((await complete('calculatorName:a\n|')).labels.includes('---END_OF_PART---'));
  assert.deepEqual((await complete('calculatorName:a\n---|')).labels, ['---END_OF_PART---']);
  assert.deepEqual((await complete('calculatorName:a\nexecutionBackend:|')).labels,
    catalog.settings.find((x) => x.name === 'executionBackend').values);
  const dependsDoc = 'calculatorName:a\nformula:\n1\n---END_OF_PART---\ncalculatorName:b\ndependsOn:x,|\nformula:\n2\n';
  const depends = await complete(dependsDoc);
  assert.deepEqual(depends.labels, ['a'], 'other blocks only');
  assert.equal(depends.from, dependsDoc.indexOf('|'));
  const inFormula = await complete('calculatorName:a\nformula:\n# c\n$bo|\n');
  assert.equal(inFormula.labels[0], '$bonus');
  assert.equal(inFormula.from, 'calculatorName:a\nformula:\n# c\n'.length);
  assert.ok((await complete('calculatorName:a\nformula:\n1 + sq|\n')).labels.includes('sqrt'));

  // Hover.
  const hoverDoc = 'calculatorName:a\nformula:\n$bonus + sqrt(4)\n---END_OF_PART---\nfoo:1\ndependsOn:a\nformula:\n1\n';
  const hover = (word, delta = 1) => formulaInfoHoverAt(catalog, vars, hoverDoc, hoverDoc.indexOf(word) + delta);
  assert.match(hover('calculatorName').content.rows[0][1], /式を識別する名前/);
  assert.equal(hover('sqrt').content.title, 'sqrt');
  assert.deepEqual(hover('$bonus').content.rows[0], ['CalculationContext', 'float = 1']);
  assert.equal(hover('foo').content.rows[0][0], '未知のキー');
  assert.equal(hover('dependsOn:a', 10).content.rows[0][0], 'calculatorName');
  assert.equal(hover('---END').content.title, '---END_OF_PART---');

  // Key descriptions come from the catalog `settings` (scope formulaInfo): every key the Java
  // loader reads (FormulaInfoParser.extractFormulaInfo, plus the siteId / checkKind fields of
  // the loader configuration) is there, with ja and en texts.
  for (const key of ['tags', 'description', 'periodStartInclusive', 'periodEndExclusive', 'calculatorName', 'dependsOn',
    'resultType', 'numberType', 'executionBackend', 'backend', 'hash', 'hashByByteCode', 'formula', 'javaCode', 'byteCode',
    'byteCode_Foo', 'siteId', 'checkKind']) {
    assert.ok(isKnownKey(keys, key), key);
  }
  for (const setting of keys.values()) assert.ok(setting.description?.ja && setting.description?.en, setting.name);
  assert.ok(keysAt.labels.includes('siteId:') && !keysAt.labels.some((l) => l.includes('<')), 'patterns are not completed');
  const bytecodeDoc = 'calculatorName:a\nbyteCode_Foo:00\nformula:\n1\n';
  assert.match(formulaInfoHoverAt(catalog, vars, bytecodeDoc, bytecodeDoc.indexOf('byteCode_Foo') + 1).content.rows[0][1], /byteCode_/);
  // A Catalog panel edit (the `settings` section) applies at once: new text, a new key.
  const edited = clone(catalog);
  edited.settings.find((x) => x.scope === 'formulaInfo' && x.name === 'calculatorName').description.ja = '編集した説明';
  edited.settings.push({ name: 'owner', scope: 'formulaInfo', type: 'enum', values: ['alice', 'bob'], description: { ja: '担当', en: 'Owner' } });
  const editedDoc = 'calculatorName:a\nowner:\nformula:\n1\n';
  assert.equal(formulaInfoHoverAt(edited, vars, editedDoc, 1).content.rows[0][1], '編集した説明');
  assert.ok(!diagnoseFormulaInfo(te, edited, editedDoc, [], analyzeFormulaInfo(editedDoc, { lexicon })).some((d) => d.code === 'unknown-key'));
  assert.ok(diagnoseFormulaInfo(te, catalog, editedDoc, [], analyzeInfo(editedDoc)).some((d) => d.code === 'unknown-key'));
  const editedSource = formulaInfoCompletionSource(() => te, () => edited, () => vars);
  const ownerValues = await editedSource(new CompletionContext(EditorState.create({ doc: editedDoc }), 'calculatorName:a\nowner:'.length, true));
  assert.deepEqual(ownerValues.options.map((o) => o.label), ['alice', 'bob']);
}

console.log(`playground check OK: catalog valid (${catalog.variables.length} variables, ${catalog.errorCodes.length} codes), ` +
  `${EXAMPLES.length} samples, diagnostics, FormulaInfo, trace, catalog editing, PR helper, editors (tokens, brackets, FormulaInfo completion / diagnostics / hover)`);
