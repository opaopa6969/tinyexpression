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
8. Generated mapper now supports preferred root selection (`parse(source, preferredAstSimpleName)`), and AST runtime passes preferred node kind by result type.
9. Generated value runtime now has an embedded-expression bridge for complex text payloads (`match` / `if` / `call`) in `StringExpr` / `BooleanExpr` / `ObjectExpr`:
   1. expression-like text is evaluated via `JavaCodeCalculatorV3`,
   2. unresolved expression-like text is rejected (no raw DSL snippet return),
   3. generated-path tests now cover string `match` and `call + method declaration`.
10. Backend parity checkpoint now includes representative non-number formulas (`string/boolean match`, `call + method declaration`) and verifies AST backend avoids `javacode-fallback` in these slices.
11. Numeric control-flow (`if(...)`) now also executes through generated runtime path via the embedded-expression bridge when direct generated-number AST evaluation is not available.
12. P4 draft boolean grammar now accepts numeric comparison expressions (`NumberExpression CompareOp NumberExpression`), enabling generated parser path for cases like `match{1==1->...,default->...}`.
13. Comparison expressions now have explicit AST node mapping (`ComparisonExpr`) and are evaluated directly in generated AST runtime (bridge非依存) for boolean comparison semantics.
14. Embedded-expression detection now follows java-style delimiter assumptions (`if`/`match` head with variable whitespace/comments), reducing heuristic mismatch vs parser behavior.
15. `MethodInvocation` now has explicit AST mapping (`MethodInvocationExpr`) and zero-arg method declarations can be resolved/evaluated on generated AST path without mandatory JavaCode fallback.
16. Parity harness now includes a curated mixed-expression corpus (`AstEvaluatorParityCorpusTest`) with runtime fallback guard for the supported slice.
17. Generated AST runtime now supports method invocation with arguments:
   1. `MethodInvocationExpr` top-level root evaluation is supported for `number/string/boolean/object` result paths.
   2. invocation argument expressions are resolved and bound to method parameters via scoped `CalculationContext` overlay before evaluating method body.
18. Execution backend model is now explicitly 3-way:
   1. `JAVA_CODE` (legacy hand-written JavaCode pipeline)
   2. `AST_EVALUATOR` (generated AST walk + token-ast/java fallback)
   3. `DSL_JAVA_CODE` (new backend slot; current implementation bridges to existing JavaCode runtime with dedicated backend identity marker)
19. Added 3-way backend parity harness (`ThreeExecutionBackendParityTest`) over a supported mixed corpus, asserting value parity and AST non-fallback guard.
20. `if` expression path now has explicit AST mapping and direct evaluator dispatch:
   1. `IfExpr` + `ExpressionExpr` are generated from P4 mapping.
   2. generated runtime evaluates condition and selected branch via AST recursion (bridge-first text evaluation is no longer required for the covered `if` slice).
21. `match` families now have explicit AST mapping and direct evaluator dispatch:
   1. `NumberMatchExpr` / `StringMatchExpr` / `BooleanMatchExpr` and case/default/value nodes are generated.
   2. generated runtime executes condition dispatch + selected branch evaluation directly on AST for number/string/boolean paths (and object path where these expressions are selected as root).
22. Declaration runtime preferred-root policy now includes structured expression heads (`if`/`match`/`call`) so setter expressions can reach generated AST direct paths before bridge fallback.
23. Formula loader now supports per-formula backend selection (`executionBackend` / `backend`) and validates backend names before calculator construction.
24. All three backends now publish unified runtime marker metadata (`_tinyExecutionBackend`, `_tinyExecutionMode`, `_tinyExecutionImplementation`, bridge flags), enabling tooling/diagnostics to distinguish bridge vs non-bridge execution.
25. Three-backend parity corpus coverage has been expanded to broader mixed formulas (method-args and declaration-heavy slices included).

## Remaining Gaps

1. AST coverage beyond numeric binary core:
   1. `if` / `match` は専用ASTノード + direct eval 対応済み。
   2. `method invocation/declaration` は引数束縛まで direct化済みだが、宣言・複合式全体で bridge/fallback 非依存にするには未完。
   3. `ObjectExpression` 複合式の一部は still bridge 依存（特に declaration-heavy / nested complex slices）。
2. Declaration semantics in generated AST runtime:
   1. variable declaration setters/defaulting は複合式まで拡張済み（if/match/call を含む preferred-root AST evaluation + method declaration text injection）
   2. declaration-heavy formulas の pure generated-ast-only 実行（bridge/fallback不要化）は未完
3. Root mapping semantics for mixed grammars:
   1. preferred-root API is available and runtime-connected, but full semantic root policy across declaration/method-heavy formulas is not yet formalized
4. Full parity harness:
   1. representative mixed corpus parity is available, but still curated/small
   2. systematic large formula corpus parity (`JAVA_CODE` vs `AST_EVALUATOR`) is not complete
5. DAP dual-runtime execution integration:
   1. `runtimeMode` AST stepping/coordinates are implemented
   2. evaluator-value-level stepping parity between JavaCode/AST runtime is not complete
6. Full DSL-native Java code generation backend:
   1. `DSL_JAVA_CODE` execution backend is present as integration seam
   2. backend currently reuses legacy JavaCode compiler path (bridge mode), but selection/metadata path is now production-wirable via formula metadata
   3. dedicated DSL-generated Java emitter + runtime parity at scale are not complete

## Dependency-Side Needs (Potential Future)

1. `unlaxer-dsl`:
   1. richer mapping metadata generation for non-numeric expression families
   2. optional explicit root-mapping policy support for multi-expression roots
2. `unlaxer-common`:
   1. no blocking API gap currently for latest implemented slices
   2. future span/token APIs may simplify mapper heuristics and DAP binding

## Recommended Implementation Order

1. Expand generated mapper/AST definitions to explicit control-flow/method nodes (reduce textual payload dependence).
2. Replace embedded-expression bridge paths with pure AST evaluator dispatch for method/match/if.
3. Build corpus-based parity runner and promote failing categories incrementally.
4. Finalize DAP runtime parity checkpoints for evaluator-level behavior.
