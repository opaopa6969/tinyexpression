#!/usr/bin/env node
// Parity smoke (issue #201): the Java differential golden (issue #179,
// rust/tinyexpression-rs/tests/java-diff/golden/) evaluated through the playground's own
// evaluation path — the same tinyexpression.wasm, the same JS binding and the same
// CalculationContext mapping (src/context.js toRequest) the browser UI uses — must give the
// Java outcome: kind and bits for floats/doubles, the value for other kinds, and the Java
// exception class plus stage (create/apply) for errors.
//
//   node scripts/parity-smoke.mjs [path/to/tinyexpression.wasm]
//
// Rows whose formula calls `external` with registered test classes (Fee, TestSideEffector, ...)
// are skipped: their results depend on the arguments, and a playground external is a constant
// stub. Rows with unregistered externals are compared (the stubs model "class loadable, no
// instance"). random() rows compare the result kind only, as the Rust gate does.
//
// Rows whose formula has a ```java:ClassName code block (issue #216) are handled explicitly as
// "Rust requires a stub": Java compiles and runs the block, the playground (Rust) never does —
// the block only declares the class and calls go to the external stubs. Every such row is
// evaluated once with no stub for its code-block classes and must fail with the Rust hint
// (UnsupportedOperationException, "コードブロックのクラスは externals で値を指定してください"); the
// rows without registered externals are also compared with Java as usual (the unregistered stub
// = Java's "class compiled, no instance"). The rows with registered externals are not compared:
// Java's result comes from running the Java code, a stub's is a constant (the documented
// difference, rust/tinyexpression-rs/README.md).
//
// Every compared row is also evaluated through `te_eval_trace` (the Trace panel's path, issue
// #201 stage 3): apart from the added `trace`, its response must equal the untraced one, and
// the trace root must carry the result.
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadTinyExpression } from '../../rust/examples/wasm/tinyexpression.mjs';
import { emptyState, toRequest } from '../src/context.js';
import { codeBlocksOf, MISSING_STUB_HINT } from '../src/code-block.js';

const here = dirname(fileURLToPath(import.meta.url));
const golden = join(here, '..', '..', 'rust', 'tinyexpression-rs', 'tests', 'java-diff', 'golden');
const wasmPath = process.argv[2] ?? join(here, '..', 'public', 'tinyexpression.wasm');
const te = await loadTinyExpression(await readFile(wasmPath));

const formulas = (await readFile(join(golden, 'formulas.jsonl'), 'utf8'))
  .split('\n').filter(Boolean).map((line) => JSON.parse(line).text);
const rows = (await readFile(join(golden, 'p4ast.jsonl'), 'utf8'))
  .split('\n').filter(Boolean).map((line) => JSON.parse(line));

// The classes the Rust gate's TestHost can load, with their methods and argument counts.
const TEST_CLASSES = [
  ['org.unlaxer.tinyexpression.Fee', 'calculate', [3, 5]],
  ['org.unlaxer.tinyexpression.parser.TestSideEffector', 'setBlackList', [1, 2]],
  ['org.unlaxer.tinyexpression.parser.TestSideEffector', 'booleanToFloatMethod', [1]],
  ['org.unlaxer.tinyexpression.parser.TestSideEffector', 'salary', [2]],
  ['org.unlaxer.tinyexpression.parser.TestSideEffector', 'beforeSupecifiedDate', [1]],
  ['org.unlaxer.tinyexpression.parser.TestSideEffector', 'getAge', [1]],
  ['org.unlaxer.tinyexpression.parser.TestSideEffector', 'getYear', [1]],
  ['CheckDigits', 'check', [1]],
  ['sample.v1.CheckAlphabets', 'check', [1]],
];

/** A golden row as playground panel state: variables as rows, test classes as unregistered stubs. */
function stateOf(row) {
  const state = emptyState();
  state.resultType = row.rt;
  state.numberType = row.nt;
  state.seed = 7;
  state.variables = row.vars.map(([map, type, name, value]) => ({ name, type, value, map }));
  state.externals = TEST_CLASSES.flatMap(([cls, method, arities]) => arities.map((arity) => ({
    class: cls, method, arity: String(arity), registered: false, returnType: 'null',
  })));
  return state;
}

function outcomeOf(response) {
  const r = response.result;
  if (!r.ok) {
    if (r.stage !== 'create' && r.stage !== 'apply') return { kind: 'bad-request', text: JSON.stringify(r) };
    return { kind: 'error', text: `${r.error.kind}@${r.stage}` };
  }
  const v = r.value;
  switch (v.kind) {
    case 'number': return { kind: 'float', bits: v.f32Bits.slice(2) };
    case 'double': return { kind: 'double', bits: v.f64Bits.slice(2) };
    case 'object': return { kind: 'object', text: v.class };
    case 'null': return { kind: 'null', text: '' };
    case 'boolean': return { kind: 'boolean', text: String(v.value) };
    case 'string': return { kind: 'string', text: v.value };
    default: return { kind: v.kind, text: r.text }; // int/long/short/byte: exact decimal text
  }
}

