# TinyExpression Design Backlog

Last updated: 2026-02-27

## 1. Parser Delimiter Coupling

Status: in progress

Context:
- `if (...)` / `if/*comment*/(...)` / `match { ... }` head判定は、runtime側でdelimiter知識を持つとdriftしやすい。
- `TinyExpressionParserCapabilities` で共通化は入ったが、`XXXDelimitedChain` の実装変更と完全連動ではない。

Target:
1. `JavaStyleDelimitedLazyChain` 系のdelimiter仕様を parser-owned contract として公開する。
2. evaluator/runtime 側は contract API のみ参照する。
3. delimiter仕様の変更時に unit test が先に落ちる構造にする。

## 2. Parser Keyword Coupling

Status: planned

Context:
- `var` / `variable` / 将来の別名（例: ローカライズ）を runtime 側が文字列で前提化すると追従コストが高い。
- `TinyExpressionKeywords` 導入済みだが、head判定APIはまだ parser capability として十分高レベルではない。

Target:
1. parser側に「宣言ヘッド/式ヘッド」を問い合わせる高レベル capability API を追加する。
2. evaluator 側の ad-hoc textual probe を順次置き換える。
3. alias 追加時に evaluator 側修正ゼロを目指す。

## 3. Evaluator Type Safety

Status: planned

Context:
- generated AST evaluation と embedded bridge の切替時に `Object` ベースの分岐が多く、型境界が緩い。

Target:
1. evaluator内部の結果表現を typed wrapper に寄せる（number/string/boolean/object + runtime mode）。
2. bridge混在時でも result-type contract を compile-time で追える設計へ寄せる。
3. DAP probe も typed metadata 由来へ統一する。

## 4. Error Message Design

Status: in progress

Context:
- 現在の parse/eval エラーは文脈情報が不足し、運用時の原因追跡が重い。
- 単純な `Choice(rightExpression, errorMessage)` だけでは情報量が足りない可能性がある。

Target:
1. parser段階で期待トークン/位置/候補 rule を構造化して保持する。
2. evaluator段階で runtime mode（generated/token/bridge）を添えて報告する。
3. LSP diagnostics と DAP variables の双方で同じ error envelope を使う。

Progress snapshot (2026-02-26):
- `unlaxer-common` に `ParseFailureDiagnostics` が入り、farthest failure + stack + expected hints を取得可能。
- terminal expected provider を parser基盤へ展開:
  - `TerminalSymbol#expectedDisplayTexts()`（複数候補対応）
  - `WordParser` / `SingleCharacterParser` の既定ヒント
  - `SuggestableParser` の全候補ヒント（tinyExpressionの `if` / `match` / 関数名 parser 群を含む）
  - `SingleStringParser` の単一文字自動ヒント（`<`, `>`, `:`, `?`, `$` 等）
- expected hint を構造化:
  - `ParseFailureDiagnostics.ExpectedHintCandidate`
  - `displayHint` + parser metadata（class/depth/terminal）をLSPで利用可能
- TinyExpression LSP は expected hint を使って `missing ',' before default case` などの具体メッセージを返す状態。

Progress snapshot (2026-02-27):
- `unlaxer-common` に `ErrorMessageParser.expected(String)` を追加。
- これは `Choice(rightParser, ErrorMessageParser.expected(\"expected: ...\"))` のように使える「失敗専用 expected-hint 注入 parser」。
- 既存 `new ErrorMessageParser(...)`（成功しつつ ErrorMessage token を埋め込む fallback 挙動）は互換維持。

## 5. Priority (next design sessions)

1. parser delimiter contract formalization
2. keyword/head capability API formalization
3. error envelope schema for LSP/DAP
4. evaluator typed-result internal model

## 6. Dependency Upgrade: unlaxer-common Latest

Status: in progress

Context:
- current TinyExpression/DSL integration is pinned to a compatibility set where runtime behavior is validated.
- latest `unlaxer-common` adoption is requested as a follow-up task.

Target:
1. upgrade `unlaxer-common` to latest available release.
2. record compatibility matrix (`tinyExpression` / `unlaxer-dsl` / LSP server) and required adaptation points.
3. keep parity checks across all TinyExpression backends and LSP/DAP smoke paths after upgrade.

