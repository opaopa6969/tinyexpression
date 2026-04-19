# TinyExpression Backends

[日本語版](backends-ja.md)

TinyExpression v1.4.10 provides 6 execution backends. This document describes their differences, selection contract, fallback chain, and recommended usage.

---

## Backend Summary

| Backend | Class | Status | Strategy |
|---------|-------|--------|----------|
| `JAVA_CODE` | `JavaCodeCalculatorV3` | Production | Parse → generate Java → `javac` → load → invoke |
| `JAVA_CODE_LEGACY_ASTCREATOR` | `LegacyAstCreatorJavaCodeCalculator` | Frozen (reference) | Same as above with pre-refactor AST creator |
| `AST_EVALUATOR` | `AstEvaluatorCalculator` | Production | Parse → AST → tree-walking interpreter |
| `DSL_JAVA_CODE` | `DslJavaCodeCalculator` | Migration target | Hybrid: DSL native emitter + legacy bridge |
| `P4_AST_EVALUATOR` | `P4AstEvaluatorCalculator` | PRIMARY (P4) | UBNF parser → P4 AST → type-safe evaluator |
| `P4_DSL_JAVA_CODE` | `P4DslJavaCodeCalculator` | Migration target (P4) | UBNF parser → P4 AST → DSL Java emitter |

---

## Backend Details

### JAVA_CODE

The current production JavaCode baseline.

- **Class**: `JavaCodeCalculatorV3`
- **Change policy**: Main target for new feature additions on the JavaCode path
- **How it works**: Translates the formula into Java source code, compiles it in-memory via `javax.tools.JavaCompiler`, loads the class via `MemoryClassLoader`, and invokes it with `CalculationContext`
- **Pros**: Fastest execution after compilation; JIT-optimized
- **Cons**: Compilation overhead at first call; in-memory `javac` dependency

### JAVA_CODE_LEGACY_ASTCREATOR

Pre-refactor comparison baseline.

- **Class**: `LegacyAstCreatorJavaCodeCalculator`, `LegacyOperatorOperandTreeCreator`
- **Change policy**: Frozen — only minimal compatibility patches allowed
- **Purpose**: Regression baseline for verifying `JAVA_CODE` refactors produce identical results

### AST_EVALUATOR

AST traversal evaluator with a 3-level fallback chain.

- **Class**: `AstEvaluatorCalculator`
- **Fallback chain**:
  ```
  P4TypedAstEvaluator (PRIMARY)
      │ P4 grammar gap
      ▼
  GeneratedP4NumberAstEvaluator
      │ fails
      ▼
  AstTokenTreeEvaluator (legacy AST walk)
      │ fails
      ▼
  JavaCode fallback (JAVA_CODE path)
  ```
- **Change policy**: Expanding generated-AST coverage is the primary target
- **Pros**: No compilation overhead; suitable for lightweight deployments
- **Cons**: Slightly slower than `JAVA_CODE` for hot paths; fallback to `JAVA_CODE` adds latency

### DSL_JAVA_CODE

Hybrid DSL Java emitter.

- **Class**: `DslJavaCodeCalculator`
- **Change policy**: Migration target for expanding native DSL Java emitter coverage
- **How it works**: Tries the native DSL Java emitter first; falls back to legacy bridge if the expression is not covered
- **Runtime markers**:
  - `_tinyDslJavaNativeEmitterUsed = true` → native path hit
  - `_tinyExecutionImplementation = legacy-javacode-bridge` → fallback

### P4_AST_EVALUATOR (PRIMARY for P4)

Type-safe UBNF-generated parser with AST evaluation.

- **Class**: `P4AstEvaluatorCalculator`
- **Change policy**: Expanding P4 grammar coverage; primary reference for LSP/DAP
- **How it works**: Uses the UBNF-generated `TinyExpressionP4Parsers` to produce a sealed-interface P4 AST, then evaluates it via `P4TypedAstEvaluator`
- **Advantage over AST_EVALUATOR**: No regex in LSP/DAP; fully `instanceof`-based dispatch; compile-time exhaustiveness
- **Limitation**: Does not yet cover all language features (P4 grammar gaps fall back to legacy)

