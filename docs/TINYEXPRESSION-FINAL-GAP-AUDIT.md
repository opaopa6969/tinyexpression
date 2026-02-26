# TinyExpression Final Gap Audit (DSL Replacement)

Last updated: 2026-02-26

## Target

1. TinyExpression parser/AST/runtime/tooling path is fully replaceable by `unlaxer-dsl` generated artifacts.
2. backend families (`JAVA_CODE` / `JAVA_CODE_LEGACY_ASTCREATOR` / `AST_EVALUATOR` / `DSL_JAVA_CODE`) remain selectable.
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
18. Execution backend model is now explicitly 4-way:
   1. `JAVA_CODE` (legacy hand-written JavaCode pipeline)
   2. `JAVA_CODE_LEGACY_ASTCREATOR` (pre-refactor OOTC reconstruction path for comparison/compatibility checks)
   3. `AST_EVALUATOR` (generated AST walk + token-ast/java fallback)
   4. `DSL_JAVA_CODE` (dedicated `DslJavaCodeCalculator` seam; current implementation is bridge-only and delegates to legacy JavaCode runtime while keeping dedicated backend identity markers)
19. Added backend parity harness (`ThreeExecutionBackendParityTest`) over a supported mixed corpus, asserting value parity and AST non-fallback guard.
20. `if` expression path now has explicit AST mapping and direct evaluator dispatch:
   1. `IfExpr` + `ExpressionExpr` are generated from P4 mapping.
   2. generated runtime evaluates condition and selected branch via AST recursion (bridge-first text evaluation is no longer required for the covered `if` slice).
21. `match` families now have explicit AST mapping and direct evaluator dispatch:
   1. `NumberMatchExpr` / `StringMatchExpr` / `BooleanMatchExpr` and case/default/value nodes are generated.
   2. generated runtime executes condition dispatch + selected branch evaluation directly on AST for number/string/boolean paths (and object path where these expressions are selected as root).
22. Declaration runtime preferred-root policy now includes structured expression heads (`if`/`match`/`call`) so setter expressions can reach generated AST direct paths before bridge fallback.
23. Formula loader now supports per-formula backend selection (`executionBackend` / `backend`) and validates backend names before calculator construction.
24. All selectable backends now publish unified runtime marker metadata (`_tinyExecutionBackend`, `_tinyExecutionMode`, `_tinyExecutionImplementation`, bridge flags), enabling tooling/diagnostics to distinguish bridge vs non-bridge execution.
25. Backend parity corpus coverage has been expanded to broader mixed formulas (method-args and declaration-heavy slices included).
26. Generated DAP adapter now imports tinyexpression runtime probe variables through an optional reflection bridge (`TinyExpressionDapRuntimeBridge`), exposing selected backend and execution markers in DAP variables for `runtimeMode=token/ast/dsl-javacode`.
27. Backend parity test now uses two-tier verification:
   1. supported corpus: `AST_EVALUATOR` non-fallback required.
   2. regression corpus: fallback allowed but value parity with `JAVA_CODE` / `DSL_JAVA_CODE` required across broader legacy-style formulas.
28. Added extracted legacy-corpus parity check:
   1. formulas are auto-extracted from `CalculatorImplTest` literal invocations,
   2. context-light subset is run across `JAVA_CODE` / `AST_EVALUATOR` / `DSL_JAVA_CODE`,
   3. value parity is enforced with executed-case lower bound for stable regression signal.
29. Generated value evaluator now performs structured-text AST re-entry before embedded bridge fallback for `StringExpr` / `BooleanExpr` / `ObjectExpr`, reducing bridge-only execution when generated mapper can parse expression-like payload text.
30. `AstEvaluatorCalculator` now has a token-ast literal/variable fast path for generated-runtime-unavailable cases (single/double-quoted strings, booleans, variables, simple numbers), so trivial formulas do not require `javacode-fallback` (verified with generated-class-blocking classloader tests).
31. AST preferred-root selection now uses shared structured-head predicates (`if`/`match`/`call` with java-style delimiters/comments) across evaluator and declaration runtime; comment-prefixed control-flow formulas are covered in regression parity corpus.
32. Added Java source parity test between `JAVA_CODE` and `DSL_JAVA_CODE` backends (`DslJavaCodeGenerationParityTest`) over curated mixed formulas; normalized generated source text is now regression-checked for equivalence.
33. Generated mapper probe now retries after trimming leading java delimiters/comments, improving AST mapping hit rate for comment-prefixed formulas (supported non-fallback parity now includes leading-comment `if` head case).
34. Generated mapper probe now also normalizes comment-delimited `if/*...*/(` head form for retry parse, and supported parity corpus verifies this form on non-fallback AST runtime.
35. DAP runtime bridge mode aliases are regression-tested (`dsl-javacode`/`dsl_java_code`/`ast`/`token`) to keep backend selection deterministic for adapter launch configurations.
36. Declaration-aware shortcut is now wired before `javacode-fallback`: declaration formulas can be evaluated via `AstDeclarationRuntime.tryEvaluateMainExpression(...)` even when generated runtime is unavailable (comment-free scope), reducing bridge fallback for this slice.
37. DSL JavaCode seam Java-source parity now includes extracted legacy corpus check (`DslJavaCodeGenerationExtractedParityTest`) in addition to curated parity, strengthening “same generated Java program” regression coverage.
38. Extracted parity harness now includes an AST non-fallback minimum threshold, so large-corpus checks catch regressions where AST backend silently drifts back to `javacode-fallback`.
39. Declaration shortcut now accepts leading-comment formulas by trimming leading java delimiters before `var` detection, while keeping internal-comment guard to avoid behavior drift.
40. AST evaluator declaration-shortcut trigger no longer uses literal `"var"` keyword checks; it now relies on separator-based pre-check and parser-driven declaration extraction in `AstDeclarationRuntime`.
41. Added 4th selectable backend for pre-refactor OOTC runtime:
   1. `ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR` is available from formula metadata/runtime mode alias (`legacy-astcreator`/`ootc`).
   2. runtime classes are split under:
      - `org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyOperatorOperandTreeCreator`
      - `org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyAstCreatorJavaCodeCalculator`
   3. parity harness now validates 4-way value equality (`JAVA_CODE` / `JAVA_CODE_LEGACY_ASTCREATOR` / `AST_EVALUATOR` / `DSL_JAVA_CODE`) on supported and extracted corpora.