Validation snapshot (2026-02-26):
- upgraded target version to `unlaxer-common 2.3.0`.
- `calculator-lsp-vscode/server` builds with `2.3.0`.
- `tinyexpression` main module fails to compile due API deltas (examples: `Token#getToken`, `tokenString/tokenRange`, `RangedString`, `StringSource(String)` ctor, `TokenList` typing changes, transaction listener signatures).
- migration task needs an adapter/refactor phase before parity verification can continue.

Validation snapshot (2026-02-27, plugin runtime):
- `tinyexpression-lsp` server dependency aligned to `tinyExpression 1.4.10` + `unlaxer-common 2.4.0`.
- resolved runtime ABI crash: `NoSuchMethodError: org.unlaxer.TypedToken.flatten()Ljava/util/List;`.
- user sample formula (`import` + `var` + `if` + `match`) now parses fully in shaded server jar probe.

## 7. VS Code Native DAP Integration

Status: planned (queued)

Context:
- current VSIX provides LSP integration and runtimeMode switching, but no `contributes.debuggers` entry.
- DAP behavior is currently available as runtime probe (`TinyExpressionDapRuntimeBridge`) rather than VS Code debug session wiring.

Target:
1. add VS Code debugger contribution (`contributes.debuggers`) and launch schema for TinyExpression.
2. wire extension-side debug adapter launch in `src/extension.ts` (token/ast/dsl-javacode runtimeMode selectable).
3. package debug adapter runtime artifacts in VSIX and verify `stop/next/continue/variables/stackTrace`.

## 8. Deepest Parse Failure Position API (unlaxer-common)

Status: planned (queued)

Context:
- current parse failures can report `Ln1,col1` when backtracking rewinds consumed/matched cursor positions.
- LSP diagnostics need stable "farthest failure offset + expected token/rule" to point to real syntax errors (for example missing comma before `default` in `match`).
- preserving a deepest parser-stack snapshot (`maxReachedStackElements`) is preferred over offset-only tracking for richer diagnostics.

Target:
1. add parser-level API in `unlaxer-common` to expose deepest/farthest failure position independent from final backtracked cursor.
2. include failure metadata (`offset`, `line`, `column`, expected terminals/rules, `maxReachedStackElements`) in a structured diagnostic object.
3. adapt TinyExpression parser/LSP to use this API and remove heuristic position guessing where possible.

## 9. Base Combinator First-Class Metadata

Status: planned (queued)

Context:
- intended scope is not limited to error messages.
- goal is to mark base combinators with first-class semantic metadata (`@FirstClass` style) so tooling can distinguish structural roles explicitly.
- some combinators need dynamic runtime metadata (cannot be fully static annotation-only).

Target:
1. define first-class metadata for base combinators (annotation and/or marker contract).
2. support hybrid resolution:
static annotation metadata + dynamic runtime provider for context-dependent cases.
3. apply this metadata to parser analysis, diagnostics, and future tooling contracts (LSP/DAP/mapper).

## 10. If/Else Grammar Decision (`else if`)

Status: planned (queued)

Context:
- current grammar expects `else { ... }` and does not accept `else if (...) { ... }` directly.
- current LSP diagnostics now reports `expected '{'`, which is correct for current grammar but may be surprising for users.

Target:
1. decide language spec:
   - keep strict style (`else { if(...) { ... } }`) or
   - support direct `else if (...)`.
2. if direct `else if` is adopted, update parser + AST mapper + evaluators (all runtimes) + codegen parity tests.
3. update docs/examples and LSP completion snippets to match final spec.

## 11. ErrorMessage Fallback Rollout

Status: planned (queued)

Context:
- `unlaxer-common` now has `ErrorMessageParser.expected(...)` (fail-only expected-hint injection).
- TinyExpression grammar has not yet systematically adopted this mechanism.

Target:
1. identify high-value grammar points (if/match/var/external/import) where fallback hints should be embedded directly.
2. replace ad-hoc LSP string heuristics with grammar-owned hints where possible.
3. keep compatibility with existing `ErrorMessageParser` success-token mode.

## 12. Diagnostic Rule Hardening (Reduce Heuristic Drift)

Status: planned (queued)

