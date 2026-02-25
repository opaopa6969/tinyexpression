# TinyExpression DSL Handover (2026-02-20)

## Purpose

Context handover after context-limit stop, so the next session can continue implementation without re-discovery.

## Scope Completed This Round

Primary focuses were:

1. Roadmap `P2: Error diagnosability hardening (javacode)`,
2. Roadmap `P1: Annotation-AST minimum viable path`,
3. Roadmap `P3` first step (remove one `@Ignore` with concrete numeric flow).

Implemented for P2:

1. replaced bare `IllegalArgumentException()` with contextual messages in key paths,
2. kept behavior intact (message improvement only),
3. added minimal roadmap tests (`1 fail + 1 success`) for diagnosability.

Implemented for P1:

1. defined annotation contract (`TinyAstNode`, `TinyAstField`, `TinyAstOperator`),
2. annotated number binary/literal parser slice,
3. added generated AST model + annotation-reader adapter,
4. added adapter path in `NumberExpressionBuilder`,
5. added minimal tests (`1 fail + 1 success`) for generated path.

Implemented for P3 (first step):

1. removed `@Ignore` from `testUnifiedNumericTypeRoadmapFromShortToBigDecimal`,
2. replaced placeholder assertion with concrete `parser -> AST -> codegen` checks for:
   - `_short`, `_byte`, `_int`, `_long`, `_float`, `_double`,
3. kept `javaType` roadmap test ignored (pending by design).

Implemented for P3 (follow-up):

1. added `bigInteger` / `bigDecimal` concrete codegen flow in `NumberExpressionBuilder`,
2. added failure case for invalid bigInteger literal in roadmap test,
3. removed `@Ignore` from javaType roadmap test and added concrete object-result flow via `JavaCodeCalculatorV3`,
4. fixed `GeneralJavaClassCreator` object-result branch to emit expression code correctly.
5. changed bigDecimal division codegen to include context scale/rounding mode.
6. added runtime test for bigDecimal `1/3` evaluation with expected scaled result.
7. added object variable declaration/inference minimal path (`var $payload ...; $payload`).
8. added object setter support (`ObjectSetterParser`) with `if not exists` behavior.
9. updated parser tests for `NakedVariableDeclarationParser` to reflect setter-enabled object declarations.
10. expanded object method flow with object parameter support:
    - introduced object type hint prefix/suffix parsers and object variable parser family,
    - added `ObjectVariableMethodParameterParser` to method parameter grammar,
    - updated object codegen path to resolve local method parameter names before context lookup,
    - verified object pass-through method:
      - `call identity('payload')`
      - `object identity($payload as object){ $payload }`
11. started P4 UBNF extension design merge:
    - added draft grammar: `docs/ubnf/tinyexpression-p4-draft.ubnf`
    - validated draft using `unlaxer-dsl` validate-only (`ok=true`)
    - added shared merge spec: `docs/TINYEXPRESSION-P4-UBNF-EXTENSION-SPEC.md`
    - added semantic snapshot tests:
      - `src/test/java/org/unlaxer/tinyexpression/roadmap/UbnfExtensionRoadmapTest.java`
12. improved scope semantics for method parameter shadowing:
    - `VariableBuilder` now prioritizes method parameter resolution (`VariableTypeResolver.resolveFromMethodParameter`) before context/global lookup,
    - roadmap test now asserts lexical shadowing behavior on number method path.

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
11. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/NumberExpressionBuilder.java`
12. `src/main/java/org/unlaxer/tinyexpression/parser/PlusParser.java`
13. `src/main/java/org/unlaxer/tinyexpression/parser/MinusParser.java`
14. `src/main/java/org/unlaxer/tinyexpression/parser/MultipleParser.java`
15. `src/main/java/org/unlaxer/tinyexpression/parser/DivisionParser.java`
16. `src/main/java/org/unlaxer/tinyexpression/parser/NumberParser.java`
17. `src/main/java/org/unlaxer/tinyexpression/ast/annotation/TinyAstNode.java`
18. `src/main/java/org/unlaxer/tinyexpression/ast/annotation/TinyAstNodeKind.java`
19. `src/main/java/org/unlaxer/tinyexpression/ast/annotation/TinyAstField.java`
20. `src/main/java/org/unlaxer/tinyexpression/ast/annotation/TinyAstFields.java`
21. `src/main/java/org/unlaxer/tinyexpression/ast/annotation/TinyAstFieldSource.java`
22. `src/main/java/org/unlaxer/tinyexpression/ast/annotation/TinyAstOperator.java`
23. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/ast/NumberGeneratedAstAdapter.java`
24. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/ast/NumberGeneratedAstNode.java`
25. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/ast/NumberGeneratedBinaryAstNode.java`
26. `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/ast/NumberGeneratedLiteralAstNode.java`

