# TinyExpression Design Backlog

Last updated: 2026-03-20

---

## バックログ項目テンプレート

新規項目は以下の構造で記述する。`Docs` セクションを必ず含め、実装完了時に全チェックが入っていることを Acceptance 条件とする。

```markdown
## N. 機能名

Status: planned (queued)

Context: ...

Target:
1. 実装内容

Docs:
- [ ] `unlaxer-dsl/specs/annotations.md` — 新アノテーション構文
- [ ] `unlaxer-dsl/specs/generators.md` — 生成コードの変化
- [ ] `tinyexpression/docs/...` — 実装ガイド更新

Acceptance:
- 実装が完了し、上記 Docs がすべてチェックされていること
```

---

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
- `tinyexpression-lsp` server dependency is currently pinned to `tinyExpression 1.4.10` + `unlaxer-common 1.2.7` for runtime ABI compatibility.
- mismatch (`tinyExpression 1.4.10` + `unlaxer-common 2.4.0`) reproduces parser runtime linkage failure: `NoSuchMethodError: org.unlaxer.TypedToken.flatten()Ljava/util/List;`.
- follow-up remains: move to newer `tinyExpression` line that is ABI-compatible with `unlaxer-common 2.4.x`, then re-enable latest-common target.

## 7. VS Code Native DAP Integration

Status: planned (queued)

Context:
- current VSIX provides LSP integration and runtimeMode switching, but no `contributes.debuggers` entry.
- DAP behavior is currently available as runtime probe (`TinyExpressionDapRuntimeBridge`) rather than VS Code debug session wiring.

Target:
1. add VS Code debugger contribution (`contributes.debuggers`) and launch schema for TinyExpression.
2. wire extension-side debug adapter launch in `src/extension.ts` (token/ast/dsl-javacode runtimeMode selectable).
3. package debug adapter runtime artifacts in VSIX and verify `stop/next/continue/variables/stackTrace`.

## 19. DAP Variable Input + Dynamic Configuration

Docs:
- [ ] `unlaxer-dsl/specs/lsp-dap.md` — DAP 変数スコープ設計、zero-config launch
- [ ] `tinyexpression/docs/TINYEXPRESSION-P4-LSP-DAP-IMPL-PLAN.md` — Phase 5 実装計画（追記済み）
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 参照ドキュメント（#23 完了後）

Status: planned (queued)

Context:
- Phase 5 of the @catalog/@declares plan adds variable injection to the DAP so users can provide actual values
  and evaluate expressions on-the-fly.
- Discussed 2026-03-20: hardcoding variables in launch.json is limiting; dynamic methods are preferred.

### Variable specification methods (priority order)

1. **Variables pane editing** (`setVariable`) — most native UX
   - `capabilities.supportsSetVariable = true` in DAP initialize response
   - `setVariable()` handler updates the in-session variable map and re-evaluates
   - Variables appear in the Variables view; click to edit inline

2. **File reference** (`variablesFile` in launch.json)
   - `"variablesFile": "${workspaceFolder}/test-vars.json"` — path to a JSON map
   - Loaded at session start; edited externally and re-run to pick up changes
   - Format: `{ "varName": "value", ... }` (simple); optional typed form TBD

3. **Debug Console assignment** (`evaluate()` with assignment form)
   - `$inputName = "alice"` in Debug Console sets a variable for subsequent evaluations
   - Makes small adjustments fast without restarting the session

4. **Zero-config launch** (no launch.json required)
   - `DebugConfigurationProvider.resolveDebugConfiguration()` synthesizes config from the active `.tinyexp` file
   - Auto-detects `tinyexp-vars.json` in the workspace root as default variablesFile
   - Falls back to empty variable map; user can populate via Variables pane

### Variable resolution priority (at session start)

```
launch.json variables: {...}      (highest: explicit inline values)
launch.json variablesFile: path   (second: explicit file reference)
workspace tinyexp-vars.json       (third: auto-detected default file)
empty map                         (last: start bare, edit in pane)
```

### Variables pane scopes (three distinct scopes)

| Scope name | Content | Editable |
|---|---|---|
| Eval Variables | User-provided values for $var substitution | Yes (setVariable) |
| Document Variables | Declared `var $x as string` in the document | No (read from AST) |
| Catalog Variables | Entries from catalogResolver | No (read-only) |

### Session persistence (optional)
- Variable map from the session is saved to workspace state (`context.workspaceState`)
  and auto-loaded as initial values on next launch if no variablesFile is specified.

