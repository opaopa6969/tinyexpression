# TinyExpression P4 LSP/DAP — VS Code Extension

VS Code extension that provides Language Server (LSP) and Debug Adapter (DAP) support
for TinyExpression formulas using the P4 grammar (UBNF-generated, type-safe).

## Features

- **Syntax highlighting** — keywords, variables (`$name`), numbers, strings, operators, comments
- **Semantic tokens** — type-safe classification via Parser `instanceof` (no regex)
- **Diagnostics** — TE001 parse errors with offset and source snippet
- **Completion** — P4 keywords + `$variable` scan
- **Hover** — AST node type display on parse success
- **Debug (DAP)** — step execution with P4 runtime markers in Variables panel:
  - `_tinyP4ParserUsed` — whether the P4 grammar parsed the formula
  - `_tinyP4AstNodeType` — sealed-interface record type of the AST root
  - `_tinyP4AstNodePath` — breadth-first path through the AST
  - `parity.*` — 6-backend evaluation comparison (JAVA_CODE / AST_EVALUATOR / DSL_JAVA_CODE / P4_AST / P4_DSL)

## Requirements

- Java 21+
- VS Code 1.85+

## Quick start

1. Install this extension (`.vsix` install or from Marketplace)
2. Open a `.tinyexp` file — the LSP server starts automatically
3. To debug a formula: open `Run and Debug`, select **Debug TinyExpression P4**, press `F5`

## Extension settings

| Setting | Default | Description |
|---------|---------|-------------|
| `tinyExpressionP4Lsp.server.javaPath` | `java` | Path to Java 21+ executable |
| `tinyExpressionP4Lsp.server.jarPath` | *(bundled)* | Path to `tinyexpression-p4-lsp-server.jar` |
| `tinyExpressionP4Lsp.server.jvmArgs` | `[]` | Extra JVM arguments (e.g. `-Xmx512m`) |
| `tinyExpressionP4Lsp.runtimeMode` | `p4-ast` | Execution backend: `p4-ast` or `p4-dsl-javacode` |

## Supported file patterns

| Pattern | Example |
|---------|---------|
| `.tinyexp` extension | `formula.tinyexp` |
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
  → unlaxer-dsl code generation
  → TinyExpressionP4Parsers / AST (sealed interface) / Mapper / Evaluator
  → TinyExpressionP4LanguageServerExt  (type-safe LSP, instanceof-based tokens)
  → TinyExpressionP4DebugAdapterExt    (DAP with AST node path)
  → tinyexpression-p4-lsp-server.jar   (fat jar, LSP + DAP)
```

See `docs/TINYEXPRESSION-P4-PIPELINE-GUIDE.md` in the repository for a detailed
walkthrough of the UBNF → ParseTree → AST → Evaluator → LSP/DAP pipeline.
