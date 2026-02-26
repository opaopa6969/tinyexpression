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
16. resolved P4 associativity/precedence draft validation by grammar rewrite (no dependency repo change):
   - switched operator segment to rule reference form:
     - `AddOp @op ...` / `MulOp @op ...`
   - re-enabled `@leftAssoc` + `@precedence` in `docs/ubnf/tinyexpression-p4-draft.ubnf`
   - validate-only now passes for both draft and repro-success form.
17. expanded P4 UBNF draft coverage with control-flow subset:
   - added `IfExpression`, `NumberMatchExpression`, `NumberCase`, `NumberDefaultCase`
   - integrated into `NumberFactor`
   - kept validator pass on updated draft (`ok=true`).
18. exported and validated parser-ir artifact for the P4 draft:
   - `docs/ubnf/tinyexpression-p4-draft.parser-ir.json`
   - validated with `--validate-parser-ir` (`ok`).
19. expanded P4 draft grammar beyond number-only control flow:
   - added annotation grammar (`Annotation`, `AnnotationParameters`, `AnnotationParameter`),
   - added `StringMatchExpression` / `BooleanMatchExpression` and their case/default rules,
   - refreshed parser-ir artifact and re-validated (`ok`).
20. strengthened P4 semantic snapshot coverage in runtime tests:
   - added successful backreference case (`call provide()` with declared method),
   - added lexical scope-tree object shadowing case (`global $payload` vs method parameter `$payload`),
   - verified both in `UbnfExtensionRoadmapTest`.
21. completed string lexical-scope snapshot and runtime builder compatibility fix:
   - added roadmap test for method parameter shadowing on string path (`$name`),
   - fixed `StringClauseBuilder` to accept wrapped `NumberExpressionParser` token path and delegate to child expression,
   - verified updated `UbnfExtensionRoadmapTest`.
22. extended P4 UBNF draft for typed declaration variants:
   - split `VariableDeclaration` into number/string/boolean/object declaration rules with typed setter rules,
   - split `MethodDeclaration` into number/string/boolean/object method declaration rules with typed return aliases,
   - re-validated draft and refreshed parser-ir artifact (`ok` for validate-only and parser-ir validation).
23. introduced dual-backend runtime wiring for migration safety:
   - added `ExecutionBackend` (`JAVA_CODE` / `AST_EVALUATOR`),
   - added `CalculatorCreatorRegistry` and switched `FormulaInfoParser` to backend-based creator selection,
   - kept `AST_EVALUATOR` as compatibility fallback path while generated evaluator runtime is being integrated.
24. started dependency-side codegen hardening for full migration:
   - implemented executable `MapperGenerator` parse entry + mapping dispatch + assoc extraction in `unlaxer-dsl`,
   - refreshed mapper snapshot goldens in `unlaxer-dsl`,
   - added `runtimeMode` (`token`/`ast`) hook to generated DAP adapter and refreshed DAP snapshot golden.
25. introduced concrete `AST_EVALUATOR` calculator entry class in tinyexpression:
   - added `AstEvaluatorCalculator` as runtime backend surface for generated AST/evaluator integration,
   - added `GeneratedAstRuntimeProbe` to detect generated parser/mapper runtime availability,
   - switched `CalculatorCreatorRegistry` AST backend from direct JavaCode return to dedicated AST backend class.
26. separated generated P4 outputs into runtime/tooling tracks to keep default compile stable:
   - updated `scripts/generate_tinyexpression_p4_from_ubnf.sh` to emit runtime (`AST,Parser,Mapper,Evaluator`) and tooling (`LSP,Launcher,DAP,DAPLauncher`) to separate directories,
   - updated `pom.xml` to auto-include only `target/generated-sources/tinyexpression-p4/runtime` as compile source.
27. implemented first real AST traversal execution path for AST backend:
   - added `AstNumberExpressionEvaluator` to evaluate generated number AST (`NumberGeneratedAstNode`) directly,
   - `AstEvaluatorCalculator` now prefers token-AST runtime for supported number expressions and falls back to JavaCode otherwise,
   - runtime mode marker is exposed via calculator context object: `_astEvaluatorRuntime`.
28. recorded and isolated generated runtime compatibility gap:
   - observed compile break between generated runtime code and current tinyexpression dependency APIs,
   - disabled default `pom.xml` auto-include for generated runtime sources,
   - moved dependency-side follow-up to `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md`.
29. applied dependency-side generator compatibility shims in `unlaxer-dsl`:
   - mapper/lsp/dap generated source creation now uses reflection-based `StringSource` compatibility helper,
   - mapper generated token text extraction now uses compatibility fallback (`getToken` / `tokenString` / `source`),
   - parser generated synthetic scope-event helpers no longer require direct `org.unlaxer.dsl.ir` dependency.
30. re-enabled runtime generated-source compile integration in tinyexpression:
   - added `build-helper-maven-plugin` source include for `target/generated-sources/tinyexpression-p4/runtime`,
   - regenerated P4 artifacts and verified `./mvnw -q -DskipTests compile` succeeds with generated runtime on classpath.