### Watch expression support
- VS Code Watch panel triggers `evaluate(expr, context="watch")`
- Same evaluation path as Debug Console; re-evaluated on variable change

### Open questions
- `variablesFile` format: simple `{"k":"v"}` only, or typed `{"k":{"value":"v","type":"string"}}`?
- Should Document Variables scope be shown always, or only when document parses successfully?
- Catalog Variables scope: show all entries or only referenced ones?

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
- TE021/TE023 false-positive reduction:
  - unknown-method scan now ignores string/comment regions,
  - unknown-method scan now skips dot-qualified chained calls (example: `get(...).orElse(...)`),
  - operator/notation (`TE023`) scan now ignores string/comment regions for `&&`, `||`, and `$method(...)` probes.
- TE022 suggestion quality update:
  - candidate ranking now prefers local declared variables over global catalog names,
  - partial-key suggestions consider prefix-aware distance (`prefix_<suffix>`).
  - when document includes context hints (e.g., `tags:FA`, `nimt`), TE022 suggestion ranking now prefers catalog entries with matching context label.
  - explicit context keys (`tags/context/product/...`) are prioritized over free-text tokens when deriving TE022 context bias.
  - explicit context hints now accept quoted values as well (`context=\"FA\"`, `tags='NIM'`).
  - explicit context key parsing also accepts single-quoted JSON-like form (`'context': 'FA'`).
  - explicit context hints now also support list-style values (`tags=core,FA,ops`) and extract context tokens from the value segment.
  - explicit context key parsing now accepts JSON-like quoted keys as well (`\"context\": \"FA\"`).
  - when multiple explicit hints exist, TE022 context bias uses the first explicit hint in document order.
  - when a single explicit value includes multiple recognized tokens (`tags=core,NIM,FA`), TE022 uses the first recognized token.
  - if the first explicit hint has no recognized token (`tags=core,ops`), TE022 evaluates subsequent explicit hints before falling back to free text.
  - JSON-like explicit context hints inside comments are ignored and do not affect TE022 ranking.
  - variable-like tokens in explicit context values (`context=$fa`) are ignored as context hints.
  - comment fragments inside explicit values (`tags=core /* FA */`, `tags=NIM // FA`) are stripped before context-token extraction.
  - inline comment tail tokens on explicit context lines (e.g., `tags:NIM // FA`) are ignored as context hints.
  - context hint extraction now ignores string/comment regions to avoid accidental bias from non-code text.
  - TE022 suggestion hint text now uses a fixed order: `context=...` first, then description.
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
- analyzer-phase advanced semantic checks are extended:
  - `TE011`: catches clearly non-boolean `if(...)` conditions (`if(1)`, string/object literal direct conditions),
  - `TE015`: validates `min/max` top-level argument count on parse-success path (non-2-arg and empty-segment forms).
- parser-phase `TE023` mapping is now added for operator/notation mistakes:
  - detects `&&` / `||` misuse (TinyExpression expects single-char `&` / `|`),
  - detects missing RHS after boolean operator (`&)` / `|}` etc.),
  - detects `$method(...)` notation misuse and suggests removing `$`.
  - implementation point: `CalculatorLanguageServer.describeOperatorNotationIssue(...)` + catalog mapping in `resolveErrorCatalogEntry(...)`.
- added TE023 regression tests in LSP server suite:
  - `CalculatorErrorCatalogMappingTest` now verifies TE023 mapping for `&&`, missing RHS after `|`, and `$method(...)`,
  - quick-fix availability for TE023 `&&` diagnostics is covered,
  - false-positive guard cases for comments/strings containing `&&` or `$method(...)` are covered (should remain `TE005` in missing-brace scenarios).
- tuned TE023 classifier priority using parse-hint context:
  - missing-RHS (`&`/`|`) branch now prefers parse expected-hint context and falls back only when parse offset is known to be unreliable,
  - `&&`/`||` and `$method(...)` checks remain unconditional syntax diagnostics.
- improved TE023 quick-fix targeting for broad diagnostics:
  - code-action resolver now scans the diagnostic neighborhood and selects the nearest TE023 issue (`&&`/`||`, `$method(...)`, missing RHS),
  - this keeps quick fixes available even when `Diagnostic.range` starts before the actual operator token.
- expanded TE023 code-action output for mixed notation errors:
  - when multiple TE023 issues coexist in the same diagnostic range, LSP now returns multiple quick-fix actions (up to 3 nearest issues),
  - regression test covers mixed `$method(...)` + `&&` input and expects multi-action output.
