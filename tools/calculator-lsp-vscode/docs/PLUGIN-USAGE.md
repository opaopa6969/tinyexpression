# TinyExpression LSP Plugin Usage

## 1. What this plugin does

- Launches a Java LSP server via stdio
- Enables syntax highlighting + diagnostics + completion for TinyExpression DSL files

## 2. Install

1. Build VSIX:
```bash
npm run package:vsix
```
2. Install in VS Code:
   - Extensions view
   - `...` menu
   - `Install from VSIX...`
   - Select generated `.vsix` in `build/`

## 3. Recommended file extension

1. Canonical: `.tinyexp`

Why `.tinyexp`:
- short and explicit for TinyExpression
- avoids collision with legacy plugin extensions

## 4. Workspace settings example

```json
{
  "tinyExpressionLsp.server.javaPath": "java",
  "tinyExpressionLsp.server.jarPath": "",
  "tinyExpressionLsp.server.jvmArgs": ["-Xmx512m"],
  "tinyExpressionLsp.catalog.path": "C:/catalogs/nimt-allowed-variables-cfvar.txt,C:/catalogs/nimt-allowed-variables-checkkind.txt",
  "tinyExpressionLsp.catalog.providerClass": "",
  "tinyExpressionLsp.runtimeMode": "token",
  "tinyExpressionLsp.fileExtensions": [".tinyexp"]
}
```

Notes:
- If `server.jarPath` is empty, bundled jar is used.
- Use an absolute `server.jarPath` when you manage server versions externally.
- Java 21+ is required for the bundled server.
- `catalog.path` is optional and enables TE024 partialKey diagnostics from external catalogs.
- `catalog.path` supports `${workspaceFolder}` and `~` expansion.

### 4.2 Catalog format for TE024

Supported:
1. Legacy format:
```text
# name|type|api|description
kind_*|string|catalog|partial key
age|number|catalog|exact
```
2. Canonical format (`.tecatalog` recommended):
```text
tinyexpression-catalog-v1
exact|age
prefixWithSuffix|kind|_|1
```

Convert legacy to canonical:
```bash
npm run catalog:convert -- /path/legacy-variables.txt > /path/catalog.tecatalog
```

Optional (advanced): custom runtime provider class
- JVM property: `-Dtinyexpression.catalog.provider.class=your.package.YourRuntimeCatalogProvider`
- env var: `TINYEXPRESSION_CATALOG_PROVIDER_CLASS=your.package.YourRuntimeCatalogProvider`
- interface: `org.unlaxer.calculator.RuntimeCatalogProvider`

Built-in sample provider (for quick local verification):
```json
{
  "tinyExpressionLsp.catalog.providerClass": "org.unlaxer.calculator.SampleInMemoryRuntimeCatalogProvider",
  "tinyExpressionLsp.server.jvmArgs": [
    "-Dtinyexpression.catalog.sample.partialPrefixes=kind"
  ]
}
```

## 4.1 Troubleshooting startup errors

If you see errors such as:
- `Pending response rejected since connection got disposed`
- `Client is not running and can't be stopped. It's current state is: startFailed`

check these first:
1. Open command palette and run `TinyExpression LSP: Show Server Output`.
2. Confirm Java path in settings:
   - `tinyExpressionLsp.server.javaPath`
3. Ensure Java is 21 or newer:
```bash
java -version
```
4. If needed, set explicit Java path:
```json
{
  "tinyExpressionLsp.server.javaPath": "/path/to/java"
}
```

## 5. Local development

```bash
npm install
npm run build:server
npm run compile
# Run Extension (F5) from VS Code
```

## 6. CI / release packaging

Use the packaging script:
```bash
bash scripts/package-vsix.sh
```

Options:
```bash
bash scripts/package-vsix.sh --skip-server
bash scripts/package-vsix.sh --out-dir ./dist
bash scripts/package-vsix.sh --matrix-three
```

`--matrix-three` creates:
1. `*-token.vsix`
2. `*-ast.vsix`
3. `*-dsl-javacode.vsix`

and settings presets under `build/runtime-mode-presets/`.