31. made AST backend execution path JavaCode-lazy by default:
   - `AstEvaluatorCalculator` now runs AST-first (`AstNumberExpressionEvaluator`) and only initializes JavaCode runtime on fallback,
   - backend metadata (`_astEvaluatorRuntime`, mapper probe flags) is tracked without forcing JavaCode delegate instantiation,
   - `FormulaInfo.updateCalculatorFromFormula` now treats `AST_EVALUATOR` as non-bytecode-first path (`byteCode` empty, marker JavaCode text).
32. connected generated mapper AST evaluation as preferred AST runtime path:
   - added `GeneratedP4NumberAstEvaluator` to evaluate generated `BinaryExpr` node shape reflectively,
   - `AstEvaluatorCalculator` runtime order is now:
     1) `generated-ast` (when mapper output is evaluable),
     2) `token-ast`,
     3) `javacode-fallback`.
33. improved generated DAP `ast` mode observability:
   - `unlaxer-dsl` `DAPGenerator` now attempts mapper parse in `runtimeMode=ast`,
   - exposes `astNodeCount` and current AST node label (`astCurrentNode`) in DAP variables response,
   - keeps existing token-step behavior as compatibility fallback.
34. added migration handbook for follow-up implementers:
   - `docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md`
   - covers structure, type/function extension, AST mapping, executable conversion, LSP/DAP linkage.
35. expanded mapper generation for non-associative mapping rules:
   - `unlaxer-dsl` `MapperGenerator` now emits constructor-argument extraction for non-assoc captures,
   - supports scalar/optional/list capture initialization paths instead of placeholder defaults.
36. added generated-AST observability on AST backend runtime:
   - `AstEvaluatorCalculator` now stores `_astEvaluatorGeneratedAstNodeCount`,
   - allows DAP/diagnostics layer to inspect generated AST size without token traversal.
37. raised generated DAP `ast` stepping fidelity:
   - `unlaxer-dsl` generated DAP now uses AST-node count as step limit in `runtimeMode=ast`,
   - stack frame label follows AST node label while preserving token-based source coordinates for compatibility.
38. expanded generated AST evaluation entry discovery:
   - `GeneratedP4NumberAstEvaluator` now searches mapped AST graph for binary-expression-like nodes (`left/op/right`) instead of assuming root node shape,
   - enables evaluation from grammar roots that wrap expression nodes.
39. connected AST-node source spans from generated mapper to generated DAP ast runtime mode:
   - `unlaxer-dsl` `MapperGenerator` now emits per-node source span registration and `sourceSpanOf(Object)` lookup,
   - generated DAP `runtimeMode=ast` now computes stack frame location and breakpoint hit lines from AST node spans,
   - generated DAP variable primary entry now switches to AST node snippet (`type=ASTNode`) in ast mode.
40. added backend parity smoke for numeric formulas:
   - introduced `AstEvaluatorBackendParityTest` to compare `JAVA_CODE` and `AST_EVALUATOR` results on representative arithmetic formulas,
   - asserts AST backend runtime remains AST path (`_astEvaluatorRuntime != javacode-fallback`) for these formulas.
41. expanded generated numeric AST evaluator leaf resolution for context variables:
   - `GeneratedP4NumberAstEvaluator` now resolves `$name` leaf literals from `CalculationContext` (`getNumber` / `getValue`) before numeric parse,
   - keeps strict fallback behavior for unresolved/non-numeric leaves.
42. improved mapper selection when root rule is not explicitly mapped:
   - `unlaxer-dsl` generated mapper now picks mapped token candidate by `minimum depth` then `maximum start offset`,
   - avoids accidental capture of declaration-initializer expressions in grammars that contain multiple mapped subexpressions.
43. added final-gap audit document for end-state tracking:
   - `docs/TINYEXPRESSION-FINAL-GAP-AUDIT.md`
   - records done/remaining/priority order toward full DSL replacement + dual runtime parity.
44. expanded P4 mapping coverage for non-numeric expression families:
   - added `@mapping` for `StringExpression`, `BooleanExpression`, `ObjectExpression`, and `VariableRef` in `docs/ubnf/tinyexpression-p4-draft.ubnf`,
   - regenerated runtime/tooling artifacts now include `StringExpr` / `BooleanExpr` / `ObjectExpr` / `VariableRefExpr` AST records.
45. hardened generated mapper for multi-choice captures under strict constructor typing:
   - `unlaxer-dsl` `MapperGenerator` now explores all capture candidates for non-assoc mappings,
   - added type-compatibility guard per constructor target type and `String` text fallback for heterogeneous alternatives.
46. expanded generated AST runtime evaluator beyond number-only output:
   - added `GeneratedP4ValueAstEvaluator` and switched `AstEvaluatorCalculator` generated path dispatch to it,
   - supports `string`/`boolean`/`object` simple expressions via generated AST node inspection and `CalculationContext` variable resolution.
