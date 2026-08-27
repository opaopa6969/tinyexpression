# Changelog

All notable changes to TinyExpression are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Changed
- Maven Central publication now defaults to bundle-only mode and requires the shared `org.unlaxer` monthly release guard to opt into upload. VSIX-only releases remain independent of the Central release train.

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