- set TE023 quick-fix ordering policy:
  - quick fixes are sorted by edit destructiveness (symbol normalization `&&/||` first, `$method(...)` cleanup next, missing-RHS completion last),
  - regression test asserts the first suggested action for mixed input is `&& を & に修正`.
- deduplicated TE023 quick-fix kinds in mixed diagnostics:
  - code-action resolver now keeps at most one action per TE023 issue kind to avoid repeated identical fixes (`&&` duplicates, etc.),
  - regression test asserts quick-fix titles are unique for mixed `$method(...)` + repeated `&&` input.
- classified TE023 code-action kinds for UI readability:
  - rewrite-type fixes (`&&/||` normalization, `$method(...)` cleanup) are emitted as `quickfix.rewrite`,
  - completion-type fixes (missing RHS -> ` true`) are emitted as `quickfix.insert` and regression-tested.
- advertised TE023 quick-fix sub-kinds in server capabilities:
  - `initialize` response now returns `CodeActionOptions.codeActionKinds = [quickfix, quickfix.rewrite, quickfix.insert]`,
  - capability regression test ensures VS Code can consume the structured kinds reliably.
- lightweight semantic `TE021` mapping is now added in LSP analyzer phase:
  - scans invocation heads and reports unknown method calls not found in:
    - parser-definition-derived method catalog (`TinyExpressionParserMethodCatalog`),
    - `import ... as alias;` aliases,
    - declared method names in current document.
  - emits `[TE021] ...` diagnostics with closest-candidate hint (`候補: ...`) and propagates `Diagnostic.code` via catalog-prefix extraction.
- quick fix integration expanded:
  - `TE021`: rename unknown method to suggested candidate (`候補: ...`) as replacement edit (`quickfix.rewrite`).
  - `TE022`: rename unknown variable to suggested candidate (`候補: $...`) as replacement edit (`quickfix.rewrite`).
  - `TE023`: operator/notation quick fixes for `&& -> &`, `|| -> |`, `$method(...)` -> `method(...)` (remove leading `$`), and missing RHS after `&`/`|` (insert `true`).
  - `TE024`: partialKey suffix quick fix (`$prefix` -> `$prefix_<suffix>`) is classified as `quickfix.insert`.
  - `TE003`: convert string double quotes to single quotes.
  - `TE007`: close missing quote in `description='...'`.
  - `TE008`: normalize full-width punctuation to half-width symbols.
  - `TE016`: add missing `as alias` in `import ... as ...;`.
  - `TE017`: add missing `$` in variable declaration head.
  - `TE018`: reorder misplaced type hint from `as type $name` to `$name as type`.
  - `TE019`: repair `get(...).orElse(...)` shape (missing `.orElse`, missing `orElse(...)`, or missing `)` in `orElse`).
- unresolved:
  - full-precision semantic validation still has gaps (`TE011`/`TE015` now have first-stage advanced checks, but deep type-aware validation and broader `TE021`/`TE022`/`TE023` context validation remain).
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
- added analyzer regression coverage for TE021 unknown methods:
  - configuration-free analyzer run now produces `[TE021]` diagnostics with candidate hints (e.g., `cosh` → `cos`),
  - `TinyExpressionVariableCatalogTest` guards TE021/TE022 counts so future heuristic tweaks are covered.
- reduced diagnostic noise for structural mismatch fallback:
  - TE010 messages no longer append parser detail tail by default,
  - missing-semicolon (`TE006`) detection for `var` declarations now includes a line-based fallback path when parse start offset rewinds to the file head,
  - global structural fallback now checks missing block/match braces (`expected '{'` / `expected '}'`) before generic `unexpected characters`.
- added converter script:
  - `tools/calculator-lsp-vscode/scripts/convert-legacy-catalog-to-canonical.sh`,
  - `npm run catalog:convert -- <legacy-files...>`.
- VSCode client now resolves catalog path tokens before launch:
  - `${workspaceFolder}` and `~` expansion in `tinyExpressionLsp.catalog.path`.

## 15. TinyExpression P4 UBNF Completion (CodeBlock / Full Consume)

Status: planned (queued)

Context:
- `tinyexpression-p4.ubnf` で parse は進むが、debug trace ではサンプル式が全文消費に到達しないケースがある（`if(external ...)` 付近で rollback）。
- `CodeBlock` は現在 `token CODE_BLOCK = org.unlaxer.tinyexpression.parser.javalang.CodeParser` 依存で、UBNF側の終端保証（全文消費）と責務境界が曖昧。
- debugger 側の trace test で `expectedAllConsumed=true` を入れたため、未完成箇所が継続的に可視化される状態。