47. added generated-path runtime tests for non-number simple values:
   - `AstEvaluatorGeneratedValuePathTest` verifies `boolean` literal and `object`(string literal) evaluation on `generated-ast` runtime.
48. restored object mapping + generated-path declaration execution for object variables:
   - restored `@mapping(ObjectExpr, params=[value])` in `docs/ubnf/tinyexpression-p4-draft.ubnf`,
   - added `AstDeclarationRuntime` and wired `AstEvaluatorCalculator` to apply declaration setters before generated-path retry,
   - supports `var $payload set if not exists 'fallback' ...; $payload` on `generated-ast` runtime.
49. hardened generated value evaluator for mixed-root/object cases:
   - `GeneratedP4ValueAstEvaluator` now resolves object outputs from binary-shaped roots (`BinaryExpr`) when mapper root selection lands on numeric-like nodes,
   - handles variable leaf/object lookup and pass-through binary-left normalization.
50. extended dependency-side mapper type inference for heterogeneous captures:
   - `unlaxer-dsl` `ASTGenerator` / `MapperGenerator` now infer `Object` when a capture name can bind multiple mapped types across alternatives,
   - prevents over-constrained generated AST fields (for example `ObjectExpr.value` fixed to `BinaryExpr`).
51. improved dependency-side identifier capture mapping:
   - `unlaxer-dsl` `MapperGenerator` now emits `identifierLikeText(...)` for `IdentifierParser`-backed token captures,
   - avoids `$` capture loss in variable-reference mapping paths.
52. expanded generated-path object tests:
   - `AstEvaluatorGeneratedValuePathTest` now covers object variable reference and object declaration setter paths under `generated-ast`.
53. added preferred-root mapping API for generated mapper and wired AST runtime selection:
   - `unlaxer-dsl` generated mapper now supports `parse(String, String preferredAstSimpleName)`,
   - mapped-node selection ranks preferred AST simple name first, then existing depth/offset heuristic,
   - `AstEvaluatorCalculator` now passes preferred AST node by result type (`BinaryExpr`/`StringExpr`/`BooleanExpr`/`ObjectExpr`).
54. fixed generated assoc operator extraction to avoid merged token text (e.g. `+2`) in binary AST:
   - `unlaxer-dsl` `MapperGenerator` now resolves assoc operator token via op-element parser class before text extraction,
   - prevents invalid operator payloads and stabilizes generated numeric evaluation.
55. normalized declaration-side `number` abstract type handling in AST runtime:
   - `AstDeclarationRuntime` now resolves `ExpressionTypes.number` to concrete number type (`specified numberType` or `_float`) for parse/set paths,
   - enables generated-path declaration setter execution for typed number declarations (`as number`).
56. added embedded-expression bridge for generated value runtime on complex non-numeric formulas:
   - added `AstEmbeddedExpressionRuntime` to evaluate expression-like payloads (`match`, `if`, `call`) via `JavaCodeCalculatorV3` when generated mapper stores raw textual value,
   - `GeneratedP4ValueAstEvaluator` now routes `StringExpr` / `BooleanExpr` / `ObjectExpr` textual expressions through the bridge and returns `empty` for unresolved expression-like text instead of returning raw DSL snippets,
   - `AstDeclarationRuntime` now injects method declarations into declaration-setter embedded evaluation context to support `call ...` setter formulas.
57. expanded generated-path validation for complex value families:
   - `AstEvaluatorGeneratedValuePathTest` now covers string `match` evaluation and `call + method declaration` object evaluation on `generated-ast` runtime.
58. expanded backend parity checkpoint beyond numeric-only formulas:
   - `AstEvaluatorBackendParityTest` now compares `JAVA_CODE` vs `AST_EVALUATOR` on representative non-number cases (`string match`, `boolean match`, `call + method declaration`),
   - asserts these cases no longer hit `javacode-fallback` runtime.
59. added generated-path coverage for numeric control-flow expressions:
   - `GeneratedP4ValueAstEvaluator` now routes number-evaluation misses to embedded expression runtime bridge,
   - `AstEvaluatorGeneratedValuePathTest` now verifies `if(true){1}else{2}` runs on `generated-ast` runtime.
60. extended P4 draft boolean grammar with comparison expressions:
   - added `ComparisonExpression ::= NumberExpression CompareOp NumberExpression` and wired it into `BooleanExpression`,
   - supports `match{1==1->...,default->...}` class formulas on generated parser path,
   - refreshed parser-ir artifact (`docs/ubnf/tinyexpression-p4-draft.parser-ir.json`) and validated it.
61. fixed embedded-expression detector compatibility with parser whitespace semantics:
   - `AstEmbeddedExpressionRuntime` now treats `if` / `match` heads with java-style delimiters (multi-space/comment tolerant) instead of only `if(` / `if (`,
   - reduced false positive detection for plain string literals by separating structured-expression and comparison-expression heuristics.
