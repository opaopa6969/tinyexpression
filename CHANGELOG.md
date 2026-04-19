# Changelog

All notable changes to TinyExpression are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