### P4_DSL_JAVA_CODE

Type-safe UBNF-generated parser with DSL Java code emission.

- **Class**: `P4DslJavaCodeCalculator`
- **Change policy**: Migration target toward fully generated DSL evaluator
- **How it works**: P4 parser → P4 AST → DSL Java emitter

---

## Selection Contract

### Resolution Order

1. **Global default**: `FormulaInfoAdditionalFields.setExecutionBackend(...)` — default is `JAVA_CODE`
2. **Per-formula override**: `executionBackend` or `backend` key in `FormulaInfo`
3. **Implementation mapping**: `CalculatorCreatorRegistry.forBackend(ExecutionBackend)`

### Runtime Aliases

| Alias | Backend |
|-------|---------|
| `token` | `JAVA_CODE` |
| `legacy-astcreator`, `ootc` | `JAVA_CODE_LEGACY_ASTCREATOR` |
| `ast` | `AST_EVALUATOR` |
| `dsl-javacode` | `DSL_JAVA_CODE` |
| `p4-ast`, `p4-ast-evaluator` | `P4_AST_EVALUATOR` |
| `p4-dsl-javacode`, `p4-dsl-java-code` | `P4_DSL_JAVA_CODE` |

---

## Parity Contract

All 6 backends must return equivalent values for the same input. Key requirements:

1. All 6 backends produce equivalent values for supported corpus (MUST)
2. `AST_EVALUATOR` must avoid the `javacode-fallback` for supported expressions (MUST)
3. `P4_AST_EVALUATOR` and `P4_DSL_JAVA_CODE` must match the other 4 backends (MUST)
4. **Known exception**: Formulas using syntax not yet covered by P4 grammar use the fallback path

### DAP Parity Variables

When running in DAP debug mode, the following variables are exposed:

| Variable | Description |
|----------|-------------|
| `parity.JAVA_CODE` | Result from `JAVA_CODE` backend |
| `parity.AST_EVALUATOR` | Result from `AST_EVALUATOR` backend |
| `parity.P4_AST_EVALUATOR` | Result from `P4_AST_EVALUATOR` backend |
| `parity.equalAll` | `true` if all 6 backends agree |

---

## Runtime Markers

All backends set these context markers after execution:

| Marker | Description |
|--------|-------------|
| `_tinyExecutionBackend` | Backend name used |
| `_tinyExecutionMode` | Execution mode |
| `_tinyExecutionImplementation` | Implementation variant |
| `_tinyExecutionBridgeImplementation` | Bridge implementation variant (DSL backends) |

---

## Rollout Strategy

### Recommended Production Approach

1. Keep the global default as `JAVA_CODE`
2. Test specific formulas with `P4_AST_EVALUATOR` to verify coverage
3. Override per-formula: `backend:P4_AST_EVALUATOR`
4. Compare `parity.equalAll` in DAP debug before full migration
5. Do not change `JAVA_CODE_LEGACY_ASTCREATOR` — treat it as immutable reference

### Backend Change Guidelines

1. When adding syntax or runtime features: update `JAVA_CODE` and `AST_EVALUATOR` first
2. Keep changes to `JAVA_CODE_LEGACY_ASTCREATOR` to a minimum
3. Do not reuse backend names (MUST NOT)
4. When changing backend behavior contracts: update this document and parity tests simultaneously

---

## Known Limitations

- P4 backends do not yet cover all language features (incremental expansion in progress)
- `JAVA_CODE_LEGACY_ASTCREATOR` is frozen
- `BigDecimal` and `BigInteger` arithmetic in expressions has limited support

---

## Related Documents

- [architecture.md](architecture.md) — how backends connect to parser and AST layers
- [decisions/ADR-001-p4-primary.md](decisions/ADR-001-p4-primary.md) — why P4 was promoted to PRIMARY
