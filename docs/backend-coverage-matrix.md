# Backend Coverage Matrix

## Public execution backends

| Backend | Implementation | Generated-only |
|---------|----------------|:--------------:|
| `JAVA_CODE` | `JavaCodeCalculatorV3` | No (explicit legacy backend) |
| `JAVA_CODE_LEGACY_ASTCREATOR` | `LegacyAstCreatorJavaCodeCalculator` | No (frozen reference) |
| `AST_EVALUATOR` | `P4TypedAstEvaluator` | Yes |
| `DSL_JAVA_CODE` | `P4TypedJavaCodeEmitter` | Yes |
| `P4_AST_EVALUATOR` | `P4TypedAstEvaluator` | Yes |
| `P4_DSL_JAVA_CODE` | `P4TypedJavaCodeEmitter` | Yes |

## Generated P4 coverage

The generated AST evaluator and Java emitter cover the representative 49-test
calculator corpus, including arithmetic, declarations and declared-type inference,
boolean/string expressions, nested `if`, ternary and `match`, methods, imports,
external calls, string methods/predicates/slices, and time-range functions.

There is no runtime fallback chain. A grammar or mapping gap is an explicit error.
Coverage is expanded by changing
`tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` and the typed
evaluator/emitter together.
