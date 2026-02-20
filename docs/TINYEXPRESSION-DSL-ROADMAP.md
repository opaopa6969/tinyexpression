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