Target:
1. `Formula` の終端契約を明示する（`Expression` 必須化 + EOF 明示を含む最終仕様決定）。
2. `CodeBlock` を可能な範囲で UBNF へ寄せる:
   - `CodeStart` / `CodeBody` / `CodeEnd` の文法責務分離、
   - ただし本文の「終端まで読み飛ばし」は必要なら専用 parser 併用。
3. parser trace と debugger cargo movement の整合テストを追加し、同一入力で一致を検証する。
4. `tinyExpression-p4.ubnf` 更新に合わせて spec / docs / sample grammar を同期更新する。

Validation:
1. `unlaxer-debugger` の `DebugSessionTraceTest` で `tinyexpression-p4-user-sample` が全文消費で green。
2. `tinyexpression` 側 parser/evaluator/LSP の既存回帰が壊れない。

## 16. P4 Feature Parity: Variable Catalog via UBNF

Status: planned (queued)

Docs:
- [ ] `unlaxer-dsl/specs/annotations.md` — `@catalog` アノテーション構文・意味論
- [ ] `unlaxer-dsl/specs/generators.md` — LSPGenerator のカタログインフラ生成
- [ ] `tinyexpression/docs/TINYEXPRESSION-P4-LSP-DAP-IMPL-PLAN.md` — 実装計画更新
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 参照ドキュメント（#23 完了後）

Context:
- `calculator-lsp-vscode` 0.2.31 では外部ファイルから変数カタログを読み込み、入力補完・ホバー・TE022診断に利用していた。
- P4 拡張（`tinyexpression-p4-lsp-vscode`）にはカタログ機能がなく、ScopeStore（コード上の宣言）のみ。
- 既存カタログファイルは4種類のフォーマットが混在している（フォーマット詳細は `TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md` 参照）。
- 今後はファイルだけでなく in-memory resolver 等もサポートしたい（Resolver概念の導入）。

Target:
1. **カタログフォーマット正規化**:
   - 4つのカタログファイル（`fa-*`, `nimt-*`）を共通フォーマットに統一。
   - 正規化フォーマット候補: `name|type|api|description`（nimt-cfvar形式を基準に拡張）。
   - 変換スクリプトまたは自動マイグレーションを用意。
2. **UBNF 上でのカタログ定義表現**:
   - grammar ファイル内または外部カタログファイルで「変数候補」を宣言できる構文を設計。
   - UBNF アノテーション（例: `@catalog` や `@variableSource`）で変数候補ソースを指定する方式を検討。
3. **Resolver 概念の導入**:
   - カタログのソースを抽象化する `VariableCatalogResolver` インターフェースを設計。
   - 実装: `FileBasedResolver`（ファイル読み込み）、`InMemoryResolver`（runtime提供）、`CompositeResolver`（複数統合）。
   - LSP 初期化オプションまたは UBNF grammarで resolver クラスを指定できるようにする。
4. **P4 LSP への統合**:
   - `TinyExpressionP4LanguageServerExt` の completion / hover / diagnostics にカタログを反映。
   - TE022（未定義変数）診断とクイックフィックスの復活。
   - TE024（partialKey サフィックス欠如）診断の復活。

Reference:
- 差分分析: `docs/TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md`
- 旧実装: `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/TinyExpressionVariableCatalog.java`
- 旧カタログファイル: `tools/calculator-lsp-vscode/config/`

## 17. P4 Feature Parity: Quick Fixes & TE Diagnostics

Status: planned (queued)

Docs:
- [ ] `unlaxer-dsl/specs/annotations.md` — `@quickFix`, `@deprecated`, `@quickFixHook`, `@errorCode` 構文（#22 と連動）
- [ ] `unlaxer-dsl/specs/generators.md` — quick fix スタブ生成の仕組み
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 参照ドキュメント（#23 完了後）

Context:
- 0.2.31 では 18 種以上の TE コードに対してクイックフィックスが提供されていた（セミコロン挿入、括弧補完、全角→半角、演算子修正等）。
- P4 現行ではクイックフィックスは TE001 の 1 種のみ。
- TEエラー診断そのものも出ないケースが多い（TE006 セミコロン、TE022 未定義変数等）。

