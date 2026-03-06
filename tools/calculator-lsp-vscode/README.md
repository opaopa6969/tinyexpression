# TinyExpression DSL (LSP) - VS Code Extension

This is a minimal VS Code extension that launches a Java LSP server (LSP4J) via stdio.

## What is included
- VS Code client (TypeScript) using `vscode-languageclient`
- Minimal syntax highlighting for `.tinyexp`
- A Maven module (`server/`) that builds a runnable jar for the LSP server
- A helper script that copies the built jar into `server-dist/`

## Quick start (dev)
1. Install prerequisites:
   - Node.js (LTS)
   - Java 21+
   - Maven

2. Install extension dependencies:
   ```bash
   npm install
   ```

3. Build the language server jar and copy it into the extension:
   ```bash
   npm run build:server
   ```

4. In VS Code, press `F5` to start the Extension Development Host.

5. Create a file `demo.tinyexp` and type expressions like:
   ```
   1 + 2 * (3 + 4)
   ```

## Packaging to .vsix
```bash
npm run package
```

Recommended (reproducible script, builds server + compile + package):
```bash
npm run package:vsix
```

Optional flags:
```bash
# package without rebuilding server jar
bash scripts/package-vsix.sh --skip-server

# custom output directory
bash scripts/package-vsix.sh --out-dir ./dist

# also create token/ast/dsl-javacode distribution variants
bash scripts/package-vsix.sh --matrix-three
```

## Catalog setup in VS Code (TE022/TE024)
1. Open VS Code Settings.
2. Search `TinyExpression LSP: Catalog Path`.
3. Set one or more catalog files:
   - `${workspaceFolder}/catalog/nimt.tecatalog`
   - `${workspaceFolder}/catalog/fa.tecatalog`
   - Multiple files can be joined with `,`.
4. Reopen `.tinyexp` files (or run `Developer: Reload Window`).

See details:
- [docs/PLUGIN-USAGE.md](docs/PLUGIN-USAGE.md)
- [docs/CATALOG-PROVIDER-EXTENSION.md](docs/CATALOG-PROVIDER-EXTENSION.md)

## Configuration
- `tinyExpressionLsp.server.javaPath`: path to java executable (default: `java`)
- `tinyExpressionLsp.server.jarPath`: optional path to an external server jar. If empty, uses the bundled jar in this extension.
- `tinyExpressionLsp.server.jvmArgs`: extra JVM args (e.g. `-Xmx512m`)
- `tinyExpressionLsp.runtimeMode`: runtime mode hint (`token` / `ast` / `dsl-javacode`)
- `tinyExpressionLsp.catalog.path`: external variable catalog path(s) for TE024 partialKey checks (comma-separated). The extension forwards this as `-Dtinyexpression.catalog.path=...` to the server.
  - supports `${workspaceFolder}` and `~` expansion on the client side.
  - when configured, analyzer also enables catalog-backed TE022 undefined-variable diagnostics/suggestions.
  - `$` completion also uses this catalog and shows context/description in completion detail.
  - TE022 suggestion ranking also uses document context hints (`tags/context/...`) when available.
- `tinyExpressionLsp.catalog.useBundledDefault`: when `true` (default), use bundled `catalog/default.tecatalog` if `catalog.path` is empty.
  - Current default behavior prefers bundled `config/*.txt` catalogs:
    - `config/nimt-allowed-variables-cfvar.txt`: NIM CF variable catalog
    - `config/nimt-allowed-variables-checkkind.txt`: NIM checkKind-derived variable catalog
    - `config/fa-allowed-variables-cf-variable.txt`: FA CF variable catalog
    - `config/fa-allowed-variables-checkkind.txt`: FA checkKind-derived variable catalog
  - If those files are unavailable, it falls back to `catalog/default.tecatalog`.
- `tinyExpressionLsp.catalog.providerClass`: optional runtime provider class (`org.unlaxer.calculator.RuntimeCatalogProvider`).
- `tinyExpressionLsp.fileExtensions`: file extensions watched by the extension (default: `.tinyexp`)

### TE024 catalog format (external file)
Supported formats:
- legacy format (existing teammate format): `name|type|api|description` with partial key as `prefix_*`
  - `prefix_*` means wildcard suffix: `$prefix_<anything>` is valid, `$prefix` alone triggers TE024.
- canonical format (v1):
  - marker line: `tinyexpression-catalog-v1` (optional but recommended)
  - `exact|name|description(optional)|context(optional)`
  - `prefixWithSuffix|prefix|_|1|description(optional)|context(optional)`

