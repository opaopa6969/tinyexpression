# TinyExpression VS Code Plugin Notes

Last updated: 2026-02-27

## 1. File Extension Recommendation

1. canonical: `.tinyexp`

Rationale:
1. `.tinyexp` is explicit for TinyExpression.
2. avoids extension collision with older plugins.

## 2. VSIX Packaging

Current helper script (in `calculator-lsp-vscode` repo):
- `scripts/package-vsix.sh`

TinyExpression workspace wrapper script:
- `scripts/package_calculator_lsp_vsix.sh`
- default target project:
  - `../calculator-lsp-vscode` (preferred)
  - fallback: `tools/calculator-lsp-vscode` (vendored copy)

Capabilities:
1. normal package build
2. skip server rebuild (`--skip-server`, default)
3. rebuild bundled server jar (`--with-server-build`)
4. output directory selection (`--out-dir`)
5. three-variant distribution artifacts for runtime/DAP split (`--matrix-three`)
6. post-package runtime dependency injection (`node_modules`) for stable offline VSIX

Run from `tinyexpression` root:
1. default build:
   - `./scripts/package_calculator_lsp_vsix.sh`
2. three artifacts (`token`/`ast`/`dsl-javacode`):
   - `./scripts/package_calculator_lsp_vsix.sh --matrix-three`
3. explicit plugin directory:
   - `./scripts/package_calculator_lsp_vsix.sh --plugin-dir /path/to/calculator-lsp-vscode`

Default output directory:
- `build/vsix`
- output filename prefix:
  - `tinyExpression-...`

## 3. Three-variant Distribution

When DAP/runtime mode is distributed separately, package artifacts can be split as:
1. `token`
2. `ast`
3. `dsl-javacode`

`--matrix-three` currently emits these three filenames and matching settings presets.

## 4. Current Extension Metadata

1. extension id:
   - `opaopa6969.tinyexpression-lsp`
2. display name:
   - `TinyExpression DSL (LSP)`
3. publisher:
   - `opaopa6969`
4. author/homepage:
   - `unlaxer.org` / `https://unlaxer.org`
5. runtime preset key:
   - `tinyExpressionLsp.runtimeMode`

## 5. 0.2.6 Compatibility Fix

Symptoms seen in `0.2.3`-`0.2.5`:
1. server crash loop on open (`Pending response rejected since connection got disposed`)
2. runtime linkage error:
   - `NoSuchMethodError: org.unlaxer.TypedToken.flatten()Ljava/util/List;`
3. valid TinyExpression formulas could be reported as invalid with noisy expected-hint diagnostics.

Root cause:
1. LSP server used `tinyExpression 1.4.6` together with `unlaxer-common 2.4.0`.
2. this pair has ABI mismatch around `Token/TypedToken.flatten()` return type.

Fix in `0.2.6`:
1. set server dependency to `tinyExpression 1.4.10` (compatible with `unlaxer-common 2.4.0`).
2. rebuild and re-bundle `server-dist/tinyexpression-lsp-server.jar`.
3. keep parse-failure diagnostics compatibility path in server to avoid hard class linkage (`ParseFailureDiagnostics` reflection path).

Validation:
1. shaded jar probe with formula containing `import`/`var`/`if`/`match` parses fully:
   - `succeeded=true, consumed=239/239`

## 6. 0.2.8 Diagnostic Noise Reduction

Changes:
1. missing `;` after `var` declaration is normalized to:
   - `Invalid expression: missing ';' after var declaration`
2. missing `{` after block heads (`if (...)`, `else`, `match`) is normalized to:
   - `Invalid expression: expected '{'`
3. for primary messages starting with `expected '...'`, extra `Expected hints: ...` suffix is suppressed.

## 7. 0.2.9 Diagnostic Tightening

Changes:
1. `if(true` now reports:
   - `Invalid expression: expected ')'`
2. `match ...` without `{` now reports:
   - `Invalid expression: expected '{'`
3. missing `}` around `if/else/match` blocks (including `}else{` missing right brace and `else{...` unclosed) now reports:
   - `Invalid expression: expected '}'`

## 8. 0.2.10 Match/If Follow-up

Changes:
1. `match` case with missing right expression after `->` now reports:
   - `Invalid expression: expected expression after '->'`
2. trailing comma before closing brace in `match` now reports:
   - `Invalid expression: unexpected trailing ',' before '}'`
3. `missing ',' before default case` is now emitted only when comma omission is actually detected.
4. `if(true` keeps focused diagnostic:
   - `Invalid expression: expected ')'`

## 9. 0.2.11 Quick Fix (TE004/TE005/TE006)

Changes:
1. LSP `CodeAction` (quick fix) enabled.
2. quick fixes added for:
   - `TE004`: insert `)`
   - `TE005`: insert `}`
   - `TE006`: insert `;`
3. these fixes are offered from diagnostics with matching `Diagnostic.code`.

## 10. Session Handover (2026-02-27)

Current implemented (source level):
1. catalog-driven completion/validation uses bundled `config/*.txt` by default when `catalog.path` is empty.
2. `description` is shown as `hint:` in `$` completion detail.
3. legacy wildcard `foo_*` is treated as partial key:
   - `$foo_<anything>` allowed
   - `$foo` alone -> `TE024`
4. completion duplicate-dollar issue fixed (`$` completion now replaces current `$...` token range instead of appending).
5. extension language activation expanded:
   - extensionless `default`, `emergency`
   - `*.default`, `*.emergency`
6. FormulaInfo-style document support added:
   - parse/diagnose/hover/completion target only `formula:` ... `---END_OF_PART---` ranges
   - multiple formula sections in one file supported
   - if no `formula:` section exists, fallback remains full-document parse (legacy behavior).

Important next step:
1. re-package VSIX from latest source (new version tag) after formula-section changes, then smoke-test on:
   - extensionless `default` / `emergency`
   - multi-part FormulaInfo file with 2+ `formula:` sections
   - quickfix locality when multiple formula sections exist.