Target:
1. TEエラー診断のUBNF表現設計（errorCode アノテーション候補: `@errorCode("TE006")`）。
2. 高頻度クイックフィックスの復活（優先順位順）:
   - TE006: `;` 挿入
   - TE004/TE005: `)` / `}` 挿入
   - TE008: 全角→半角変換
   - TE023: 演算子正規化（`&&`→`&` 等）
   - TE022: 変数名typo修正
3. errorCode の多言語対応（i18n）との連携設計（バックログ #4 とも関連）。

Reference:
- 差分分析: `docs/TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md`
- 旧実装: `tools/calculator-lsp-vscode/server/src/main/java/org/unlaxer/calculator/CalculatorLanguageServer.java`

## 18. P4 Feature Parity: Import Completion

Status: planned (queued)

Docs:
- [ ] `unlaxer-dsl/specs/lsp-dap.md` — import 補完トリガー設定
- [ ] `unlaxer-dsl/specs/generators.md` — LSPGenerator の補完ヒント機構
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 参照ドキュメント（#23 完了後）

Context:
- 0.2.31 では import 文を `import sample.v1.CheckAlphabets#check as checkAlphabets;` までフル補完できた。
- P4 現行では `import` キーワードまでしか補完されない。

Target:
1. import 文の文法構造（`import QualifiedName#method as alias;`）を補完候補生成に組み込む。
2. UBNF の import ルールに `@completion` 系アノテーション（または LSPGenerator の補完ヒント機構）を追加。
3. import補完のトリガー文字（`.`, `#`, `as` 等）を設定する。

Reference:
- 差分分析: `docs/TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md`

## 20. codegen.toml — Generator Configuration File

Status: planned (queued)

Context:
- 現状 unlaxer-dsl はジェネレーター設定を UBNF アノテーション（`@package`, `@whitespace` 等）に詰め込んでいる。
- 「ツールがどう振る舞うか」という設定（エラー戦略、DAP スコープ名、補完トリガー等）は文法の記述とは別の関心事。
- 設定の置き場所が明確でないため、実装者が独自判断で散在させてしまう。

Decisions (2026-03-20):
- フォーマット: **TOML**（シンプル、コメント可、最大3階層程度で収まる）
- 命名: 文法ファイルのサイドカー（`foo.ubnf` → `foo.codegen.toml`）
- 指定方法: サイドカー自動検出 + `--config` フラグで明示指定も可能
- 設定の分類:
  - **codegen.toml に書くもの**: ツール挙動のポリシー（エラー戦略、スコープ名、同期戦略等）
  - **UBNF アノテーションに書くもの**: 文法意味論に関わるもの（`@catalog`, `@declares`, `@quickFix` 等）

Target:
1. `GrammarDecl` に対応する `CodegenConfig` データモデルを設計・実装。
2. TOML パーサー統合（依存ライブラリ選定）。
3. `CodegenMain` に `--config` フラグ追加、サイドカー自動検出ロジック実装。
4. 各ジェネレーター（LSPGenerator, DAPGenerator 等）が `CodegenConfig` を参照するよう拡張。
5. 設定キー一覧のリファレンスドキュメント作成。

Config 構造例:
```toml
[lsp]
errorStrategy = "te-catalog"       # te-catalog / simple / mixed
completionTriggers = ["$", "."]

[lsp.embedded.java]
fenceSyntax = "```java:"
virtualDocExtension = ".java"
routeTo = "java"
syncStrategy = "debounce"          # realtime / debounce / onSave
debounceMs = 300

[dap]
errorStrategy = "mixed"
sessionPersistence = true
zeroConfigFile = "tinyexp-vars.json"

[dap.scopes]
eval = "Eval Variables"
document = "Document Variables"
catalog = "Catalog Variables"

[catalog]
format = "both"                    # simple / typed / both
autoDetectFile = "tinyexp-vars.json"

# Level B: ルール単位オーバーライド
[lsp.rules.VariableDeclaration]
errorStrategy = "simple"
```

Docs:
- [ ] `unlaxer-dsl/specs/generators.md` — CodegenConfig の仕組みと各ジェネレーターへの影響
- [ ] `unlaxer-dsl/specs/cli.md` — `--config` フラグの追加
- [ ] `unlaxer-dsl/docs/codegen-config-reference.md` — 設定キー全リファレンス（新規作成）
- [ ] `unlaxer-dsl/SPEC.md` — codegen.toml の概念を追記
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 参照ドキュメント（#23 完了後）

Acceptance:
- `foo.ubnf` のサイドカー `foo.codegen.toml` が自動検出される
- `--config` 指定が機能する
- 各ジェネレーターが設定値を参照して出力を変える
- 上記 Docs がすべてチェックされていること

## 21. Embedded Language Support (@embeddedLanguage + codegen.toml)

Status: planned (queued)

Context:
- TinyExpression の Java コードブロック（` ```java:ClassName ` フェンス構文）のように、
  ある言語のファイル内に別の言語が埋め込まれるケースがある。
