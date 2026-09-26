# Changelog

All notable changes to TinyExpression are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [2.0.1] - Unreleased

### Dependencies
- Bumped `unlaxer-common` / `unlaxer-dsl` (`unlaxer.version`) from 3.0.15 to [3.1.0](https://github.com/opaopa6969/unlaxer-parser/releases/tag/3.1.0) (#209). Only the `classic` engine (`p4Engine:classic`, alias `legacy`, or `-Dtinyexpression.p4.engine=classic`) links against it; the default `ubnfc` engine is unaffected.
- `classic` parses now retain a smaller tree (-33% size / -40% object count on a 20 KB input, sub-sources are views over the root's code points) and the packrat memo table is windowed (default 1024 code points behind the parse frontier) instead of growing with input length, bounding memory to the grammar's backtracking distance.
- `classic` also gains `SAFE_FAILURES` memoization replay for safe successes and `Source.sourceRange()` for reading a token's `[start, start+length)` extent without building a `CursorRange`; neither changes observable parse/evaluation results.

### Added
- Language catalog `catalog/tinyexpression-catalog.json` (+ JSON schema) as the single source of variable / function / keyword descriptions, TE / FI error texts and the TE code rules; everything from the VSIX (`config/*.tecatalog`, the Ext's `ERROR_CATALOG` / snippet tables, `error-catalog.json`) migrated with matching counts. The LSP server reads it through `CatalogProvider` (hover, completion docs, diagnostics) and merges `tinyExpressionP4Lsp.catalog.overridePath` (#201).
- Playground (`playground/`, GitHub Pages <https://opaopa6969.github.io/tinyexpression/>): CodeMirror 6 + `tinyexpression.wasm`, CalculationContext panel, completion / hover / TE diagnostics from the catalog, FormulaInfo load and run; a node parity smoke runs the Java differential golden through it in CI (#201).
- Rust: evaluation with a caller-supplied CalculationContext and stubbed externals — `api::eval_context_json` / `formula_info_context_json`, CLI `eval-context` / `run-context`, C ABI and wasm `te_eval_context` / `te_formula_info_context` (#201). On wasm32 the default nested `call` depth is 48 (native 256) so deep recursion is a `StackOverflowError`, not an engine trap.
- Evaluation trace (#201 stage 3): `Program::eval_tree_traced` with a `TraceHook` (`on_enter` / `on_exit`) on the Java-compatible tree walker and the `TraceRecorder` (node kind, code-point span, value or error; bounded to 20,000 steps); `api::eval_trace_json`, CLI `eval --trace` / `eval-context --trace`, C ABI and wasm `te_eval_trace` (additive, ABI version unchanged). The Java differential gate runs every row traced and untraced and requires identical outcomes; the closure compiler is untouched.
- Playground (#201 stages 3–4): Trace panel (collapsible tree of sub-expression → value / type, click to highlight, step mode in evaluation order with the stack of partial values, failing step with the catalog hint) and Catalog panel (browse / search / edit ja-en texts, examples, fix hints, add entries and error-code variants, in-browser schema validation, export full JSON / override JSON / unified diff / JSON Patch, PR through the GitHub API with an in-memory token). `playground/scripts/catalog-roundtrip.mjs` checks in CI that an unedited export is byte-identical and the derived files regenerate byte-identically.
- VSIX (#201 stages 4–5): "TinyExpression: Open playground" (the same web build packaged as `playground-dist/`, in a webview preloaded with the active formula and catalog), "TinyExpression: Import catalog from playground export" (writes `.vscode/tinyexpression-catalog.override.json`, sets `catalog.overridePath`, restarts the server), a status bar item for the active catalog; relative `catalog.overridePath` values resolve against the workspace folder.
- `grammar/formula-info.ubnf`: the FormulaInfo block format as a UBNF v2 grammar (typed AST via `@mapping`), accepting exactly what `FormulaInfoSourceDocument.parse` accepts. `tinyexpression-rs` vendors its ubnfc Rust parser and gains a FormulaInfo loader (`formula_info::load`) plus `load` / `run` CLI subcommands; `tests/formula_info.rs` gates fields, load errors and evaluation against a golden taken from the Java loader (#180). The Java loader itself was unchanged by #180; see Fixed (#195) for the three bugs the parity check it added then found.
- Playground FormulaInfo editor (#212): the FormulaInfo panel is a CodeMirror 6 editor — key / comment / end-mark colouring with the tinyexpression colouring inside `formula:` values, completion (keys, `---END_OF_PART---`, `dependsOn` names, enum values, the formula editor's completion inside `formula:`), diagnostics (the Rust loader's error at its position, every `formula:` through `te_check` with the catalog's TE codes and hints, unknown keys, every unknown `dependsOn`) and hover. Both editors gain token colouring, depth-coloured brackets, unmatched-bracket marking and the pair at the cursor (code-point scanners, no regular expressions).
- Rust: a failed FormulaInfo `load` / `run` response carries `"span":[start,end]` (code points of the document) from `formula_info_span::load_error_span`: the parser's offset for syntax errors, else the failing block's entry (#212). Additive: accepted documents, error kinds and Java exception names are unchanged; Java's `FormulaInfoParseException` has an offset only for partially parsed documents.

### Fixed
- **FormulaInfo loader: three bugs in the hand-written Java loader (`org.unlaxer.tinyexpression.loader`), found by the UBNF-generated parity check of #180, now rejected with an explicit `FormulaInfoParseException` instead of the previous silent/incidental behaviour (#195). `FormulaInfo` is used by production systems (e.g. fraud-alert); a document that used to load (possibly missing formulas) or crash with an unrelated JDK exception now fails loudly and specifically. `formula_info::LoadError` on the Rust side (`tinyexpression-rs`, already explicit) is unchanged in shape; only `LoadError::java_exception()` was updated to `FormulaInfoParseException` to match.**
  - A document `FormulaInfoBlocksParser` cannot consume in full (e.g. an unparsable line after a complete block) — before: `FormulaInfoList.parse` silently returned only the blocks parsed before the first unparsable line (possibly zero), with no error; after: throws `FormulaInfoParseException` ("FormulaInfo document was only partially parsed at offset N").
  - An entry whose value is empty at the end of input (a trailing `key:` with nothing after it) — before: `NoSuchElementException` from the empty value token, with no indication of which key; after: throws `FormulaInfoParseException` ("'key:' has an empty value at the end of input").
  - `dependsOn` naming a calculator the document does not define — before: `NullPointerException` while wiring the dependency back onto the unresolved (`null`) calculator; after: throws `FormulaInfoParseException` ("<calculatorName> dependsOn unknown calculator '<name>'").
- **FormulaInfo: a malformed or missing `---END_OF_PART---` line no longer merges two blocks silently (#211).** A `---END_OF_PART---` line with something after the mark used to be read as a line of the previous entry's value, so the next block's `calculatorName:` / `formula:` became entries of the same block and overwrote it: a two-block file loaded as `[b]` only, with no error, and formula `a` was gone. Same change in the Java loader (`FormulaInfoList.parse`, `FormulaInfoSourceDocument.parse`), `grammar/formula-info.ubnf` (and the vendored ubnfc parser) and the Rust loader (`tinyexpression-rs` `formula_info`); fixtures `src/test/resources/formulaInfo-ubnf/{accept-26,accept-27,reject-syntax-12..15,reject-load-10,reject-load-11}-*.fi`.
  - `---END_OF_PART---` followed only by spaces / tabs, then a line break or the end of input — before: not an end mark, the blocks merged (`[b]`); after: an end mark, both blocks load (`[a, b]`). LF and CRLF alike.
  - `---END_OF_PART---` followed by any other character (`---END_OF_PART---xyz`, `---END_OF_PART--- x`, a VT/FF) — before: a value line, the blocks merged (`[b]`); after: `FormulaInfoParseException` ("line N: '---END_OF_PART---' must be followed only by spaces or tabs up to the end of the line, but found '…'"). This also applies inside a value: such a line can no longer be part of a `description:` or `formula:` text. Rust: `LoadError::Syntax`.
  - A block with `calculatorName` twice or more (typically: the end mark line between two FormulaInfo is missing) — before: the later values silently overwrote the earlier ones (`[b]`); after: `FormulaInfoParseException` ("calculatorName appears 2 times in one block ('a', 'b'); is the ---END_OF_PART--- line between two FormulaInfo missing?"). Rust: `LoadError::DuplicateCalculatorName`. Other repeated keys are unchanged (`tags` still accumulates, other single-valued keys are still last-wins). A missing end mark between two blocks where the second has no `calculatorName` is still not detectable.
  - Unchanged: the final block may still end at the end of input without an end mark; `#` / blank / tab-only lines between or after blocks, CRLF and lone CR are accepted as before; a document may not start with an end mark.
  - `FormulaInfoParseException` (new in 2.0.1) now extends `IllegalArgumentException`, so `FormulaInfoSourceDocument.parse` raises it within its documented `IllegalArgumentException` contract.

### Performance
- Test suite time (#200): `CalculatorImplTest` (49 tests, ~150 formulas shared by 6 backend subclasses — Ast/P4Ast/P4Dsl × default/classic engine, plus `JavaCodeCalculatorV3Test`) re-verified every formula against the classic legacy grammar (`testAllMatch(calculator.getParser(), formula)`, always `FormulaParser` regardless of backend) once per subclass. The classic grammar backtracks exponentially on the deep fraud-alert formulas, so this redundant re-parsing — not `javac`, contrary to the issue's working hypothesis — was the dominant, duplicated cost. A JVM-static, formula-keyed cache (`CalculatorImplTest.verifyClassicGrammarAccepts`) now verifies each distinct formula against the classic grammar at most once per `mvn test` run instead of up to 6 times; every formula is still asserted the first time any suite sees it, so coverage is unchanged. `P4DslJavaCodeCalculatorTest` alone: 112.8s → 18.9s on the same host. Test-only change; no compiler/evaluator/engine code touched. See `docs/reports/2026-09-25-test-time-200.md`.

## [2.0.0] - 2026-09-25 (Central publish pending owner confirmation, #176)

### Breaking
- **The ubnfc-generated P4 parser is the default engine** (#183). Every backend that parses with the P4 grammar — `AST_EVALUATOR`, `DSL_JAVA_CODE`, `P4_AST_EVALUATOR`, `P4_DSL_JAVA_CODE` — now goes through a dependency-free Java parser that [ubnfc](https://github.com/opaopa6969/ubnfc) generates from the same grammar (`tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf`). `P4PreferredAstMapper` keeps its public surface (four entry points, `ParsedAst`, candidate-name API, `ParseDeadlineExceededException`, failure types and messages) and returns the same AST records, `selectionMode` strings and code-point spans; `UbnfcParityTest` pins this over 350 inputs with zero differences against the fixed generator (for the published 3.0.15 generator see Fixed).
- **How to get the previous behaviour back (`classic`, alias `legacy`, removed in 3.0)**, most specific first:
  1. FormulaInfo block field `p4Engine:classic`, or `CalculatorCreatorRegistry.forBackend(backend, P4ParserEngine.CLASSIC)` / `P4ParserEngine.with(P4ParserEngine.CLASSIC, ...)`;
  2. JVM-wide escape hatch `-Dtinyexpression.p4.engine=classic`;
  3. default `ubnfc`.
  The id `legacy` is still accepted everywhere `classic` is, as a deprecated alias (logs a one-time warning). Unknown values fail instead of falling back. Calculators record the engine in the `_tinyP4ParserEngine` marker.
- **`classic` (alias `legacy`, removed in 3.0)** is deprecated and scheduled for removal in 3.0 (the unlaxer combinator path `TinyExpressionP4Parsers` + `TinyExpressionP4Mapper` behind `P4PreferredAstMapper`).
- `tinyexpression.p4.memoize` now only affects `classic` (ubnfc is always packrat). `tinyexpression.p4.parse.timeout.millis` still applies, but the ubnfc engine checks it before/after parsing only: it is packrat with a depth limit and cannot backtrack exponentially (#19, #20).

### Known issues
- `classic` engine only: `P4PackratFraudFormulaTest` fails (also on the pre-2.0.0 master `14af5ae2`, so not a regression of this release; green with the default `ubnfc` engine). Tracked for the `classic` removal in 3.0.
- CI verifies the vendored ubnfc parser by sha256 against `UBNFC_PIN`; the full grammar → IR → Java regeneration check needs read access to the private `ubnfc` repository and is run locally (report `docs/reports/2026-09-24-ubnfc-default-engine.md`).

### Fixed (visible when built against the published unlaxer-dsl 3.0.15, i.e. the release)
- The default engine does not inherit two AST bugs of the published 3.0.15 mapper generator that `classic` keeps: `import X as alias` no longer yields `method="alias", alias=""` (now `method=null, alias="alias"`), and `receiver.contains/startsWith/endsWith(p...)` no longer repeats the receiver as an extra first pattern. With the fixed generator (unlaxer-parser source pin) both engines already agree; `UbnfcParityTest` tolerates exactly these two shapes, and only when the published generator is on the classpath.

### Added
- `org.unlaxer.tinyexpression.p4.P4ParserEngine` (engine selection and precedence) and `CalculatorCreatorRegistry.forBackend(ExecutionBackend, P4ParserEngine)` / `withP4ParserEngine(...)`.
- Vendored generated parser under `org.unlaxer.tinyexpression.p4.ubnfc.generated` (22 files, JDK 21 only) plus its extern scanners (`P4Scanners`), the hand-maintained facade core `UbnfcP4Parse`, the machine-generated AST converter `UbnfcAstConverter` (86 records, 1:1) and `VariantShapes`, which adapts the 7 components whose declared type differs between the published 3.0.15 and the newer unlaxer-dsl generator (`External*InvocationExpr.className`, `SliceExpr.start/end/step`) at run time, so the same sources build against either. Pin: `src/main/java/org/unlaxer/tinyexpression/p4/ubnfc/UBNFC_PIN` (ubnfc commit, grammar/IR sha256, per-file sha256). `scripts/regenerate-ubnfc-parser.sh --check` verifies grammar and vendored files against the pin (CI) and, where the private ubnfc repository is readable, regenerates grammar → IR → Java byte-for-byte; `scripts/generate-ubnfc-converter.py --check` verifies the converter.
- Tests: `UbnfcParityTest` (classic vs ubnfc over 324 suite formulas + 16 ubnfc fixtures + 10 non-BMP inputs), `UbnfcDifferentialFuzzTest` (deterministic token-level mutants of the corpus; both engines must agree on accept/reject, AST and messages), `P4EngineModeMatrixTest` (FormulaInfo → parse → Java → javac → execute for 4 backends × 7 engine selections incl. non-BMP formulas and the `legacy` alias, and identical generated Java across engines), `P4ParserEngineTest` (incl. the `legacy` alias), and `P4DslJavaCodeCalculatorClassicEngineTest` / `P4AstEvaluatorCalculatorClassicEngineTest` (the full `CalculatorImplTest` suite under `-Dtinyexpression.p4.engine=classic`).

### Changed
- `P4PreferredAstMapperDiagnosticsTest` and the reflective helper in `P4SourceMappingTest` now target `ClassicP4PreferredAstMapper`: they pin internals (combinator root graphs, in-parse deadline listener) that only the `classic` engine has.
- `tinyexpression-p4-lsp` builds against tinyExpression 2.0.0. Its own syntax diagnostics still use the generated `TinyExpressionP4Mapper` directly (unchanged in 2.0); calls it makes through `P4PreferredAstMapper` use the default engine.
- Maven Central publication now defaults to bundle-only mode and requires the shared `org.unlaxer` monthly release guard to opt into upload. VSIX-only releases remain independent of the Central release train.
- No unlaxer upgrade is needed: 2.0.0 stays on unlaxer-common / unlaxer-dsl **3.0.15** (the ubnfc parser does not depend on unlaxer; `classic` and the `JAVA_CODE` backend keep using it). Java CI resolves the published 3.0.15 jars.

### Performance
Measured in ubnfc's facade report (`docs/reports/2026-09-24-te-facade.md` in ubnfc; same facade code, shared machine at load 15–18, so javac differences are noise). parse / Java generation / javac medians:

| Input | parse share classic | parse share ubnfc | parse+gen+javac classic | ubnfc |
|---|---:|---:|---:|---:|
| `valid-basic.tiny` | 1% | 0.3% | 57.1 ms | 31.4 ms |
| `complex.tiny` | 19% | 2.4% | 105.4 ms | 52.7 ms |
| `large-match.tiny` | 80% | 17.5% | 181.2 ms | 38.0 ms |
| fraud-alert #1 | 76% | 2.5% | 148.0 ms | 75.7 ms |
| fraud-alert #4 | 68% | 3.6% | 232.6 ms | 38.2 ms |
| fraud-alert #5 | 98% | 4.3% | 2179.3 ms | 33.4 ms |
| `complex-x64.tiny` (20 KB) | 60% | 50.0% | 2398.5 ms | 154.5 ms |

`FormulaInfoList.parse` of the five fraud-alert formulas (`backend:dsl-javacode`, load → Calculator): **2490.2 ms → 363.6 ms (6.8×)**. For small formulas javac dominates and parsing faster changes little; after the switch javac is the bottleneck on real formulas.

## [1.4.15] - 2026-08-27

### Fixed
- UBNF-generated P4 parsers now treat variable `description='...'` metadata as optional for number, string, boolean, and object declarations, matching the published DSL specification.
- TinyExpression P4 LSP/DAP 0.2.35 accepts declarations such as `var $base as float set if not exists 40;` without reporting the terminating semicolon as an error.

## [1.4.14] - 2026-08-26

### Added
- FormulaInfo documents can be debugged directly from VS Code. DAP selects a block by `calculatorName`, follows its `executionBackend` metadata, maps AST stack frames and breakpoints back to the original file, executes the document in dependency order, and shows per-formula results alongside editable `CalculationContext` inputs.
- The VSIX now recognizes `formulaInfo.txt`, `formula-info.txt`, and `*.formulainfo` automatically. Fenced Java blocks use embedded Java syntax highlighting; execution remains disabled unless `allowJavaCodeBlocks: true` is explicitly set for the isolated DAP process.
- Parser-backed `FormulaInfoSourceDocument` exposes source sections without constructing calculators or compiling Java, so editor source selection does not rely on an ad-hoc metadata scanner.
- TinyExpression `TE*` and FormulaInfo `FI*` diagnostics now carry schema-versioned `Diagnostic.data` for editor/LLM repair clients. `FI002` reports unknown execution backends and returns all six supported values.

### Changed
- Adopt unlaxer-dsl/common **3.0.14** and tinyexpression-p4-lsp **0.2.33** for the generated container-document DAP hook.
- FormulaInfo DAP evaluates the dependency graph exactly once, preventing duplicate Java or side-effect execution while still projecting the selected result into the standard Variables fields.

## [1.4.13] - 2026-08-26

### Changed
- Adopt unlaxer-dsl/common **3.0.13**. Generated LSP diagnostics now use the farthest failure position, stable code `ULX-PARSE-001`, actionable expected-value messages, and structured `Diagnostic.data` for LLM/editor repair clients.
- `tinyexpression-p4-lsp` is versioned as **0.2.32** and regenerates its LSP/DAP server from the 3.0.13 generator while preserving TinyExpression-specific `TE*` semantic diagnostics. The version follows the already deployed 0.2.31 extension so code-server upgrades instead of downgrading it.
- DAP launch variables are injected into the real `CalculationContext` as typed JSON values and can now be edited while stopped through the Variables view; runtime results and Debug Console evaluation refresh against the edited context.

### Fixed
- Invalid UBNF rule/token combinations that would resolve to the same generated parser class now fail generation with `E-RULE-TOKEN-NAME-COLLISION` instead of producing a recursively broken parser.
- Removed the remaining heuristic `P4ParseProbe` success marker. A failed generated P4 parse is now reported as `failed` and never presented as parser success based on a regular-expression guess.

## [1.4.12] - 2026-08-26

### Changed
- Restore the published Maven coordinate `org.unlaxer:tinyExpression` and migrate release publishing from the retired OSSRH service to Central Publisher Portal.
- Adopt unlaxer-dsl/common **3.0.12**, including the runnable generated DAP launcher and application runtime hook.
- `tinyexpression-p4-lsp` **0.2.4** uses standard `program` launch configuration, separates `runtimeMode` from `steppingMode`, and accepts typed JSON launch variables.

### Added
- **Usable VS Code DAP integration**: AST entry stop/step/stack/variables, selected P4 backend result, real `CalculationContext` inputs, all-six-backend parity, and Debug Console evaluation through TinyExpression itself.
- **Automated DAP integration coverage** for launch, AST stack, typed variables, P4 markers, parity, Debug Console, and the legacy `formulaSource` launch alias.
- **Opt-in packrat memoization for P4 parsing** (`-Dtinyexpression.p4.memoize=true`, wired in `P4PreferredAstMapper`). Off by default. Collapses the exponential backtracking of deeply nested fraud-detection formulas (#19/#38): the boolean/parenthesis-ambiguity formulas (#19 examples 1–4) parse in <0.5s instead of hitting the 10s parse deadline. Generated backends now report deadline/coverage failures explicitly rather than switching to a handwritten evaluator. Verified by `P4PackratFraudFormulaTest`.

### Fixed
- DAP parity flags now mean all six backends actually evaluated; `parity.equalAllWithP4` remains a compatibility alias of `parity.equalAll`.
- Unknown DAP `runtimeMode` values no longer silently select `JAVA_CODE`; blank mode defaults to generated `P4_AST_EVALUATOR`, while invalid mode returns an explicit diagnostic.
- Removed the Debug Console's handwritten regex substitution and recursive-descent arithmetic evaluator; evaluation now uses the selected TinyExpression backend.
- `P4TypedAstEvaluator`: declared variable types (`var $name as string …`) now make `$name == $other` a **string** comparison on the pure-AST path, instead of coercing both operands to boolean. Variable declarations carry `@declares` (not `@mapping`) so they are dropped from the generated AST; declared types are now threaded from `AstDeclarationRuntime` into the evaluator (mirrors the legacy `VariableTypeResolver`). Resolves the last pure-AST (if-source-shadow OFF) failure in `testTypeInference`. Consumer-only change — no unlaxer-dsl/codegen release. (#32 / handoff #44 "C")

## [1.4.11] - 2026-04-21

### Fixed
- `DSL_JAVA_CODE` / `P4_DSL_JAVA_CODE`: nested parenthesis multiplication bug resolved — `(10-2)*(7-3)` now correctly returns `32.0` across all 6 backends via `P4TypedJavaCodeEmitter`
- `P4BackendParityTest`: stale comment and missing coverage for `(10-2)*(7-3)` corrected; formula added to six-backend parity corpus

### Added
- `JavaCodeBlockPolicy`: Java code block (triple-backtick `` ```java:ClassName `` fence) execution is disabled by default and requires explicit `setEnabled(true)` opt-in for trusted formula authors.

## [1.4.10] - 2026-02-26

### Changed
- `P4TypedAstEvaluator` promoted to PRIMARY evaluator path — fallback chain is now safety net only
- DAP default runtime mode changed from `token` to `ast-evaluator`
- Migrated to unlaxer-common 2.8.0: `NoneChildCollectingParser` migration

### Fixed
- `SliceExpr` usage adapted to `Optional<BinaryExpr>` fields after P4 regeneration

## [1.4.9] - 2026-02-25

### Added
- String slice (`$msg[0:3]`) — last feature gap closed; full parity achieved across all 6 backends
- FormulaInfo LSP Phase 2 + `IncrementalParseCache` LSP integration
- String concatenation (`+`) operator
- `inTimeRange` / `inDayTimeRange` built-in functions

### Fixed
- Declaration expression extraction falls back correctly when token text is mangled

## [1.4.8] - 2026-02-24

### Added
- `MethodInvocation` + `External` invocations in `P4TypedAstEvaluator` — fallback eliminated
- Backend coverage matrix (`docs/backend-coverage-matrix.md`)
- Feature parity diff (`docs/feature-parity-diff.md`)

## [1.4.7] - 2026-02-23

### Added
- String predicates: `startsWith`, `endsWith`, `contains`, `isPresent`
- P4 fallback logging — visibility into which formulas still fall back
- LSP CodeAction: `if` ↔ ternary bidirectional conversion
- FormulaInfo LSP Phase 1: metadata completion, `dependsOn` validation

## [1.4.6] - 2026-02-22

### Added
- `ArgumentExpression` (no double parentheses)
- String dot-method chaining
- Ternary expression (`condition ? then : else`)
- String methods: `toUpperCase`, `toLowerCase`, `trim`, `length`

### Fixed
- 62 test fixes after ternary introduction

## [1.4.5] - 2026-02-21

### Added
- Math functions: `min`, `max` (variadic, 2+ arguments), `abs`, `floor`, `ceil`
- `not()` operator
- `toNum()` conversion function
- Boolean 3-level operator hierarchy: `|` (Or) < `&` (And) < `^` (Xor)
- Railroad diagrams auto-generated on `mvn compile` (91 SVGs)

## [1.4.4] - 2026-02-20

### Added
- `P4TypedAstEvaluator` as primary eval path
- Full-spec P4 Java code generation via `P4TypedJavaCodeEmitter`
- GGP concrete implementations for P4-typed AST evaluation and code generation
- `@eval` strategy design (default and template Java code emitters)

## [1.4.3] - 2026-02-19

### Added
- P4 grammar: `P4_AST_EVALUATOR` and `P4_DSL_JAVA_CODE` backends registered
- UBNF-generated type-safe parser, AST (sealed interface), Mapper, Evaluator chain

### Fixed
- Resolved 197 compile errors + 15 test failures after P4 regeneration (409 tests, 5 remaining)

## [1.4.2] - 2026-02-18

### Added
- LSP/DAP server improvements
- Grammar improvements and updates to VS Code extension

### Fixed
- AST evaluator for declarations and embedded expressions

## [1.4.1] - 2026-02-17

### Added
- `ParseFailureDiagnostics` infrastructure for type-safe LSP/DAP

## [1.4.0] - 2026-02-14

### Added
- `LegacyAstCreatorJavaCodeCalculator` — pre-refactor comparison baseline (`JAVA_CODE_LEGACY_ASTCREATOR`)
- `AstEvaluatorCalculator` — AST traversal backend (`AST_EVALUATOR`)
- `DslJavaCodeCalculator` — DSL JavaCode seam (`DSL_JAVA_CODE`)
- 4-backend parity test suite

## [1.3.0] - 2025-12-01

### Added
- `JavaCodeCalculatorV3` — current production JavaCode baseline (`JAVA_CODE`)
- `TinyExpressionsExecutor` — multi-formula dependency-ordered execution
- `FileBaseTinyExpressionInstancesCache` — file-based formula cache
- `FormulaInfo` parser with `---END_OF_PART---` block format
- `ResultConsumer` interface for pluggable result handling
- `CalculatorCreatorRegistry` — backend enum to creator mapping

## [1.2.0] - 2025-09-01

### Added
- `FormulaInfoAdditionalFields` — pluggable name resolver and global backend default
- Java code block embedding in `formula` field (triple-backtick syntax)
- External Java method import (`import pkg.Class#method as alias`)

## [1.1.0] - 2025-06-01

### Added
- `match` expression (pattern matching with `default` branch)
- User-defined methods (`float main(){...}` + `call methodName()`)
- Variable declaration (`variable $name as type set defaultValue`)

## [1.0.0] - 2025-03-01

### Added
- Initial public release
- `if`/`else` conditional expression
- Arithmetic operators: `+`, `-`, `*`, `/`
- Comparison operators: `==`, `!=`, `>`, `>=`, `<`, `<=`
- Boolean operators: `&`, `|`, `^`, `not()`
- String utilities: `toUpperCase`, `toLowerCase`, `.startsWith`, `.endsWith`, `.contains`
- `CalculationContext` — thread-safe context for variable binding
- `ExpressionTypes` enum — full numeric type ladder (`byte` through `double`, `BigDecimal`, `BigInteger`)
- Maven Central publication: `org.unlaxer:tinyExpression`
