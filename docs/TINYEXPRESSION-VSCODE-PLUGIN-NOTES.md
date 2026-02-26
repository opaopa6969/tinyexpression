# TinyExpression VS Code Plugin Notes

Last updated: 2026-02-26

## 1. File Extension Recommendation

1. canonical: `.texpr`
2. additional: `.tinyexpr`
3. legacy compatibility: `.calc`

Rationale:
1. `.texpr` is short and explicit for TinyExpression.
2. `.calc` remains for backward compatibility with existing files.

## 2. VSIX Packaging

Current helper script (in `calculator-lsp-vscode` repo):
- `scripts/package-vsix.sh`

Capabilities:
1. normal package build
2. skip server rebuild (`--skip-server`)
3. output directory selection (`--out-dir`)
4. three-variant distribution artifacts for runtime/DAP split (`--matrix-three`)

## 3. Three-variant Distribution

When DAP/runtime mode is distributed separately, package artifacts can be split as:
1. `token`
2. `ast`
3. `dsl-javacode`

`--matrix-three` currently emits these three filenames and matching settings presets.