- 現状 LSP/DAP はこれを認識せず、Java ブロック内で Java LSP の補完・診断が効かない。
- LSP 仕様に埋め込み言語の正式定義はなく、VS Code HTML 拡張が採用する Virtual Document パターンが事実上の標準。

Decisions (2026-03-20):
- アーキテクチャ: **Virtual Document** — 埋め込みブロックを切り出し、対象言語の LSP に完全委譲
- 同期戦略: デフォルト `debounce`（300ms）、`codegen.toml` で `realtime` / `onSave` に変更可能
- フェンス検出・routeTo 設定は `codegen.toml` の `[lsp.embedded.*]` セクションで定義
- Java ブロック内変数（`$inputName`）は `CalculationContext` 経由でアクセスするため、Java LSP 視点では純粋な Java → 完全委譲で問題なし

Target:
1. `@embeddedLanguage(id="java")` アノテーションを UBNFAST / UBNFParsers / UBNFMapper に追加。
2. LSPGenerator が `[lsp.embedded.*]` 設定を読んで Virtual Document 管理コードを `extension.ts` に生成。
3. Virtual Document の作成・更新・破棄ライフサイクル実装。
4. 同期戦略（realtime / debounce / onSave）の切り替え実装。
5. DAPGenerator が埋め込みブロックのブレークポイントを対象言語 DAP に転送する基盤を検討。

Docs:
- [ ] `unlaxer-dsl/specs/annotations.md` — `@embeddedLanguage` 構文・意味論
- [ ] `unlaxer-dsl/specs/lsp-dap.md` — Virtual Document パターン、同期戦略
- [ ] `unlaxer-dsl/docs/lsp-extensions.md` — 埋め込み言語サポートの実装ガイド
- [ ] `unlaxer-dsl/docs/codegen-config-reference.md` — `[lsp.embedded.*]` キー説明（#20 と連動）
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 参照ドキュメント（#23 完了後）

Acceptance:
- `.tinyexp` ファイルの Java フェンスブロック内で Java LSP の補完・診断が動作する
- 同期戦略が `codegen.toml` で切り替えられる
- 上記 Docs がすべてチェックされていること

## 22. @quickFix / @deprecated / @quickFixHook アノテーション

Status: planned (queued)

Context:
- 現状 Quick Fix はすべて手書き実装（`CalculatorLanguageServer.java` 内）。
- P4 では Ext クラスに移植しているが、UBNF から生成する仕組みがない。
- シンプルなトークン挿入系 Quick Fix は UBNF アノテーションで宣言的に表現できるはず。
- 複雑な変換（テキスト移動・再構築）はフックを生成して Ext クラスで実装する方式が適切。

Decisions (2026-03-20):
- 3種類のアノテーションを導入:
  - `@quickFix(insert=";")` — トークン挿入など単純変換 → 完全自動生成
  - `@deprecated(message="...", quickFix="...")` — deprecated パターンの警告と修正提案
  - `@quickFixHook("name")` — 複雑変換のスタブを生成、Ext クラスで実装
- `@errorCode("TE006")` と組み合わせて TE コード体系と連携

Target:
1. `@quickFix`, `@deprecated`, `@quickFixHook`, `@errorCode` を UBNFAST に追加。
2. UBNFParsers / UBNFMapper に対応するパーサー・マッパーを追加。
3. LSPGenerator が各アノテーションに応じた Quick Fix コードを生成。
   - `@quickFix(insert=";")` → CodeAction 生成コードを完全自動出力
   - `@quickFixHook("name")` → `protected List<TextEdit> quickFix_name(...)` スタブを生成
4. GrammarValidator にバリデーション追加（`@quickFix` と `@errorCode` の整合確認等）。

例:
```ubnf
@errorCode("TE006")
@quickFix(insert=";")
Statement ::= Expression ';' ;

@deprecated(message="import 宣言側に returning TYPE を移してください")
@quickFixHook("moveReturningToImport")
ExternalCallLegacy ::= 'external' 'returning' 'as' TYPE IDENTIFIER ;
```

