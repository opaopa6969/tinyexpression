# TinyExpression Final Gap Audit (DSL Replacement)

Last updated: 2026-02-25

## Target

1. TinyExpression parser/AST/runtime/tooling path is fully replaceable by `unlaxer-dsl` generated artifacts.
2. `JAVA_CODE` and `AST_EVALUATOR` both remain available.
3. Final observable behavior is equivalent, and LSP/DAP operate in both runtime modes.

## Done

1. Generated runtime/tooling split is established and integrated into build.
2. `AST_EVALUATOR` runtime is wired with staged execution:
   1. `generated-ast`
   2. `token-ast`
   3. `javacode-fallback`
3. Generated DAP `runtimeMode=ast` has AST node observability and AST-span-based source location.
4. Generated mapper compatibility shims are in place for current `unlaxer-common`.
5. Numeric AST path has basic parity and variable leaf resolution from `CalculationContext`.
6. Object generated-path evaluation now covers variable reference and declaration setter defaulting (`if not exists`) in representative formulas.
7. `unlaxer-dsl` capture-type inference now degrades heterogeneous captures to `Object`, preventing over-constrained generated AST fields in mixed alternatives.

## Remaining Gaps

1. AST coverage beyond numeric binary core:
   1. `StringExpression`, `BooleanExpression`, `ObjectExpression` の複合式（method/match/if）
   2. `IfExpression`, `match` families
   3. method invocation and declaration semantics in AST runtime
2. Declaration semantics in generated AST runtime:
   1. variable declaration setters/defaulting are basic object pathで実装済みだが、number/string/booleanの網羅と複合式評価は未完
   2. dependency on fallback remains for declaration-heavy formulas
3. Root mapping semantics for mixed grammars:
   1. improved heuristic + object fallback evaluator is in place, but explicit semantic root mapping is not guaranteed for all patterns
4. Full parity harness:
   1. current parity tests cover representative numeric slices
   2. systematic formula corpus parity (`JAVA_CODE` vs `AST_EVALUATOR`) is not complete
5. DAP dual-runtime execution integration:
   1. `runtimeMode` AST stepping/coordinates are implemented
   2. evaluator-value-level stepping parity between JavaCode/AST runtime is not complete

## Dependency-Side Needs (Potential Future)

1. `unlaxer-dsl`:
   1. richer mapping metadata generation for non-numeric expression families
   2. optional explicit root-mapping policy support for multi-expression roots
2. `unlaxer-common`:
   1. no blocking API gap currently for latest implemented slices
   2. future span/token APIs may simplify mapper heuristics and DAP binding

## Recommended Implementation Order

1. Expand generated mapper annotations/rules to include non-numeric expression families.
2. Implement generated AST evaluator adapters for string/boolean/object and control-flow nodes.
3. Add declaration/method runtime semantics in AST evaluator path.
4. Build corpus-based parity runner and promote failing categories incrementally.
5. Finalize DAP runtime parity checkpoints for evaluator-level behavior.