62. added explicit comparison AST mapping and direct AST evaluation path:
   - `@mapping(ComparisonExpr, params=[left, op, right])` added to `ComparisonExpression`,
   - generated runtime now emits `ComparisonExpr` node and `GeneratedP4ValueAstEvaluator` evaluates it directly without bridge fallback.
63. fixed dependency-side duplicate-capture indexing for generated non-assoc mappings:
   - `unlaxer-dsl` `MapperGenerator` now emits `findDescendantByIndex(...)` and assigns occurrence index across params in declaration order,
   - resolves mis-mapping when same parser class is captured by multiple params (`left/right` style captures).
64. added method invocation AST mapping + zero-arg method direct evaluation path:
   - added `@mapping(MethodInvocationExpr, params=[name])` on `MethodInvocation`,
   - `GeneratedP4ValueAstEvaluator` now resolves `MethodInvocationExpr` by locating method declaration body from source and evaluating it through generated AST path first (bridge fallback only if needed),
   - `unlaxer-dsl` mapper generation now binds `IDENTIFIER` capture to `IdentifierParser.class` for accurate method-name extraction.
65. added first curated parity corpus harness for mixed expression families:
   - added `AstEvaluatorParityCorpusTest` to compare `JAVA_CODE` vs `AST_EVALUATOR` across number/if/match/object/variable/method/declaration representative formulas,
   - corpus assertions include runtime guard (`_astEvaluatorRuntime != javacode-fallback`) for this supported subset.
66. extended generated method invocation direct path to argument-bearing calls:
   - `GeneratedP4ValueAstEvaluator` now supports top-level `MethodInvocationExpr` roots for all result families (`number/string/boolean/object`),
   - method invocation evaluation now extracts invocation arguments, parses method parameter declarations, binds arguments into a scoped `CalculationContext`, and evaluates method body on generated AST path first.
67. introduced explicit 3-backend execution model and parity harness:
   - added `ExecutionBackend.DSL_JAVA_CODE` with a dedicated `DslJavaCodeCalculator` seam (`CalculatorCreatorRegistry.dslJavaCodeCreator()`),
   - current `DslJavaCodeCalculator` seam is bridge-only: it delegates to legacy `JavaCodeCalculatorV3` while exposing backend markers (`_tinyExecutionMode=dsl-javacode`, `_tinyExecutionImplementation=legacy-javacode-bridge`),
   - native DSL Java emitter remains an explicit follow-up gap (seam is ready, emitter implementation is pending),
   - added `ThreeExecutionBackendParityTest` to assert value parity across `JAVA_CODE` / `AST_EVALUATOR` / `DSL_JAVA_CODE` on supported mixed formulas.
68. added explicit `if` AST mapping path with direct branch evaluation:
   - added `@mapping(IfExpr, params=[condition, thenExpr, elseExpr])` on `IfExpression`,
   - added `@mapping(ExpressionExpr, params=[value])` on `Expression` so `if` branches are carried as AST nodes instead of plain text tokens,
   - `GeneratedP4ValueAstEvaluator` now evaluates `IfExpr` directly (condition AST eval + selected branch recursive AST eval) and `AstEvaluatorCalculator` prefers `IfExpr` root when formula head is `if`.
69. added explicit `match` AST mapping path with direct case-dispatch evaluation:
   - added typed AST mappings for `Number/String/Boolean` match families:
     - `*MatchExpr`, `*CaseExpr`, `*DefaultCaseExpr`, `*CaseValueExpr`,
   - avoided parser-class capture ambiguity in case-value mapping via dedicated `*CaseValue` wrapper rules,
   - `GeneratedP4ValueAstEvaluator` now evaluates match-case conditions and selected values directly on generated AST path for number/string/boolean/object result flows,
   - `AstEvaluatorCalculator` now prefers `*MatchExpr` roots when formula head is `match` and result type matches.
70. improved declaration-runtime generated AST selection for complex setter expressions:
   - `AstDeclarationRuntime` now uses preferred-root candidate list (`if`/`match`/`call` aware) instead of single fixed root by result type,
   - typed declaration setter flow now covers match-based defaults on generated path (e.g. `set if not exists match{...}`).
71. expanded three-backend parity corpus to a broader mixed slice:
   - `ThreeExecutionBackendParityTest` now covers numeric arithmetic/control-flow/match, string/boolean match, object literals/variables, method argument calls, and declaration-heavy formulas,
   - parity assertion remains `JAVA_CODE` baseline vs `AST_EVALUATOR` and `DSL_JAVA_CODE`.
72. implemented formula-level backend selection wiring + backend diagnostics markers:
   - `FormulaInfoParser` now resolves execution backend from metadata keys (`executionBackend` / `backend`) with strict validation and fallback to configured default backend,
   - `ExecutionBackend` now provides normalized parser (`dsl-javacode` etc.) and runtime marker metadata,
   - `CalculatorCreatorRegistry` now emits unified runtime markers (`_tinyExecutionBackend`, `_tinyExecutionMode`, `_tinyExecutionImplementation`, bridge flags) for all three backends,
   - added focused loader test `FormulaInfoExecutionBackendSelectionTest`.
