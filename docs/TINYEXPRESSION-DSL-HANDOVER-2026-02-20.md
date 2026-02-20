# TinyExpression DSL Handover (2026-02-20)

## Purpose

Context handover after context-limit stop, so the next session can continue implementation without re-discovery.

## Scope Completed This Round

Primary focus was Roadmap `P2: Error diagnosability hardening (javacode)`.

Implemented:

1. replaced bare `IllegalArgumentException()` with contextual messages in key paths,
2. kept behavior intact (message improvement only),
3. added minimal roadmap tests (`1 fail + 1 success`) for diagnosability.

## Files Changed

### Production

1. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/BinaryConditionBuilder.java`
2. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/StringMethodClauseBuilder.java`
3. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/ParametersBuilder.java`
4. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/SideEffectStringExpressionBuilder.java`
5. `src/main/java/org/unlaxer/tinyexpression/parser/ExpressionType.java`
6. `src/main/java/org/unlaxer/tinyexpression/parser/JavaClassMethodParser.java`
7. `src/main/java/org/unlaxer/tinyexpression/parser/javalang/VariableDeclarationsParser.java`
8. `src/main/java/org/unlaxer/tinyexpression/parser/javalang/VariableDeclarationParser.java`
9. `src/main/java/org/unlaxer/tinyexpression/PreConstructedCalculator.java`
10. `src/main/java/org/unlaxer/tinyexpression/PreConstructedCalculator_.java`

### Test

1. `src/test/java/org/unlaxer/tinyexpression/roadmap/ErrorDiagnosabilityRoadmapTest.java`

### Docs

1. `docs/TINYEXPRESSION-DSL-ROADMAP.md` (progress snapshot added)

## Test Execution Result

Executed and passed:

1. `./mvnw -q -Dtest=ErrorDiagnosabilityRoadmapTest test`
2. `./mvnw -q -Dtest=ParserDispatchTest,TinyExpressionTokensTest test`

Not executed in this round:

1. full test suite.

## Current Backlog Status

1. `P2`: partially done.
2. `P1`: not started.
3. `P3`: not started (`TypeSystemRoadmapTest` has `@Ignore`).
4. `P4`: not started.

## Recommended Next Step (Immediate)

Continue with `P1` minimum viable path in this order:

1. define minimal annotation contract for number binary expression slice,
2. annotate the narrow parser slice,
3. generate/prepare AST model for the slice,
4. add adapter entry in `NumberExpressionBuilder`,
5. add exactly two focused tests (`1 fail + 1 success`).

## Guardrails / Collaboration Rules

1. `unlaxer-common` and `unlaxer-dsl` can be read freely.
2. if changes to `unlaxer-common` or `unlaxer-dsl` are required, ask user permission before editing.
3. prefer implementation-first progress, then minimal tests, per roadmap execution policy.