Docs:
- [ ] `unlaxer-dsl/specs/annotations.md` — 3アノテーションの構文・意味論・組み合わせルール
- [ ] `unlaxer-dsl/specs/generators.md` — LSPGenerator の Quick Fix 生成ロジック
- [ ] `unlaxer-dsl/specs/validation.md` — バリデーションルール追加
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 参照ドキュメント（#23 完了後）

Acceptance:
- `@quickFix(insert=";")` から CodeAction が自動生成される
- `@quickFixHook("name")` からスタブメソッドが生成される
- GrammarValidator が不整合を検出する
- 上記 Docs がすべてチェックされていること

## 23. TinyExpression × unlaxer-dsl 実装リファレンスドキュメント

Status: planned (queued)

Context:
- 現状 unlaxer-dsl には四則演算レベルの quick start ドキュメント（SPEC.md / README）がある。これは良いものなので残す。
- しかし実際に複雑な言語（TinyExpression レベル）を unlaxer-dsl で実装する際の詳細な設計判断・落とし穴・パターンを解説したドキュメントが存在しない。
- TinyExpression の実装は unlaxer-dsl の「実戦リファレンス」として最適な題材であり、本1冊レベルのコンテンツになりうる。

Target:
以下のトピックを網羅する `TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` を作成:

1. **文法設計編**
   - quick start との違い: 実規模言語でのルール分割戦略
   - `@whitespace` / `@interleave` の使い分け
   - `@leftAssoc` / `@rightAssoc` / `@precedence` の実装例
   - `@backref` による前方参照・後方参照
   - `@scopeTree` によるスコープ管理

2. **コード生成編**
   - codegen.toml の設計と各ジェネレーターへの影響
   - 生成コードを壊さずに拡張する Ext パターン
   - golden snapshot の管理と `refresh-golden-snapshots.sh`

3. **LSP 実装編**
   - `@catalog` による変数カタログ統合
   - `@declares` + `description` によるホバー生成
   - `@quickFix` / `@quickFixHook` による Quick Fix 生成
   - `@embeddedLanguage` による Virtual Document 統合
   - errorStrategy の選択（te-catalog / simple / mixed）

4. **DAP 実装編**
   - zero-config launch の仕組み
   - Variables ペイン 3スコープ設計（Eval / Document / Catalog）
   - `variablesFile` のスキーマ（simple / typed 両対応）
   - セッション永続化と Watch 式サポート

5. **文法拡張編**
   - `import X#method returning TYPE as alias` パターン
   - `@typeof` によるランタイム型チェック
   - `external` キーワードの意図的な冗長性（「意識させる」設計哲学）

6. **テスト・検証編**
   - parity テストの書き方
   - golden snapshot の運用
   - LSP / DAP 統合テストパターン

Docs:
- [ ] `unlaxer-dsl/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 本ドキュメント自体（新規作成）
- [ ] `unlaxer-dsl/SPEC.md` — 参照ドキュメントへのリンク追記
- [ ] `unlaxer-dsl/README.md` / `README.ja.md` — リファレンスドキュメントへの導線追加

Acceptance:
- quick start（SPEC.md）と本リファレンスが相互に参照している
- 各セクションが対応するバックログ項目の実装完了後に更新されている
- 上記 Docs がすべてチェックされていること

Note: 本ドキュメントは各機能実装と並行して章ごとに追記していく運用を推奨。
一括作成を待たずに、機能が完成したら対応章を書く。

## 24. Import 宣言への型アノテーション移動 (`returning TYPE`)

Status: planned (queued)

Context:
- 現状の TinyExpression では外部メソッド呼び出し時に call site で型を明示:
  `if(external returning as boolean checkAlphabets($inputName))`
- 内部メソッドは定義側で型を持つため call site がクリーン:
  `if(innerbooleanMethod($parameter))`
- この非対称性は設計上の負債であり、import 宣言側に型情報を移すことで解消できる。
- 言語哲学「意識させる」に基づき `external` キーワードは optional で残す。

Decisions (2026-03-20):
- 新構文: `import sample.v1.CheckAlphabets#check returning boolean as checkAlphabets;`
- 旧構文: 互換維持（deprecated 扱い）
- call site: `if(checkAlphabets($inputName))` または `if(external checkAlphabets($inputName))`
- 移行支援: LSP が旧構文を検出し「import 宣言側に returning を移してください」Quick Fix を提案

Target:
1. `tinyexpression-p4.ubnf` の import ルールを拡張（`returning TYPE` をオプション追加）。
2. `ExternalCallLegacy` ルールに `@deprecated` + `@quickFixHook("moveReturningToImport")` を追加（#22 と連動）。
3. TinyExpressionP4LanguageServerExt に `moveReturningToImport` フック実装。
4. 型情報を import 宣言から取得して補完・型チェックに利用。