Example:
```text
# tinyexpression canonical catalog
tinyexpression-catalog-v1
exact|age|age in years|nimt
prefixWithSuffix|kind|_|1|partial key|fa
```

If context column is omitted, context is inferred from catalog file name tokens (`nimt` / `fa` etc).

Convert legacy to canonical:
```bash
npm run catalog:convert -- /path/nimt-allowed-variables-cfvar.txt > /tmp/nimt.tecatalog
```

Server-side direct launch also supports:
- JVM property: `-Dtinyexpression.catalog.path=/path/a.tecatalog,/path/b.txt`
- env var: `TINYEXPRESSION_CATALOG_PATH=/path/a.tecatalog,/path/b.txt`
- custom provider class:
  - JVM property: `-Dtinyexpression.catalog.provider.class=your.package.YourRuntimeCatalogProvider`
  - env var: `TINYEXPRESSION_CATALOG_PROVIDER_CLASS=your.package.YourRuntimeCatalogProvider`
  - class must implement `org.unlaxer.calculator.RuntimeCatalogProvider`

Built-in sample provider:
- class: `org.unlaxer.calculator.SampleInMemoryRuntimeCatalogProvider`
- options:
  - `-Dtinyexpression.catalog.sample.exactNames=age,score`
  - `-Dtinyexpression.catalog.sample.partialPrefixes=kind,segment`
  - `-Dtinyexpression.catalog.sample.fallbackPaths=/path/a.tecatalog,/path/b.txt`

## Troubleshooting startup
- Open command: `TinyExpression LSP: Show Server Output`
- Verify Java:
  - `tinyExpressionLsp.server.javaPath`
  - Java 21+ (`java -version`)
- Typical symptoms when startup fails:
  - `Pending response rejected since connection got disposed`
  - `Client is not running and can't be stopped ... startFailed`

## File extension policy
- recommended canonical extension: `.tinyexp`
- extensionless filenames `default` / `emergency` are also auto-detected as TinyExpression

## Notes for WSL / Windows
- If you develop in WSL but run VS Code on Windows, prefer launching the server jar with a Windows-side Java, or set `tinyExpressionLsp.server.jarPath` to a jar reachable from Windows.


## Architecture (one picture)
![architecture](docs/architecture.png)

## Quick try (no build of the server required for users)
If you already have the `.vsix`, install it via:
- Extensions view → `...` → **Install from VSIX...**

Then open a `.tinyexp` file. The extension will launch the bundled server jar via stdio.

## Development workflow
```bash
npm install
npm run build:server
npm run compile
# VS Code: Run and Debug → "Run Extension" (F5)
```

## Plugin usage guide
Detailed usage and integration guide:
- [docs/PLUGIN-USAGE.md](docs/PLUGIN-USAGE.md)
- [docs/CATALOG-PROVIDER-EXTENSION.md](docs/CATALOG-PROVIDER-EXTENSION.md)
- [docs/DIAGNOSTIC-QUICKFIX-SPEC-ja.md](docs/DIAGNOSTIC-QUICKFIX-SPEC-ja.md)

## Adding a new function (one place)
Functions are defined in their own parsers in `server/src/main/java/.../CalculatorParsers.java`:

```java
public static class SineFunctionParser extends WordParser implements FunctionSuggestable {
    public SineFunctionParser() {
        super("sin");
    }

    @Override
    public FunctionCompletion getFunctionCompletion() {
        return new FunctionCompletion("sin", "Sine function", "sin($1)");
    }
}
```

Register the parser class in `getFunctionParserClasses()`. That drives:
- Grammar (`FunctionNameParser`)
- Completion (`SuggestableParser`)
- Documentation

## Diagnostics: show expected tokens
When parsing fails, the server publishes diagnostics. If unlaxer provides “expected tokens” metadata, the server adds it to the diagnostic message (best-effort extraction).

### TE021-TE024 Quick Fix kinds
- TE021/TE022 rename fixes use `quickfix.rewrite`:
  - unknown method -> candidate method name
  - unknown variable -> candidate variable name
- TE023 (`演算子/記法が不正です`) returns subtype-aware quick fixes.
- Rewrite fixes use `quickfix.rewrite`:
  - `&&` -> `&`
  - `||` -> `|`
  - `$method(...)` -> `method(...)`
- Missing RHS completion uses `quickfix.insert`:
  - `a &` / `a |` -> append ` true` on the right-hand side.
- TE024 (`partialKey` suffix不足) uses `quickfix.insert`:
  - `$kind` -> `$kind_<suffix>`
- Server capability advertises `quickfix`, `quickfix.rewrite`, and `quickfix.insert` from `initialize`.
