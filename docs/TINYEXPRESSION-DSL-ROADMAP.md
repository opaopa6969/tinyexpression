# TinyExpression DSL Rewrite Roadmap

This document captures the current redesign plan discussed in 2026-02.

## Goal

Refactor TinyExpression toward a cleaner architecture where:

1. parser definition is easier to maintain,
2. AST/model generation is explicit,
3. type handling (number/boolean/string/javaType) is consistent,
4. future UBNF + annotation integration can be adopted incrementally.

## Why Two-Stage Rewrite

Direct full replacement is high-risk because TinyExpression has runtime-specific behavior
that is not only grammar-level (scope store, transaction hooks, parser replacement on commit).

So we intentionally use two stages.

## Stage 1: Stabilize + Annotation-First

Base branch: `feat-java21-from-v1.4.10`

Scope:

1. keep behavior stable on the v1.4.10 baseline,
2. migrate to Java 21 baseline,
3. add roadmap tests and anchor points,
4. introduce annotation-oriented metadata on existing parser definitions first.

Expected output:

1. stable parser/evaluator behavior,
2. explicit mapping points from parser -> AST/model,
3. minimum disruption for production users.

## Stage 2: UBNF Extension + Gradual Merge

After Stage 1 hardening, merge with unlaxer-dsl capabilities.

BNF-level features (recognition semantics):

1. interleave,
2. backreference,
3. scope/context guard.

Annotation-level features (semantic/tooling contract):

1. scope events (declare/use/resolve metadata),
2. profile-style readability APIs such as `@whitespace: javaStyle`,
3. diagnostics and codegen policies.

Rule:

1. Parse-time truth -> BNF extension,
2. Post-parse meaning -> annotation.

## Mapping to Historical WIP Themes

Historical WIP around `v1.4.10..master` appears to target these streams:

1. package restructuring by domain (`booltype`, `numbertype`, `stringtype`, `javatype`),
2. AST mapping/model redesign,
3. unified numeric type handling (short .. BigDecimal),
4. config/runtime separation.

These are valid directions; we will re-introduce them in controlled increments.

## Roadmap Tracks (Current)

### Track A: AST/Model Refactor (current main focus)

1. establish parser-to-model mapping seams,
2. reduce large builder complexity using model/AST steps,
3. keep compatibility test-first.

### Track B: Unified Numeric Types (short .. BigDecimal)

1. unify numeric expression flow across parser/AST/codegen/runtime,
2. keep literal suffix/cast/wrap semantics explicit,
3. only promote after compatibility tests are ready.

Reference placeholder test:

- `src/test/java/org/unlaxer/tinyexpression/roadmap/TypeSystemRoadmapTest.java`
  - `testUnifiedNumericTypeRoadmapFromShortToBigDecimal` (`@Ignore`)

### Track C: javaType as the 4th type family

1. treat javaType as first-class type family beside number/boolean/string,
2. define parser + inference + codegen contract explicitly,
3. keep external/java interop behavior deterministic.

Reference placeholder test:

- `src/test/java/org/unlaxer/tinyexpression/roadmap/TypeSystemRoadmapTest.java`
  - `testJavaTypeAsFourthTypeRoadmap` (`@Ignore`)

### Track D: Runtime/Config separation

1. isolate parsing configuration from runtime execution context,
2. isolate code-building configuration from parser definitions,
3. reduce hidden coupling in evaluators.

### Track E: Annotation-Driven AST Generation (new)

Purpose:

1. reduce hand-written AST mapper cost,
2. keep existing parser definitions while adding generation metadata,
3. create a migration bridge from parser-token world to generated AST world.

Scope:

1. add annotation metadata to existing parser definitions (TinyExpression side),
2. implement an annotation-reader pipeline that generates AST/model classes,
3. provide adapter points so existing builders can consume generated AST incrementally,
4. verify one end-to-end path: annotated parser -> generated AST -> existing evaluator.

Non-goal (for Stage 1):

1. full parser replacement with UBNF grammar.

## Rebuilt Task Backlog (Priority Order)

### P0: In-flight stabilization

1. finalize current `TinyExpressionTokens` input validation change and push,
2. keep focused regression tests green for `javacode` core path.

### P1: Annotation-AST minimum viable path