73. connected generated DAP adapter to tinyexpression runtime probe bridge:
   - added `TinyExpressionDapRuntimeBridge` in tinyexpression (`debugVariables(formula, runtimeMode)`),
   - extended `unlaxer-dsl` `DAPGenerator` so generated debug adapters collect bridge-provided runtime variables reflectively and expose them in DAP `variables`,
   - bridge integration is optional/fallback-safe (adapter keeps working when bridge class is absent).
74. expanded three-backend parity into two-tier corpus strategy:
   - `ThreeExecutionBackendParityTest` now separates:
     - supported corpus (`AST_EVALUATOR` non-fallback required),
     - regression corpus (`AST_EVALUATOR` fallback allowed, value parity required),
   - regression corpus now includes broader legacy-style formulas (`if` boolean operators, string functions/slice, `match`, declaration, method invocation) to increase practical parity coverage while keeping deterministic assertions.
75. added extracted-corpus three-backend parity from existing legacy test assets:
   - added `ThreeExecutionBackendExtractedCorpusParityTest`,
   - test extracts literal formulas from `CalculatorImplTest` source, filters context-light cases, and enforces parity against `JAVA_CODE` baseline for `JAVA_CODE` / `AST_EVALUATOR` / `DSL_JAVA_CODE`,
   - unsupported extracted formulas are skipped with executed-case lower bound to keep regression signal stable while parser coverage evolves.
76. reduced bridge dependence in generated value evaluator for structured text payloads:
   - `GeneratedP4ValueAstEvaluator` now attempts `GeneratedAstRuntimeProbe.tryMapAst(...)` re-entry for expression-like text in `StringExpr` / `BooleanExpr` / `ObjectExpr` before falling back to `AstEmbeddedExpressionRuntime`,
   - added recursion guard by skipping remap when `sourceText == fallbackFormulaSource` to avoid self-loop remap,
   - keeps legacy bridge as fallback path for unresolved/non-mappable structured text.
77. added token-ast literal/variable fast path when generated runtime is unavailable:
   - `AstEvaluatorCalculator` now evaluates simple literal/variable formulas (`'...'`, `true/false`, `$name`, simple numbers) directly before `javacode-fallback`,
   - this path is enabled when generated mapper runtime is unavailable or mapping fails, reducing unnecessary JavaCode bridge use for trivial formulas,
   - added `AstEvaluatorTokenLiteralFallbackTest` with a classloader that blocks generated classes to verify `_astEvaluatorRuntime=token-ast`.
78. unified structured-expression head detection used by AST preferred-root selection:
   - `AstEmbeddedExpressionRuntime` now exposes shared head predicates (`hasIfHead` / `hasMatchHead` / `hasMethodInvocationHead`),
   - `AstEvaluatorCalculator` / `AstDeclarationRuntime` preferred-root builders now use these predicates instead of plain `startsWith(...)`,
   - comment-prefixed control-flow formulas are currently tracked in regression parity corpus (value parity required) until generated parser root handling is fully aligned.
79. added Java source parity checkpoint between legacy and DSL JavaCode backends:
   - added `DslJavaCodeGenerationParityTest`,
   - compares normalized generated Java source from `JAVA_CODE` and `DSL_JAVA_CODE` across curated mixed formulas,
   - confirms current DSL JavaCode seam produces equivalent Java program text (class-name normalization applied).
80. improved generated mapper probe resilience for leading java delimiters:
   - `GeneratedAstRuntimeProbe.tryMapAst(...)` now retries parse after trimming leading whitespace/comments when first parse fails,
   - enabled supported parity coverage for leading-comment `if` formulas (non-fallback assertion),
   - internal-comment delimiter forms (`if/*...*/(...)`) remain in regression corpus parity until generated grammar support is extended.
81. extended probe normalization for comment-delimited `if` heads while preserving legacy parity:
   - `GeneratedAstRuntimeProbe` now normalizes `if/*...*/(` head delimiters for retry parse,
   - supported corpus includes `if/*...*/(...)` non-fallback assertion,
   - `match/*...*/{...}` head normalization is intentionally not forced because it introduced legacy parity drift in regression corpus.
82. extended token-ast literal fast path for double-quoted string literals:
   - `AstEvaluatorCalculator.parseStringLiteral(...)` now accepts both `'...'` and `\"...\"`,
   - `AstEvaluatorTokenLiteralFallbackTest` covers `\"hello\"` under generated-runtime-unavailable classloader.
83. strengthened DAP runtime-bridge mode alias regression coverage:
   - `TinyExpressionDapRuntimeBridgeTest` now asserts backend resolution for `dsl-javacode`, `dsl_java_code`, `ast`, and `token` runtime modes.
