# TinyExpression P4 LSP/DAP — VS Code Extension

VS Code extension that provides Language Server (LSP) and Debug Adapter (DAP) support
for TinyExpression formulas using the P4 grammar (UBNF-generated, type-safe).

## Features

- **Syntax highlighting** — keywords, variables (`$name`), numbers, strings, operators, comments
- **Semantic tokens** — type-safe classification via Parser `instanceof` (no regex)
- **Diagnostics** — rich `TE001`–`TE025` messages, catalog-aware `TE022`/`TE024`, stable `ULX-PARSE-001` machine data, and targeted Quick Fixes
- **Completion** — P4 keywords, declarations, imports, methods, and bundled/external `.tecatalog` variables with context labels
- **Hover** — AST node type, preferred root, declarations, and catalog descriptions
- **Debug (DAP)** — step execution with P4 runtime markers in Variables panel:
  - `_tinyP4ParserUsed` — whether the P4 grammar parsed the formula
  - `_tinyP4ParserExact` — whether generated P4 parsing succeeded exactly
  - `_tinyP4ParserProbeMode` — `exact`, `semantic`, or `failed` (no heuristic parse fallback)
  - `_tinyP4AstNodeType` — sealed-interface record type of the AST root
  - `_tinyP4AstNodePath` — breadth-first path through the AST
  - `parity.*` — 6-backend evaluation comparison (JAVA_CODE / AST_EVALUATOR / DSL_JAVA_CODE / P4_AST / P4_DSL)

## Requirements

- Java 21+
- VS Code 1.85+

## Quick start

1. Install this extension (`.vsix` install or from Marketplace)
2. Run **TinyExpression P4: Open Getting Started** for the in-editor walkthrough
3. Open a `.tinyexp`, `*.formulainfo`, or `formulaInfo.txt` file — the LSP server starts automatically
4. To debug a formula: open `Run and Debug`, select **Debug TinyExpression P4**, press `F5`

The Command Palette also provides **Open Language Guide** and **Create FormulaInfo Sample**.
The guide is bundled in the VSIX, so it remains available in an offline code-server session.

The default debug configuration stops on the first generated AST node. `F10` moves through
AST nodes, `F5` continues to the next breakpoint, and the Variables view contains the selected
backend result plus the six-backend parity comparison.

```json
{
  "type": "tinyexpressionP4",
  "request": "launch",
  "name": "Debug TinyExpression P4",
  "program": "${file}",
  "runtimeMode": "metadata",
  "steppingMode": "ast",
  "stopOnEntry": true,
  "variables": {
    "score": 42,
    "inputName": "alice"
  }
}
```

For FormulaInfo documents, add `"calculatorName": "derivedScore"` to select a block.
`runtimeMode: metadata` follows that block's `executionBackend`; plain formulas use `p4-ast`.
Dependencies execute before their consumers and appear as `formulaInfo.<name>.*` values.
To change a FormulaInfo backend, edit its `executionBackend` metadata; a conflicting explicit
`runtimeMode` is rejected so the Variables view cannot report mixed execution semantics.

Fenced Java code receives embedded Java syntax highlighting. Compiling or executing it is a
separate, privileged action and is disabled by default. Trusted, isolated sessions may set
`"allowJavaCodeBlocks": true`; never enable it for untrusted formula authors.

`variables` values are typed JSON values and are injected into the real `CalculationContext`.
The same context values are used by the selected backend, the parity probe, and Debug Console
evaluation. Numeric values must be JSON numbers rather than quoted strings.
While stopped, injected values can be edited from VS Code's Variables view. The adapter preserves
the original number/boolean/string type, refreshes the runtime result, and uses the new value for
subsequent Debug Console evaluations.

## Extension settings

| Setting | Default | Description |
|---------|---------|-------------|
| `tinyExpressionP4Lsp.server.javaPath` | `java` | Path to Java 21+ executable |
| `tinyExpressionP4Lsp.server.jarPath` | *(bundled)* | Path to `tinyexpression-p4-lsp-server.jar` |
| `tinyExpressionP4Lsp.server.jvmArgs` | `[]` | Extra JVM arguments (e.g. `-Xmx512m`) |
| `tinyExpressionP4Lsp.runtimeMode` | `metadata` | Follow FormulaInfo metadata; plain formulas default to `p4-ast` |

`runtimeMode` selects execution semantics. `steppingMode` selects the structural view and is
normally `ast` for both P4 backends. AST mapping errors terminate the debug session explicitly;
there is no hidden token-stepping fallback.

## Supported file patterns

| Pattern | Example |
|---------|---------|
| `.tinyexp` extension | `formula.tinyexp` |
| `.formulainfo` extension | `rules.formulainfo` |
| FormulaInfo filename | `formulaInfo.txt`, `formula-info.txt` |
| `default` filename | `default` |
| `emergency` filename | `emergency` |
| `*.default` pattern | `formula.default` |
| `*.emergency` pattern | `formula.emergency` |

## Building from source

```bash
# Build the fat jar (Java 21 + Maven required)
cd tools/tinyexpression-p4-lsp-vscode
mvn package -DskipTests

# Install Node dependencies and compile TypeScript
npm install
npm run compile

# Package as VSIX
npm run package
```

## Architecture

```
UBNF grammar (tinyexpression-p4.ubnf)
  → unlaxer-dsl 3.0.14 code generation
  → TinyExpressionP4Parsers / AST (sealed interface) / Mapper / Evaluator
  → P4PreferredAstMapper        (preferred-root selection + compat parse)
  → TinyExpressionP4LanguageServerExt  (type-safe LSP, instanceof-based tokens)
  → TinyExpressionP4DebugAdapterExt    (DAP with AST node path)
  → tinyexpression-p4-lsp-server.jar   (fat jar, LSP + DAP)
```

The generic DAP protocol and AST/source-span traversal are generated from UBNF. TinyExpression's
hand-written adapter only supplies language-specific runtime binding through the generated
`runtimeVariables(...)` hook. Debug Console evaluation delegates to TinyExpression itself; it
does not contain a second arithmetic parser.

The module's vocabulary conformance test extracts word literals from the UBNF and compares them
with the static LSP completion vocabulary. An intentional omission must carry a reason in the
test allow-list. The extractor is kept independent of LSP internals so the same check can later
cover TextMate grammar scopes, DAP value vocabularies, and files bundled in the VSIX.

AST stepping is currently structural: the formula result and parity snapshot are evaluated with
the launch variables, while F10 changes the selected AST node. Per-node mutable runtime state and
reverse/time-travel debugging are not provided.

See `docs/TINYEXPRESSION-P4-PIPELINE-GUIDE.md` in the repository for a detailed
walkthrough of the UBNF → ParseTree → AST → Evaluator → LSP/DAP pipeline.