1. define minimal annotation contract for AST generation (node kind, field mapping, operator metadata),
2. annotate a narrow grammar slice (number binary expression path),
3. generate AST/model for that slice,
4. add adapter so `NumberExpressionBuilder` can accept generated AST nodes,
5. add minimal tests (`1 fail + 1 success`) for generated path.

### P2: Error diagnosability hardening (javacode)

1. replace bare `IllegalArgumentException()` with contextual messages,
2. cover `OperatorOperandTreeCreator` remaining bare-throw points,
3. keep tests focused to prevent regressions in failure diagnostics.

### P3: Type roadmap activation

1. remove one `@Ignore` by implementing first concrete flow for unified numeric types,
2. define first concrete javaType flow (declaration + inference + codegen).

### P4: UBNF extension design merge

1. map TinyExpression requirements into BNF-level vs annotation-level decisions,
2. draft shared spec for tinyexpression-codex collaboration,
3. validate `interleave`, `backreference`, and `scope tree` semantics against real parser cases.

## Progress Snapshot (2026-02-20)

Status:

1. P0: partially done (TinyExpressionTokens side is stabilized in current branch context),
2. P1: done (minimum viable path for number binary expression),
3. P2: done (diagnosability hardening including OperatorOperandTreeCreator fallback path),
4. P3: partially done (both roadmap tests activated with first concrete flows),
5. P4: in progress (design merge + first semantic validation checkpoints completed).

Completed in previous session (P2):

1. replaced bare `IllegalArgumentException()` with contextual messages across key `javacode` and parser paths,
2. added focused roadmap tests for diagnosability:
   - `src/test/java/org/unlaxer/tinyexpression/roadmap/ErrorDiagnosabilityRoadmapTest.java`
3. verified focused tests:
   - `./mvnw -q -Dtest=ErrorDiagnosabilityRoadmapTest test`
   - `./mvnw -q -Dtest=ParserDispatchTest,TinyExpressionTokensTest test`

Remaining P2 items:

1. monitor future parser-family additions and keep unsupported-path tests in sync.

Completed in this session (P1):

1. defined minimal annotation contract:
   - `TinyAstNode` (node kind)
   - `TinyAstField` (field mapping)
   - `TinyAstOperator` (operator metadata)
2. annotated narrow grammar slice:
   - `PlusParser`, `MinusParser`, `MultipleParser`, `DivisionParser`, `NumberParser`
3. implemented annotation-reader pipeline and generated number AST model:
   - `NumberGeneratedAstAdapter`
   - `NumberGeneratedAstNode` / `NumberGeneratedBinaryAstNode` / `NumberGeneratedLiteralAstNode`
4. added adapter path in `NumberExpressionBuilder` to consume generated AST incrementally with fallback to existing token path,
5. added minimal tests (`1 fail + 1 success`):
   - `src/test/java/org/unlaxer/tinyexpression/evaluator/javacode/NumberGeneratedAstAdapterTest.java`

Verified tests:

1. `./mvnw -q -Dtest=NumberGeneratedAstAdapterTest,ExpressionBuilderTest test`
2. `./mvnw -q -Dtest=ErrorDiagnosabilityRoadmapTest,NumberGeneratedAstAdapterTest,ParserDispatchTest,TinyExpressionTokensTest test`

Completed in this session (P3 first step):

1. removed one `@Ignore` from
   - `src/test/java/org/unlaxer/tinyexpression/roadmap/TypeSystemRoadmapTest.java`
2. implemented first concrete unified numeric flow (`parser -> AST -> codegen`) for primitive numeric families:
   - `_short`, `_byte`, `_int`, `_long`, `_float`, `_double`
3. kept `javaType` roadmap test ignored (still pending as planned).

Completed in this session (P3 follow-up):

1. extended unified numeric codegen flow for `bigInteger` / `bigDecimal`:
   - literal generation: `new java.math.BigInteger("...")` / `new java.math.BigDecimal("...")`
   - binary operations: `.add/.subtract/.multiply/.divide`