84. added declaration-aware evaluation shortcut before javacode fallback:
   - `AstDeclarationRuntime` now exposes `tryEvaluateMainExpression(...)` (declaration apply + main expression evaluation with runtime mode),
   - `AstEvaluatorCalculator` now invokes this path for declaration formulas before `javacode-fallback`,
   - generated-runtime-unavailable regression now includes declaration formula in `AstEvaluatorTokenLiteralFallbackTest`,
   - current guard: comment-containing declaration formulas remain on existing path to avoid legacy parity drift.
85. added extracted-corpus Java source parity check for DSL JavaCode seam:
   - added `DslJavaCodeGenerationExtractedParityTest`,
   - test extracts formulas from `CalculatorImplTest` and compares normalized generated Java source (`JAVA_CODE` vs `DSL_JAVA_CODE`),
   - unsupported formulas are skipped and minimum executed count is enforced.
86. strengthened extracted three-backend value parity with AST non-fallback budget:
   - `ThreeExecutionBackendExtractedCorpusParityTest` now tracks `_astEvaluatorRuntime`,
   - keeps executed-case lower bound and adds minimum non-fallback AST case threshold to guard against broad regression into `javacode-fallback`.
87. widened declaration shortcut eligibility for leading-comment formulas:
   - declaration formula detection now trims leading java-style delimiters/comments before `var` head check,
   - still preserves safety guard for internal comments to avoid parity drift,
   - `AstEvaluatorTokenLiteralFallbackTest` now covers leading-comment declaration formula.
88. reduced extracted Java parity test noise by unsupported-formula prefilter:
   - `DslJavaCodeGenerationExtractedParityTest` now filters ternary (`?:`) formulas before calculator creation,
   - keeps skip-by-exception fallback for other unsupported extractions while reducing expected parse-error log noise.
89. reduced declaration-shortcut keyword coupling in AST evaluator:
   - declaration shortcut trigger no longer depends on literal `"var"` string checks,
   - shortcut pre-check now uses clause separators (`;` / newline) plus existing internal-comment safety guard,
   - actual declaration presence remains parser-driven via `AstDeclarationRuntime.tryEvaluateMainExpression(...)`.
90. queued parser-capability refactor for evaluator head detection:
   - current declaration/embedded-expression pre-checks still include evaluator-side string heuristics,
   - move these checks behind parser-owned capability APIs so parser keyword aliases (for example `variable` or localized tokens) and delimiter-combinator changes do not require evaluator runtime edits,
   - target hotspots: `AstEvaluatorCalculator.shouldTryDeclarationShortcut(...)` and `GeneratedAstRuntimeProbe` normalization path.
91. switched declaration shortcut entry from heuristic gating to parser-attempt-first:
   - `AstEvaluatorCalculator` now always attempts `AstDeclarationRuntime.tryEvaluateMainExpression(...)` before `javacode-fallback` (no `var`/separator heuristic gate),
   - internal-comment parity guard is centralized in `AstDeclarationRuntime` (leading comments allowed, internal comment-delimited declaration formulas remain excluded),
   - keeps runtime parity for regression corpus while reducing evaluator-side keyword coupling.
92. added category-level reporting guard to extracted three-backend parity corpus:
   - `ThreeExecutionBackendExtractedCorpusParityTest` now aggregates executed and AST-non-fallback cases by formula category (`if`, `math-fn`, `arithmetic`, etc.),
   - enforces minimum category spread to detect coverage collapse into a single shape family,
   - keeps existing executed/non-fallback lower bounds.
93. expanded DAP runtime probe value observability:
   - `TinyExpressionDapRuntimeBridge` now publishes `evaluationResultType` and numeric-canonical `evaluationResultNormalized`,
   - generated DAP adapters ingest these via existing runtime-probe variable bridge,
   - improves value-level comparison visibility between execution runtime modes.
94. widened generated mapper preferred-root retries in value evaluator:
   - `GeneratedP4ValueAstEvaluator` now uses multi-candidate preferred roots (head-aware + result-type-aware) instead of single preferred root for
     1) structured-text AST re-entry,
     2) method body evaluation,
     3) method argument expression evaluation,
   - reduces unnecessary embedded-bridge fallback for object/match/if/method-heavy slices.
95. started externalized extracted parity dataset support:
   - `ThreeExecutionBackendExtractedCorpusParityTest` now merges formulas from `src/test/resources/parity/three-backend-parity-corpus.txt`,
   - keeps extracted `CalculatorImplTest` formulas as base and deduplicates by formula text,
   - enables curated corpus extension without editing test source.
96. expanded declaration shortcut literal parser for double-quoted strings:
   - `AstDeclarationRuntime.parseLiteralOrVariable(...)` now accepts both `'...'` and `\"...\"`,
   - generated-runtime-unavailable declaration fallback test now covers double-quoted setter value.
97. added backend parity probe variables to DAP runtime bridge:
   - `TinyExpressionDapRuntimeBridge` now evaluates formula once per backend and exports `parity.*` variables,
   - includes per-backend value/type/normalized fields plus `parity.equalAll`,
   - provides value-level runtime parity visibility directly in DAP `variables` response.