Docs:
- [ ] `tinyexpression/specs/language.md` — import 構文の新旧両形式を記述
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — 文法拡張編に追記（#23 と連動）
- [ ] `tinyexpression/tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` — 文法更新

Acceptance:
- 新構文でパースが通り、call site に `external` が不要になる
- 旧構文が deprecated 警告 + Quick Fix を出す
- 上記 Docs がすべてチェックされていること

## 25. Java コードブロック DAP デバッグ

Status: planned (queued)

Context:
- TinyExpression の ` ```java:ClassName ` フェンス構文で埋め込まれた Java コードは、
  実行時に `CalculationContext` 経由で TinyExpression の変数値を受け取る。
- この Java コードをデバッグするには、JVM の起動・classpath・ソースパスの設定が必要。
- `launch.json` はワークスペース側（`.vscode/launch.json`）に置かれ、VSIX 外。
  プロジェクトごとに複数設定を持てる。

### 実装レベル定義

| レベル | 内容 |
|---|---|
| L1 | Java ブロックをコンパイル＋実行し、結果を Variables ペインに表示 |
| L2 | 例外発生時にスタックトレースを Debug Console に表示 |
| L3 | Java コード内ブレークポイント＋ステップ実行（JDWP + vscode-java-debug 連携） |

**当面の Target: L1 + L2。L3 は将来フェーズ。**

### launch.json スキーマ拡張（案）

```jsonc
{
  "type": "tinyexpressionp4",
  "request": "launch",
  "name": "FA環境",
  "javaRuntime": "${env:JAVA_HOME}/bin/java",   // 省略時は PATH から解決
  "classpath": [
    "${workspaceFolder}/lib/*.jar"              // 追加 JAR（tinyexpression JAR は自動バンドル）
  ],
  "sourcePaths": ["${workspaceFolder}/src"],    // L3 で使用
  "variables": { "inputName": "alice" }
}
```

- `tinyexpression-p4-lsp-server.jar`（CalculationContext を含む）は VSIX にバンドル済みのため
  classpath に自動追加される。ユーザーが意識するのは追加 JAR のみ。
- `javaRuntime` 省略時は `JAVA_HOME` 環境変数 → `PATH` の順で自動解決。

### L1/L2 実装方針

```
フェンス内 Java コードを抽出
  → javac でコンパイル（tinyexpression JAR を classpath に含める）
  → java で実行（TinyExpression 変数値を CalculationContext に注入）
  → 結果を Variables ペインの "Eval Variables" に表示（L1）
  → 例外時はスタックトレースを Debug Console に表示（L2）
```

### L3 実装方針（将来）

- `vscode-java-debug` 拡張（Language Support for Java）との連携
- JDWP（Java Debug Wire Protocol）で JVM にアタッチ
- フェンス内 Java コードのソースマッピングを提供
- ブレークポイント・ステップ実行・変数ウォッチを Java Debug 拡張に委譲

Target (L1/L2):
1. `launch.json` スキーマに `javaRuntime` / `classpath` / `sourcePaths` を追加。
2. `javaRuntime` の自動解決ロジック実装（JAVA_HOME → PATH）。
3. フェンス内 Java コードの抽出・コンパイル・実行パイプライン実装。
4. `CalculationContext` への変数注入（DAP の Eval Variables と連動）。
5. 実行結果を Variables ペインに表示（L1）。
6. 例外キャッチ＋スタックトレースを Debug Console に出力（L2）。

Target (L3, 将来):
7. `vscode-java-debug` との連携調査・PoC。
8. JDWP アタッチ＋ソースマッピング実装。
9. フェンスブロックのブレークポイントを Java DAP に転送。

Docs:
- [ ] `tinyexpression/docs/TINYEXPRESSION-P4-LSP-DAP-IMPL-PLAN.md` — Java ブロック DAP セクション追加
- [ ] `tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-REFERENCE.md` — DAP 実装編に追記（#23 完了後）
- [ ] `unlaxer-dsl/specs/lsp-dap.md` — 埋め込み言語 DAP の設計方針

Acceptance (L1/L2):
- Java フェンスブロックがコンパイル＋実行され、結果が Variables ペインに表示される
- 例外時にスタックトレースが Debug Console に出る
- `javaRuntime` 省略時に自動解決される
- 上記 Docs がすべてチェックされていること