42. Java-style delimiter/comment handling in AST runtime helpers is now centralized:
   1. added `JavaStyleSourceProbe` for shared delimiter skip/trim and structured-head normalization helpers,
   2. removed duplicated delimiter/comment scanners from `GeneratedAstRuntimeProbe` / `AstDeclarationRuntime` / `GeneratedP4ValueAstEvaluator`,
   3. `AstEmbeddedExpressionRuntime` structured-head checks now use shared delimiter-aware helper instead of local regex-only head checks.

## Remaining Gaps

1. AST coverage beyond numeric binary core:
   1. `if` / `match` は専用ASTノード + direct eval 対応済み。
   2. `method invocation/declaration` は引数束縛まで direct化済みだが、宣言・複合式全体で bridge/fallback 非依存にするには未完。
   3. `ObjectExpression` 複合式の一部は still bridge 依存（structured-text/method body/method argument の multi-root AST retry で縮小済み、`_astEvaluatorGeneratedEmbeddedBridgeUsed` で可視化可能。supported corpus には mixed 実行上限ガードを追加済みだが declaration-heavy / nested complex slices は未完）。
2. Declaration semantics in generated AST runtime:
   1. variable declaration setters/defaulting は複合式まで拡張済み（if/match/call を含む preferred-root AST evaluation + method declaration text injection）
   2. declaration-heavy formulas の pure generated-ast-only 実行（bridge/fallback不要化）は未完
   3. comment-delimited declaration formulas は parity保護のため shortcut 対象外（現状は既存経路維持）
3. Root mapping semantics for mixed grammars:
   1. preferred-root API is available and runtime-connected, but full semantic root policy across declaration/method-heavy formulas is not yet formalized
4. Full parity harness:
   1. representative, medium regression, and extracted legacy-source corpora parity are available
   2. extracted corpus parity now includes category-level coverage/non-fallback counters, external curated corpus merge, and resource-first extracted dataset loading; systematic large corpus with richer category reporting is still not complete
5. DAP dual-runtime execution integration:
   1. `runtimeMode` AST stepping/coordinates are implemented
   2. backend/runtime marker observability and value probe metadata (`evaluationResultType` / `evaluationResultNormalized`) are exposed in generated DAP variables via runtime probe bridge
   3. DAP variables now include 4-backend parity probe fields (`parity.*`, `parity.equalAll`), but per-step evaluator-value parity (step-by-step, not whole-formula probe) is not complete
6. Full DSL-native Java code generation backend (native-emitter gap):
   1. `DSL_JAVA_CODE` execution backend is present with a dedicated `DslJavaCodeCalculator` seam
   2. seam currently reuses legacy JavaCode compiler/runtime path (`JavaCodeCalculatorV3`) in bridge mode; backend selection and metadata are already production-wirable via formula metadata
   3. current seam equivalence with legacy generated Java source is regression-checked on curated corpus
   4. dedicated DSL-native Java emitter (generated AST -> Java source/compiler path without legacy bridge) is not implemented yet
   5. large-corpus parity/performance sign-off for the native emitter path is therefore still pending
7. Delimiter-chain coupling cleanup (design follow-up):
   1. shared runtime abstraction is now in place (`JavaStyleSourceProbe`), reducing duplication risk
   2. remaining work: expose this capability from parser-owned API surface (or shared parser module contract) so evaluator runtime does not own delimiter semantics
8. Parser-keyword coupling cleanup (design follow-up):
   1. declaration shortcut pre-check has removed direct `"var "` dependency, but full parser-driven head detection API is still not centralized
   2. replace remaining ad-hoc textual pre-checks with shared parser capability API (for example `VariableDeclarationParser`/`TinyExpressionParser`-derived) so alias changes (`variable`/localized keywords) do not require evaluator edits
   3. `AstEvaluatorCalculator.shouldTryDeclarationShortcut(...)` has been removed and declaration-main evaluation is parser-attempt-first, but comment/delimiter exclusion logic is still textual in `AstDeclarationRuntime` / `GeneratedAstRuntimeProbe`
   4. move remaining declaration/head/comment probing behind parser-owned capability methods to prevent future parser-alias drift

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