98. added generated-path embedded-bridge usage marker:
   - `GeneratedP4ValueAstEvaluator` now tracks whether evaluation touched `AstEmbeddedExpressionRuntime`,
   - `AstEvaluatorCalculator` publishes `_astEvaluatorGeneratedEmbeddedBridgeUsed`,
   - DAP runtime bridge exposes this marker to distinguish pure generated-ast runs from generated+embedded mixed execution.
99. externalized extracted corpus source with resource-first loading:
   - added `src/test/resources/parity/three-backend-extracted-corpus.escaped.txt` (161 extracted formulas from `CalculatorImplTest`),
   - `ThreeExecutionBackendExtractedCorpusParityTest` now prefers this resource and only falls back to source extraction when resource is absent,
   - reduces hard dependency on test-source parsing for routine parity runs.
100. added supported-corpus guard for generated+embedded mixed execution:
   - `ThreeExecutionBackendParityTest` now tracks `_astEvaluatorGeneratedEmbeddedBridgeUsed` on supported corpus,
   - enforces upper bound on mixed (`generated-ast` + embedded bridge) cases,
   - keeps `AST_EVALUATOR` non-fallback guarantee while preventing silent regression to bridge-heavy generated path.
101. restored pre-refactor OOTC runtime as a selectable 4th backend:
   - introduced `ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR` and backend aliases (`legacy-astcreator`, `ootc`),
   - added `org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyOperatorOperandTreeCreator` (restored from `bb2b085` with minimal object-parser compatibility patch),
   - added `org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyAstCreatorJavaCodeCalculator`,
   - wired `CalculatorCreatorRegistry` / `FormulaInfoParser` / DAP runtime mode resolution to select this backend.
102. upgraded parity guard from three to four backends:
   - `ThreeExecutionBackendParityTest` and `ThreeExecutionBackendExtractedCorpusParityTest` now enforce value parity across
     `JAVA_CODE` / `JAVA_CODE_LEGACY_ASTCREATOR` / `AST_EVALUATOR` / `DSL_JAVA_CODE`,
   - `TinyExpressionDapRuntimeBridge` parity probe now includes the 4th backend and `parity.equalAll` checks all four.
103. separated legacy backend implementation package for readability:
   - moved legacy runtime classes under `org.unlaxer.tinyexpression.evaluator.javacode.legacy`,
   - kept existing current reducer package unchanged to minimize migration risk.
104. documented 4-backend responsibility contract:
   - added `docs/TINYEXPRESSION-BACKEND-CONTRACT.md`,
   - fixed backend role boundaries (`JAVA_CODE` / `JAVA_CODE_LEGACY_ASTCREATOR` / `AST_EVALUATOR` / `DSL_JAVA_CODE`),
   - fixed parity and selection rules for formula metadata/runtime aliases.
105. centralized java-style delimiter/comment probing to reduce evaluator-side drift:
   - added shared java-style probing helpers (`skip`, `trim`, head word boundary checks, structured-head normalization) and removed duplicated scanners,
   - replaced duplicated implementations in:
     - `AstEmbeddedExpressionRuntime`,
     - `GeneratedAstRuntimeProbe`,
     - `AstDeclarationRuntime`,
     - `GeneratedP4ValueAstEvaluator`,
   - kept parity-safe behavior by limiting structured-head normalization to existing compatibility scope (`if` + method-invocation heads) and avoiding semantic expansion for `match` normalization.
106. introduced parser-owned keyword constants and rewired runtime probes to use them:
   - added `TinyExpressionKeywords` (`if` / `match` / `call` / `external` / `internal` / `var` / `variable`),
   - parser definitions now reference shared constants (`IfExpressionParser`, `NumberMatchExpressionParser`, `SideEffectName1/2Parser`, `AbstractVariableDeclarationParser`),
   - evaluator/probe runtime now references shared constants (`TinyExpressionParserCapabilities`, `AstEmbeddedExpressionRuntime`, `GeneratedP4ValueAstEvaluator`) to reduce keyword drift when parser aliases evolve.
107. exposed parser-owned delimiter/head capability API and switched evaluator runtime to it:
   - added `TinyExpressionParserCapabilities` under parser package,
   - migrated evaluator/probe delimiter/comment/head checks to parser-owned API (`GeneratedAstRuntimeProbe`, `AstDeclarationRuntime`, `AstEmbeddedExpressionRuntime`, `GeneratedP4ValueAstEvaluator`),
   - removed evaluator-local delimiter utility so delimiter semantics are now owned by parser package.
108. introduced first DSL-native Java emitter slice for `DSL_JAVA_CODE` backend:
   - added `DslGeneratedAstJavaEmitter` (generated-AST based java-source emitter) with strict literal subset support (`number` / `string` / `boolean` / `object-literal`),
   - `DslJavaCodeCalculator#createJavaClass(...)` now runs `generated-ast -> java source` first and falls back to legacy `GeneralJavaClassCreator` path when unsupported,
   - backend markers are now dynamic for DSL backend:
     - native path: `_tinyExecutionImplementation=dsl-javacode-native`, `_tinyExecutionBridgeImplementation=false`,
     - fallback path: existing `legacy-javacode-bridge`,
   - added emitter observability markers:
     - `_tinyDslJavaEmitterMode`,
     - `_tinyDslJavaNativeEmitterUsed`.
