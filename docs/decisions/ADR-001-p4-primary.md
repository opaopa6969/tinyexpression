# ADR-001: P4TypedAstEvaluator を PRIMARY 評価パスに昇格

**Status**: Accepted  
**Date**: 2026-02-26  
**Deciders**: Project architect

---

## Context

TinyExpression has two parser stacks:

1. **Legacy stack** — hand-written parser combinators (unlaxer-common). Covers all language features.
2. **P4 stack** — UBNF-generated, type-safe parsers (unlaxer-dsl). Covers a growing subset of language features.

Prior to this decision, the `AST_EVALUATOR` backend used the following chain:

```
AstTokenTreeEvaluator (legacy, regex-based)
    │ fails
    ▼
JavaCode fallback
```

The P4 evaluator (`P4TypedAstEvaluator`) existed but was not the primary path in `AST_EVALUATOR`.

Meanwhile:

- P4 grammar coverage had reached full parity for the core feature set (string slice was the last gap closed in v1.4.9)
- The legacy regex-based approach in LSP/DAP created maintenance debt and incorrect semantic tokens
- The P4 AST uses sealed interface records, enabling `instanceof`-based dispatch with compile-time exhaustiveness

---

## Decision

Promote `P4TypedAstEvaluator` to the **PRIMARY** evaluation path in `AST_EVALUATOR`.

The new chain is:

```
P4TypedAstEvaluator (PRIMARY)
    │ P4 grammar gap
    ▼
GeneratedP4NumberAstEvaluator
    │ fails
    ▼
AstTokenTreeEvaluator
    │ fails
    ▼
JavaCode fallback (SAFETY NET)
```

The fallback chain is retained as a **safety net only** — not an intended execution path.

---

## Consequences

### Positive

- LSP/DAP now uses `instanceof`-based dispatch — no regex, no ad-hoc string matching
- Compile-time exhaustiveness: adding a new AST node type causes a compile error at the switch site, not a silent runtime failure
- `parity.*` DAP variables reflect P4 evaluation as the source of truth
- `_tinyP4ParserUsed` marker allows observability of P4 vs legacy path usage

### Negative

- Formulas using language features not yet covered by P4 grammar will take the fallback path, which adds latency
- The fallback path must be monitored via `_tinyExecutionImplementation` markers to identify remaining coverage gaps

### Neutral

- `JAVA_CODE` backend is unaffected — it uses the legacy stack directly and is the production JavaCode baseline
- DAP default runtime mode changed from `token` to `ast-evaluator` to align with this decision

---

## Alternatives Considered

**Keep legacy as primary, P4 as optional**: Rejected. The maintenance cost of the regex-based approach in LSP/DAP was high. The P4 stack's type safety is a significant quality improvement.

**Wait for 100% P4 coverage before promoting**: Rejected. Full coverage would delay LSP/DAP improvements for months. The fallback safety net handles remaining gaps safely.

---

## Related

- [ADR-002-type-promotion.md](ADR-002-type-promotion.md)
- [docs/backends.md](../backends.md)
- Git commit: `0c91aef refactor: P4TypedAstEvaluator is PRIMARY, fallback chain is SAFETY NET`
