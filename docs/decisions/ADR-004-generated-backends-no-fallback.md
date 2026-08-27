# ADR-004: Generated backends do not use handwritten fallbacks

**Status**: Accepted
**Date**: 2026-08-26
**Deciders**: Project architect

## Context

`AST_EVALUATOR` and `DSL_JAVA_CODE` originally used generated P4 parsing where
possible, then silently switched to reflection-based evaluation, token-tree
evaluation, or the handwritten Java-code generator. A formula could therefore
change implementation after a grammar or mapper edit while returning the same
value. Runtime markers exposed the switch, but callers rarely inspected them.

That behavior hid P4 coverage gaps and made the generated grammar an optional
front end rather than the source of truth.

## Decision

The generated backend families are strict:

- `AST_EVALUATOR` and `P4_AST_EVALUATOR` execute the generated P4 AST with
  `P4TypedAstEvaluator` only.
- `DSL_JAVA_CODE` and `P4_DSL_JAVA_CODE` emit Java from the generated P4 AST
  with `P4TypedJavaCodeEmitter` only.
- A parse, mapping, typing, evaluation, or emission gap is an explicit failure.
- `JAVA_CODE` and `JAVA_CODE_LEGACY_ASTCREATOR` remain explicit legacy choices;
  they are not implicit recovery paths.
- Precompiled bytecode loaded for a DSL backend remains a valid stored artifact,
  and is reported as `precompiled-bytecode` rather than a bridge.

Grammar and AST coverage changes must update the UBNF grammar, typed consumers,
and parity tests together.

## Consequences

- Backend selection is deterministic and observable.
- P4 gaps fail near their cause instead of being masked by another implementation.
- Existing callers that depended on silent recovery must choose a legacy backend
  explicitly or migrate their formulas to supported P4 syntax.
- Historical migration documents can still describe the removed chain, but the
  backend contract and primary guides are authoritative for current behavior.

## Related

- [Backend contract](../TINYEXPRESSION-BACKEND-CONTRACT.md)
- [Backend guide](../backends.md)
- [ADR-001](ADR-001-p4-primary.md)
- Issues #22, #32, #35, and #46