Context:
- current LSP diagnostic improvements rely on text heuristics (`if`, `match`, `->`, braces, semicolon).
- behavior is improved but can still drift when grammar/combinators evolve.

Target:
1. prefer parser metadata and expected-hint candidates over raw text scanning for final message selection.
2. introduce deterministic prioritization policy:
   - structural missing token (`)`, `{`, `}`, `;`)
   - missing expression (`-> rhs`)
   - fallback expected-set
3. add regression cases for noisy patterns:
   - `if(true`
   - missing `{` after `if/else/match`
   - missing `}` in nested `if/match`
   - `match` arrow RHS missing
   - trailing comma before `}`.

## 13. TE Error Catalog Integration

Status: in progress

Context:
- teammate-provided catalog (`TE001`-`TE024`) should be surfaced from LSP diagnostics.
- parser-only detection cannot cover semantic-only items (`TE021`/`TE022`/`TE024` and part of type/arity checks) without analyzer support.

Target:
1. introduce diagnostic mapping layer in LSP:
   - parse failure message -> `TE` code + user message + fix hint.
2. implement parser-feasible subset first:
   - `TE004` `TE005` `TE006` `TE009` `TE010` `TE013` `TE014` `TE020`.
3. semantic analyzer phase for unresolved entries:
   - `TE011` `TE015` `TE021` `TE022` `TE023` `TE024`.
   - `TE001` `TE012` are marked **N/A** for current TinyExpression spec
     because `if` expression is not number-only (boolean/string/number branches are allowed by grammar/runtime).
4. expose `Diagnostic.code = TE###` and keep message Japanese-friendly.

Progress snapshot (2026-02-27):
- backlog item created and parser-feasible subset implementation started in `CalculatorLanguageServer`.
- parser-phase mapping implemented (initial):
  - `TE004` `TE005` `TE006` `TE009` `TE010` `TE013` `TE014` `TE020` `TE022` (tentative for undeclared/untyped variable message).
- parser-phase catalog mapping extended:
  - `TE002` bare identifier,
  - `TE003` string quote invalid,
  - `TE007` description syntax invalid,
  - `TE008` invalid full-width punctuation,
  - `TE016` import declaration invalid,
  - `TE017` variable declaration invalid,
  - `TE018` type hint position invalid,
  - `TE019` get/orElse syntax invalid.
- LSP now sets `Diagnostic.code = TE###` and emits catalog-style user message for mapped parse failures.
- quick fix implemented for parser-safe bracket/semicolon cases:
  - `TE004` => insert `)`
  - `TE005` => insert `}`
  - `TE006` => insert `;`
- parser-phase `TE011` mapping is now added for if-condition errors:
  - missing `(` after `if`
  - missing `)` in condition
  - empty condition `if()`
  - implementation point: `CalculatorLanguageServer.describeIfConditionIssue(...)` + catalog mapping in `resolveErrorCatalogEntry(...)`.
  - verified by `CalculatorErrorCatalogMappingTest` (2 cases).
- parser-phase `TE015` mapping is now added for `min/max` arity issues:
  - detects empty argument segment and non-2-argument forms around nearest `min(...)`/`max(...)`,
  - implementation point: `CalculatorLanguageServer.describeMinMaxArityIssue(...)` + catalog mapping in `resolveErrorCatalogEntry(...)`.
- parser-phase `TE023` mapping is now added for operator/notation mistakes:
  - detects `&&` / `||` misuse (TinyExpression expects single-char `&` / `|`),
  - detects missing RHS after boolean operator (`&)` / `|}` etc.),
  - detects `$method(...)` notation misuse and suggests removing `$`.
  - implementation point: `CalculatorLanguageServer.describeOperatorNotationIssue(...)` + catalog mapping in `resolveErrorCatalogEntry(...)`.
- lightweight semantic `TE021` mapping is now added in LSP analyzer phase:
  - scans invocation heads and reports unknown method calls not found in:
    - parser-definition-derived method catalog (`TinyExpressionParserMethodCatalog`),
    - `import ... as alias;` aliases,
    - declared method names in current document.
  - emits `[TE021] ...` diagnostics with closest-candidate hint (`候補: ...`) and propagates `Diagnostic.code` via catalog-prefix extraction.