2. added one failure-path test for invalid bigInteger literal (`1.5`),
3. activated `javaType` roadmap test with first concrete declaration + codegen flow (`resultType=object`),
4. fixed `GeneralJavaClassCreator` object-result branch so expression body is emitted for object return type.
5. hardened `bigDecimal` division codegen to use `calculateContext.scale()` and `calculateContext.roundingMode()`.
6. added runtime verification for bigDecimal division (`1/3`) to ensure evaluation obeys context scale/rounding and does not fail.
7. enabled first object variable declaration/inference path:
   - `var $payload description='...'; $payload`
   - resolved as object declaration and emitted `calculateContext.getObject(..., Object.class)` in object result flow.
8. added object setter support for naked variable declaration:
   - `var $payload set if not exists 'fallback' description='...'; $payload`
   - mapped to `getObject(...).orElse(...)` or `setAndGetObject(...)` depending on `if not exists`.
9. aligned parser coverage for naked variable declaration with new object setter behavior.
10. expanded javaType/object flow for object method parameters and object variable expression:
   - added object type hint prefix/suffix parsers and object variable parser family,
   - added `ObjectVariableMethodParameterParser` and wired it into method parameter parsing,
   - updated object expression codegen to resolve local method parameters before context lookup,
   - validated object pass-through method flow:
     - `call identity('payload')`
     - `object identity($payload as object){ $payload }`
11. completed remaining P2 item for `OperatorOperandTreeCreator` fallback diagnosability:
   - changed final fallback throw in `apply` from bare message to contextual unsupported-parser message,
   - added targeted failure-path test using unsupported parser token to assert owner/parser/path details.
12. started P4 UBNF extension design merge:
   - created draft UBNF artifact with `@interleave`, `@backref`, `@scopeTree`:
     - `docs/ubnf/tinyexpression-p4-draft.ubnf`
   - validated draft with `unlaxer-dsl` validate-only (`ok=true`),
   - added shared collaboration spec:
     - `docs/TINYEXPRESSION-P4-UBNF-EXTENSION-SPEC.md`
   - added roadmap semantic snapshot tests:
     - `src/test/java/org/unlaxer/tinyexpression/roadmap/UbnfExtensionRoadmapTest.java`
13. improved scope-tree semantics on runtime path:
   - method parameter resolution is now prioritized in `VariableBuilder` via `VariableTypeResolver.resolveFromMethodParameter(...)`,
   - added lexical shadowing roadmap assertion (`global $amount` vs method parameter `$amount`) in `UbnfExtensionRoadmapTest`.
14. added dependency-extension memo for future `unlaxer-dsl` / `unlaxer-common` changes:
   - `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md`
   - recorded current P4 associativity/precedence validator gap and workaround.
15. added P4 associativity/precedence minimal repro artifact:
   - `docs/ubnf/tinyexpression-p4-assoc-repro.ubnf`
   - validate-only confirms reproducible failure codes for future dependency-side fix.

Verified tests:

1. `./mvnw -q -Dtest=TypeSystemRoadmapTest test`
2. `./mvnw -q -Dtest=TypeSystemRoadmapTest,NumberGeneratedAstAdapterTest,ErrorDiagnosabilityRoadmapTest test`
3. `./mvnw -q -Dtest=TypeSystemRoadmapTest,NumberGeneratedAstAdapterTest,ErrorDiagnosabilityRoadmapTest,ExpressionBuilderTest,SimpleUDFTest test`
4. `./mvnw -q -Dtest=MethodsParserTest#testObjectMethodWithObjectParameter test`
5. `./mvnw -q -Dtest=TypeSystemRoadmapTest,SimpleUDFTest,NumberGeneratedAstAdapterTest,ErrorDiagnosabilityRoadmapTest,ExpressionBuilderTest,StringVariableDeclarationParserTest test`
6. `./mvnw -q -Dtest=UbnfExtensionRoadmapTest test`
7. `./mvnw -q -Dtest=TypeSystemRoadmapTest,ErrorDiagnosabilityRoadmapTest test`
8. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests compile`
9. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args=\"--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.ubnf --validate-only --report-format json\"`

## Execution Policy

Priority order:

1. implementation first,
2. adjust existing tests as needed,
3. at most two new tests per focused change (`1 failure reproduction + 1 success`).

Avoid:

1. shipping only schema tightening,
2. adding large negative fixtures before implementation progress.

Done criteria per feature:

1. implementation behavior exists,
2. tests are minimal and purposeful,
3. change summary starts with implementation delta.