### Test

1. `src/test/java/org/unlaxer/tinyexpression/roadmap/ErrorDiagnosabilityRoadmapTest.java`
2. `src/test/java/org/unlaxer/tinyexpression/evaluator/javacode/NumberGeneratedAstAdapterTest.java`
3. `src/test/java/org/unlaxer/tinyexpression/roadmap/TypeSystemRoadmapTest.java`

### Docs

1. `docs/TINYEXPRESSION-DSL-ROADMAP.md` (progress snapshot added)

## Test Execution Result

Executed and passed:

1. `./mvnw -q -Dtest=ErrorDiagnosabilityRoadmapTest test`
2. `./mvnw -q -Dtest=ParserDispatchTest,TinyExpressionTokensTest test`
3. `./mvnw -q -Dtest=NumberGeneratedAstAdapterTest,ExpressionBuilderTest test`
4. `./mvnw -q -Dtest=ErrorDiagnosabilityRoadmapTest,NumberGeneratedAstAdapterTest,ParserDispatchTest,TinyExpressionTokensTest test`
5. `./mvnw -q -Dtest=TypeSystemRoadmapTest test`
6. `./mvnw -q -Dtest=TypeSystemRoadmapTest,NumberGeneratedAstAdapterTest,ErrorDiagnosabilityRoadmapTest test`
7. `./mvnw -q -Dtest=TypeSystemRoadmapTest,NumberGeneratedAstAdapterTest,ErrorDiagnosabilityRoadmapTest,ExpressionBuilderTest,SimpleUDFTest test`
8. `./mvnw -q -Dtest=MethodsParserTest#testObjectMethodWithObjectParameter test`
9. `./mvnw -q -Dtest=TypeSystemRoadmapTest,SimpleUDFTest,NumberGeneratedAstAdapterTest,ErrorDiagnosabilityRoadmapTest,ExpressionBuilderTest,StringVariableDeclarationParserTest test`
10. `./mvnw -q -Dtest=UbnfExtensionRoadmapTest test`
11. `./mvnw -q -Dtest=TypeSystemRoadmapTest,ErrorDiagnosabilityRoadmapTest test`
12. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests compile`
13. `cd /mnt/c/var/unlaxer-temp/unlaxer-dsl && mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain -Dexec.args=\"--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.ubnf --validate-only --report-format json\"`

Not executed in this round:

1. full test suite.

## Current Backlog Status

1. `P1`: done (minimum viable path).
2. `P2`: done.
3. `P3`: partially done (both roadmap tests activated; further hardening remains).
4. `P4`: in progress.

## Recommended Next Step (Immediate)

Continue with `P3` in this order:

1. harden unified numeric flow for edge cases (rounding/divide semantics for big numbers),
2. harden javaType flow beyond object return type (declaration/inference for object variables/methods),
3. keep test policy minimal (`1 fail + 1 success`).

## Guardrails / Collaboration Rules

1. `unlaxer-common` and `unlaxer-dsl` can be read freely.
2. if changes to `unlaxer-common` or `unlaxer-dsl` are required, ask user permission before editing.
3. prefer implementation-first progress, then minimal tests, per roadmap execution policy.
