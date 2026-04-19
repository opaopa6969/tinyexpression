# ADR-002: 数値型昇格ルール

**Status**: Accepted  
**Date**: 2026-03-01  
**Deciders**: Project architect

---

## Context

TinyExpression supports a numeric type ladder from `byte` through `double`, plus `BigDecimal` and `BigInteger`. When an arithmetic expression combines operands of different numeric types, the engine must decide the result type.

Key design pressures:

1. **Java alignment**: Formulas are ultimately compiled to Java (`JAVA_CODE` backend). Surprising deviations from Java's rules would create parity bugs.
2. **FormulaInfo control**: Operators like `numberType` and `resultType` in `FormulaInfo` give per-formula control. The promotion rules must compose predictably with these overrides.
3. **ExpressionTypes.number**: The `number` type is a `float` alias used as a convenience default. Promotion rules must handle this transparently.

---

## Decision

Adopt Java's **widening primitive conversion** rules for binary arithmetic:

```
double > float > long > int > short > byte
```

When two operands have different types, the result type is the wider of the two.

`number` (alias for `float`) participates as `float` in all promotion rules.

### Specific Rules

| Left | Right | Result |
|------|-------|--------|
| `double` | any numeric | `double` |
| `float` | `long` or smaller | `float` |
| `long` | `int` or smaller | `long` |
| `int` | `short` or `byte` | `int` |
| `number` | any | same as `float` |

### String Promotion

The `+` operator performs string concatenation when either operand is `string`. Numeric operands are converted to `String` via `String.valueOf()`.

### Comparison Result

All comparison operators (`==`, `!=`, `>`, `>=`, `<`, `<=`) always produce `boolean` regardless of operand types.

---

## Consequences

### Positive

- Behavior is predictable for Java developers — no surprises
- `JAVA_CODE` backend's generated Java code implicitly follows the same rules
- `SpecifiedExpressionTypes` can override the result type at the formula level without conflicting with internal promotion

### Negative

- `BigDecimal` and `BigInteger` do not participate in standard promotion. Mixed arithmetic with these types has limited support and may produce unexpected results. This is a known limitation.

### Neutral

- `AST_EVALUATOR` and `P4_AST_EVALUATOR` must independently implement these rules in their tree walkers. Parity tests (`ThreeExecutionBackendParityTest`) verify consistency.

---

## Alternatives Considered

**Always promote to `double`**: Rejected. Would break formulas relying on `float` precision behavior and waste precision where not needed.

**Custom promotion rules diverging from Java**: Rejected. Parity bugs between `JAVA_CODE` (which delegates to javac) and AST backends would be unavoidable.

---

## Related

- [docs/language-guide.md — Type Hints](../language-guide.md#type-hints)
- [ADR-001-p4-primary.md](ADR-001-p4-primary.md)
- `specs/type-system.md`
