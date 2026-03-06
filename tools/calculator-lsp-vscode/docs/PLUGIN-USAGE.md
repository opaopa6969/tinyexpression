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
2. Also supported: extensionless `default`, `emergency`, and `*.default` / `*.emergency`

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
  "tinyExpressionLsp.catalog.useBundledDefault": true,
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
- `catalog.path` is also used for `$` completion entries (detail includes context + description).
- `catalog.path` supports `${workspaceFolder}` and `~` expansion.
- if `catalog.path` is empty and `catalog.useBundledDefault=true`, bundled catalogs are used in this order:
  - `config/nimt-allowed-variables-cfvar.txt`
  - `config/nimt-allowed-variables-checkkind.txt`
  - `config/fa-allowed-variables-cf-variable.txt`
  - `config/fa-allowed-variables-checkkind.txt`
- if above files are unavailable, fallback is `catalog/default.tecatalog`.

### 4.2 Catalog format for TE024

Supported:
1. Legacy format:
```text
# name|type|api|description
kind_*|string|catalog|partial key
age|number|catalog|exact
```
`foo_*` means wildcard suffix: `$foo_<anything>` is allowed, while `$foo` alone is treated as TE024.
2. Canonical format (`.tecatalog` recommended):
```text
tinyexpression-catalog-v1
exact|age|age in years|nimt
prefixWithSuffix|kind|_|1|partial key variable|fa
```

`$` completion detail shows context + description. If context column is omitted, file name tokens (e.g. `nimt`, `fa`) are used as context inference.

### 4.3 Bundled config files

- `nimt-allowed-variables-cfvar.txt`: NIM product CF variable allow-list (`variable|type|api|description`).
- `nimt-allowed-variables-checkkind.txt`: NIM checkKind-derived variable candidates (`variable|api|description`).
- `fa-allowed-variables-cf-variable.txt`: FA product CF variable allow-list (`variable|type`).
- `fa-allowed-variables-checkkind.txt`: FA checkKind-derived variable candidates (`variable|description`).

Convert legacy to canonical:
```bash
npm run catalog:convert -- /path/legacy-variables.txt > /path/catalog.tecatalog
```

### 4.4 TE022 suggestion context hints

When TE022 (`利用可能な変数名ではありません`) suggests candidate variables, the server applies a context bias if it detects context hints in the document.

Supported explicit forms (preferred over free-text tokens):
- `tags: FA`
- `context = "NIM"`
- `tenant='NIM'`
- `"context": "FA"` (JSON-like)
- `'context': 'FA'` (JSON-like single-quote style)
- `tags = core,FA,ops` (list-style value)

Behavior notes:
- If multiple explicit hints exist, the first one in document order is used.
- If one explicit value includes multiple recognized tokens (for example `tags=core,NIM,FA`), the first recognized token is used.
- If the first explicit hint has no recognized token, the next explicit hint is evaluated.
- Explicit hints that appear only in commented lines are skipped; later explicit hints can still be used.
- If an explicit value contains no recognized token, context bias falls back to free-text hints.
- If explicit hints exist only in comments, they are ignored and context bias falls back to free-text hints.
- Tokens inside inline/block comment fragments within explicit values are ignored (`// ...`, `/* ... */`).
- Unterminated block-comment tails in explicit values are also ignored from `/*` onward.
- String/comment regions are ignored for context extraction.
- Variable-like tokens (for example `context=$fa`) are not treated as context hints.
- Inline comment tails (for example `tags:NIM // FA`) do not change context bias.

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

## 7. TE021-TE024 Quick Fix visibility

- TE021/TE022 (rename fixes) are emitted as `quickfix.rewrite`:
  - TE021: unknown method -> candidate method name
  - TE022: unknown variable -> candidate variable name

- TE023 (`演算子/記法が不正です`) provides categorized quick fixes:
  - `quickfix.rewrite`: `&&` -> `&`, `||` -> `|`, `$method(...)` -> `method(...)`
  - `quickfix.insert`: missing RHS after `&` / `|` -> insert ` true`
- TE024 (`partialKey` suffix不足) quick fix is also `quickfix.insert`:
  - `$kind` -> `$kind_<suffix>`
- The server advertises `quickfix`, `quickfix.rewrite`, and `quickfix.insert` in `initialize` capabilities, so VS Code can group and filter these actions consistently.