- quick fix integration expanded:
  - `TE021`: rename unknown method to suggested candidate (`候補: ...`) as replacement edit.
  - `TE023`: operator/notation quick fixes for `&& -> &`, `|| -> |`, and `$method(...)` -> `method(...)` (remove leading `$`).
- unresolved:
  - full-precision semantic validation still has gaps (advanced `TE011` boolean-shape validation / advanced `TE015` signature validation / advanced `TE021`/`TE022`/`TE023` context validation beyond lightweight heuristics).
  - `TE001` / `TE012`: **N/A (not supported by design)**.

## 14. TE024 Catalog Externalization and Generalization

Status: in progress

Decision (2026-02-27):
- do not embed partialKey catalog data in VSIX.
- proceed with incremental migration:
  1. adapter-first (consume existing teammate catalog format as-is),
  2. define canonical generalized schema,
  3. add converter from legacy format to canonical format.

Scope:
1. introduce `CatalogProvider` abstraction on LSP/server side.
2. allow external path injection (workspace/user/env/JVM property) without packaging catalog payload in extension artifacts.
3. add legacy-format adapter for current `*_allowed_variables*.txt` (`prefix_*` partialKey style).
4. define canonical schema for symbol rules:
   - `exact`
   - `prefixWithSuffix` (configurable delimiter, default `_`)
   - extensible rule kinds (future: regex/enum).
5. implement optional converter CLI:
   - legacy catalog -> canonical catalog.
6. externalization source options backlog:
   - workspace local file path (`tinyExpressionLsp.catalog.path`),
   - user-global file path (VSCode user settings),
   - env/JVM property injection for CI/server (`TINYEXPRESSION_CATALOG_PATH`, `-Dtinyexpression.catalog.path=...`),
   - future API/DB provider via `CatalogProvider` implementation swap.

Acceptance:
1. TE024 keeps existing behavior parity for legacy catalogs.
2. canonical schema can express current partialKey behavior without loss.
3. switching provider source does not require VSIX rebuild.

Progress snapshot (2026-02-27):
- implemented provider/adapter structure in `TinyExpressionVariableCatalog`:
  - `CatalogProvider`,
  - `CompositeCatalogProvider`,
  - `LegacyCatalogAdapter`,
  - `CanonicalCatalogAdapter`.
- added runtime extension hook for non-file sources:
  - `org.unlaxer.calculator.RuntimeCatalogProvider`,
  - selected via `tinyexpression.catalog.provider.class` / `TINYEXPRESSION_CATALOG_PROVIDER_CLASS`.
- added VSCode-side setting pass-through for provider class:
  - `tinyExpressionLsp.catalog.providerClass` -> `-Dtinyexpression.catalog.provider.class=...`.
- added built-in sample provider:
  - `org.unlaxer.calculator.SampleInMemoryRuntimeCatalogProvider`.
- added canonical catalog v1 support:
  - marker: `tinyexpression-catalog-v1`,
  - rule lines: `exact|name`, `prefixWithSuffix|prefix|_|1`.
- kept legacy catalog compatibility (`name|type|api|description`, `prefix_*` partial key).
- TE024 analyzer now consumes loaded catalog rules and emits `[TE024]` diagnostics for `$prefix` usage.
- added TE024 quick fix in LSP: append `_<suffix>` for partialKey variable references.
- added catalog-backed semantic TE022 in analyzer:
  - when external catalog is loaded, undefined variable references emit `[TE022] ...` with closest candidate hint,
  - LSP quick fix now supports TE022 variable rename (`候補: $...`).
- reduced diagnostic noise for structural mismatch fallback:
  - TE010 messages no longer append parser detail tail by default,
  - missing-semicolon (`TE006`) detection for `var` declarations now includes a line-based fallback path when parse start offset rewinds to the file head,
  - global structural fallback now checks missing block/match braces (`expected '{'` / `expected '}'`) before generic `unexpected characters`.
- added converter script:
  - `tools/calculator-lsp-vscode/scripts/convert-legacy-catalog-to-canonical.sh`,
  - `npm run catalog:convert -- <legacy-files...>`.
- VSCode client now resolves catalog path tokens before launch:
  - `${workspaceFolder}` and `~` expansion in `tinyExpressionLsp.catalog.path`.