109. exposed DSL native-emitter markers in DAP runtime bridge:
   - `TinyExpressionDapRuntimeBridge` now forwards `_tinyDslJavaEmitterMode` / `_tinyDslJavaNativeEmitterUsed`,
   - DAP regression now verifies bridge case (`1+1`) and native-literal case (`1`) for `runtimeMode=dsl-javacode`.



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
10. `./mvnw -q -Dtest=UbnfExtensionRoadmapTest test`
11. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args=\"--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.ubnf --validate-only --report-format json\"`
12. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args=\"--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.ubnf --export-parser-ir /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.parser-ir.json --report-format json\"`
13. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args=\"--validate-parser-ir /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.parser-ir.json --report-format json\"`
14. `scripts/generate_tinyexpression_p4_from_ubnf.sh`
15. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest,AstEvaluatorBackendParityTest test`
16. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest#testObjectVariableUsesGeneratedAstPath test`
17. `./mvnw -q -DskipTests compile`
18. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests compile`
19. `scripts/generate_tinyexpression_p4_from_ubnf.sh`
20. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest#testTypedDeclarationSettersUseGeneratedAstPath test`
21. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest test`
22. `./mvnw -q -Dtest=AstEvaluatorBackendParityTest test`
23. `./mvnw -q -DskipTests compile`
24. `./mvnw -q -Dtest=AstEvaluatorBackendParityTest,AstEvaluatorGeneratedValuePathTest test`
25. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args="--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.ubnf --export-parser-ir /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.parser-ir.json --report-format json"`
26. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args="--validate-parser-ir /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.parser-ir.json --report-format json"`
27. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest,AstEvaluatorBackendParityTest test`
28. `./mvnw -q -Dtest=AstEvaluatorParityCorpusTest,AstEvaluatorGeneratedValuePathTest,AstEvaluatorBackendParityTest test`
29. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest,AstEvaluatorParityCorpusTest test`
30. `./mvnw -q -Dtest=AstEvaluatorBackendParityTest test`
31. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest,ThreeExecutionBackendParityTest test`
32. `./mvnw -q -Dtest=AstEvaluatorParityCorpusTest test`
33. `mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args="--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.ubnf --export-parser-ir /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.parser-ir.json --report-format json"`
34. `mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args="--validate-parser-ir /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.parser-ir.json --report-format json"`
35. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest,AstEvaluatorParityCorpusTest test`
36. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest test`
37. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest,AstEvaluatorBackendParityTest test`
38. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest,FormulaInfoExecutionBackendSelectionTest test`
39. `./mvnw -q -Dtest=TinyExpressionDapRuntimeBridgeTest,FormulaInfoExecutionBackendSelectionTest test`
40. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest,TinyExpressionDapRuntimeBridgeTest test`
41. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest,ThreeExecutionBackendExtractedCorpusParityTest test`
42. `./mvnw -q -Dtest=AstEvaluatorGeneratedValuePathTest,ThreeExecutionBackendParityTest test`
43. `./mvnw -q -Dtest=AstEvaluatorTokenLiteralFallbackTest,ThreeExecutionBackendParityTest,ThreeExecutionBackendExtractedCorpusParityTest test`
44. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest,AstEvaluatorGeneratedValuePathTest test`
45. `./mvnw -q -Dtest=DslJavaCodeGenerationParityTest,ThreeExecutionBackendParityTest,AstEvaluatorTokenLiteralFallbackTest test`
46. `./mvnw -q -Dtest=ThreeExecutionBackendExtractedCorpusParityTest,AstEvaluatorTokenLiteralFallbackTest,DslJavaCodeGenerationParityTest test`
47. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest test`
48. `./mvnw -q -Dtest=ThreeExecutionBackendExtractedCorpusParityTest test`
49. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest test`
50. `./mvnw -q -Dtest=ThreeExecutionBackendParityTest test`
51. `./mvnw -q -Dtest=DslGeneratedAstJavaEmitterTest test`
52. `./mvnw -q -Dtest=FormulaInfoExecutionBackendSelectionTest,DslGeneratedAstJavaEmitterTest test`
53. `./mvnw -q -Dtest=DslJavaCodeGenerationParityTest,ThreeExecutionBackendParityTest,ThreeExecutionBackendExtractedCorpusParityTest test`
54. `./mvnw -q -Dtest=TinyExpressionDapRuntimeBridgeTest,FormulaInfoExecutionBackendSelectionTest,DslGeneratedAstJavaEmitterTest test`
55. `./mvnw -q -Dtest=DslJavaCodeGenerationParityTest,ThreeExecutionBackendParityTest,ThreeExecutionBackendExtractedCorpusParityTest,TinyExpressionDapRuntimeBridgeTest test`

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
