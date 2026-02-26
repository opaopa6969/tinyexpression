# TinyExpression Backend Contract

Last updated: 2026-02-26

## 1. Backend Roles

1. `JAVA_CODE`
   - class family: `org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3`
   - role: production baseline for JavaCode path
   - change policy: primary target for JavaCode-side feature additions

2. `JAVA_CODE_LEGACY_ASTCREATOR`
   - class family:
     - `org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyAstCreatorJavaCodeCalculator`
     - `org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyOperatorOperandTreeCreator`
   - role: pre-refactor OOTC comparison baseline
   - change policy: keep as frozen reference; allow only minimal compatibility patches

3. `AST_EVALUATOR`
   - class family: `org.unlaxer.tinyexpression.evaluator.ast.AstEvaluatorCalculator`
   - runtime chain: `generated-ast -> token-ast -> javacode-fallback`
   - role: DSL replacement execution line
   - change policy: primary target for generated AST coverage expansion

4. `DSL_JAVA_CODE`
   - class family: `org.unlaxer.tinyexpression.evaluator.javacode.DslJavaCodeCalculator`
   - role: DSL JavaCode seam (hybrid: partial native emitter + legacy bridge fallback)
   - change policy: migration target for expanding native DSL Java emitter coverage

## 2. Selection Contract

1. formula metadata:
   - `executionBackend` or `backend`
2. runtime aliases:
   - `token` -> `JAVA_CODE`
   - `legacy-astcreator` / `ootc` -> `JAVA_CODE_LEGACY_ASTCREATOR`
   - `ast` -> `AST_EVALUATOR`
   - `dsl-javacode` -> `DSL_JAVA_CODE`
3. wiring:
   - `org.unlaxer.tinyexpression.runtime.ExecutionBackend`
   - `org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry`
   - `org.unlaxer.tinyexpression.loader.FormulaInfoParser`

## 3. Parity Contract

1. supported corpus:
   - all four backends must return equivalent values
   - `AST_EVALUATOR` must avoid `javacode-fallback`
2. extracted corpus:
   - all four backends must return equivalent values
   - executed/non-fallback thresholds must pass
3. DAP probe:
   - `parity.*` variables and `parity.equalAll` must reflect all four backends

## 4. Runtime Marker Contract

1. shared:
   - `_tinyExecutionBackend`
   - `_tinyExecutionMode`
   - `_tinyExecutionImplementation`
   - `_tinyExecutionBridgeImplementation`
   - `_tinyExecutionNonBridgeImplementation`
2. DSL backend additional markers:
   - `_tinyDslJavaEmitterMode`
   - `_tinyDslJavaNativeEmitterUsed`
3. current DSL implementation values:
   - native-slice hit: `_tinyExecutionImplementation=dsl-javacode-native`
   - fallback: `_tinyExecutionImplementation=legacy-javacode-bridge`

## 5. Change Guidelines

1. when extending syntax/runtime:
   - first update `JAVA_CODE` and `AST_EVALUATOR`
   - keep `JAVA_CODE_LEGACY_ASTCREATOR` changes minimal
2. do not repurpose backend names
3. if backend behavior contract changes:
   - update this document
   - update parity tests in the same change