function expectedOf(java) {
  if (java.kind === 'error') return { kind: 'error', text: `${java.text}@${java.stage}` };
  if (java.kind === 'float' || java.kind === 'double') return { kind: java.kind, bits: java.bits };
  return { kind: java.kind, text: java.text ?? '' };
}

const same = (a, b) => a.kind === b.kind && a.text === b.text && a.bits === b.bits;
const usesExternal = (formula) => /\bexternal\b/.test(formula);

let codeBlockRows = 0;
let codeBlockCompared = 0;
const codeBlockMismatches = [];
let compared = 0;
let skipped = 0;
let kindOnly = 0;
const mismatches = [];
const traceMismatches = [];
let traceSteps = 0;
const started = performance.now();
for (const row of rows) {
  const formula = formulas[row.f];
  const blockClasses = codeBlocksOf(formula).map((b) => b.className);
  if (blockClasses.length) {
    // "Rust requires a stub": without one for the code-block classes, the explicit error.
    codeBlockRows++;
    const state = stateOf(row);
    state.externals = state.externals.filter((x) => !blockClasses.includes(x.class));
    const r = te.evalContext(toRequest(state, { formula })).result;
    if (r.ok || r.stage !== 'apply' || r.error.kind !== 'UnsupportedOperationException' || !r.error.message.includes(MISSING_STUB_HINT)) {
      codeBlockMismatches.push(`${row.id} formula=${JSON.stringify(formula.slice(0, 60))}\n    wasm=${JSON.stringify(r).slice(0, 300)}`);
    }
    if (!(row.ext && usesExternal(formula))) codeBlockCompared++;
  }
  if (row.ext && usesExternal(formula)) {
    skipped++;
    continue;
  }
  const request = toRequest(stateOf(row), { formula });
  const response = te.evalContext(request);
  const rust = outcomeOf(response);
  const traced = te.evalTrace(request);
  const { trace, ...tracedRest } = traced.result;
  if (traced.code !== response.code || JSON.stringify(tracedRest) !== JSON.stringify(response.result)
    || (response.result.ok && trace?.root && !trace.truncated && trace.root.text !== response.result.text && !formula.includes('random('))) {
    traceMismatches.push(`${row.id} formula=${JSON.stringify(formula.slice(0, 120))}\n    plain=${JSON.stringify(response.result).slice(0, 200)}\n    traced=${JSON.stringify(tracedRest).slice(0, 200)}`);
  }
  traceSteps += trace?.steps ?? 0;
  const java = expectedOf(row.java);
  compared++;
  if (same(rust, java)) continue;
  if (formula.includes('random(') && rust.kind === java.kind) {
    kindOnly++;
    continue;
  }
  mismatches.push(`${row.id} rt=${row.rt} nt=${row.nt} profile=${row.profile} formula=${JSON.stringify(formula.slice(0, 120))}\n` +
    `    java=${JSON.stringify(java)}\n    wasm=${JSON.stringify(rust)}`);
}
const ms = performance.now() - started;
console.log(`parity smoke: ${compared} rows compared over ${formulas.length} formulas in ${ms.toFixed(0)} ms; ` +
  `${skipped} rows skipped (registered externals); ${kindOnly} random() rows compared by kind`);
console.log(`code blocks (#216): ${codeBlockRows} rows need a stub in Rust (no stub → the explicit error, checked); ` +
  `${codeBlockCompared} of them compared with Java (unregistered stubs), the rest not comparable (Java runs the code, a stub is a constant)`);
if (codeBlockMismatches.length || codeBlockRows === 0) {
  console.error(`${codeBlockMismatches.length} code-block rows without a stub did not give the explicit error:\n${codeBlockMismatches.slice(0, 20).join('\n')}`);
  process.exit(1);
}
console.log(`trace: every compared row also evaluated with te_eval_trace (${traceSteps} steps recorded)`);
if (traceMismatches.length) {
  console.error(`${traceMismatches.length} rows differ with tracing on:\n${traceMismatches.slice(0, 20).join('\n')}`);
  process.exit(1);
}
if (mismatches.length) {
  console.error(`${mismatches.length} rows differ from the Java golden:\n${mismatches.slice(0, 40).join('\n')}`);
  process.exit(1);
}
console.log('parity smoke OK: the playground evaluation path matches the Java golden');
