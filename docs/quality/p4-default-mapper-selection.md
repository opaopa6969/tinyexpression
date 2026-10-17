# Default mapper selection (Tiny #259)

Classic's default facade request has a nonempty list of implicit AST preferences, such as
IfExpr, SinExpr, BinaryExpr and FormulaExpr. It is not a request to enumerate every mapped
candidate. Previously the facade called `mapParsedTree`/`mapSubtreeTree` even for this request;
the current source generator's intentional 4096-candidate inventory limit rejected established x64 benchmark inputs.

When `allowDefault` is true, the adapter now calls the existing public single-selection
`selectParsedTokenWithSourceMap`/`selectSubtreeTokenWithSourceMap` once, retaining its owned AST
and source snapshot. If both the snapshot selector and legacy `sourceSpanOf` API are absent,
whole-source AST indexing is unavailable: the adapter retains the old per-preference selection
path instead of inventing source positions. Other older generators use the existing capability
fallback only when an API is absent. Invocation failures still propagate; mapping failure does
not trigger another mapping API or parse.

The P4 AST retains whole-input wrappers and their captured children. The adapter indexes
whole-source AST nodes in ancestor order, stopping at partial-source AST branches. In this
P4 schema the relevant whole-input wrappers form an ancestor chain; the outermost matching
record is the same one preferred by the generated selector's shallowest-CST rule. It then
uses the facade's existing implicit preference order, whole-source check and default fallback.
A selected child retains the default selection's committed token as a whole-source anchor,
its own identity-based AST span, and the same owned snapshot. This adapter is not a replacement
for the generated mapper's general preferred-candidate API.

`mapDefaultOnce` itself accepts only null/blank preferences. `mapImplicitOnce` resolves only
implicit whole-source preferences already retained in that default AST. Explicit result or
AST preferences keep the existing `mapOnce` path. With the current source generator, full inventories remain bounded at 4096,
and all mapping node/edge/depth/work/text limits remain unchanged. Older generators retain
their historical inventory and validation behavior. No retry, grammar change,
unsafe memo policy, or resource-limit increase is part of this change.

This change depends on Tiny #256's root/family projection. Its canonical projection runs
after the single mapping and before facade preference selection, retaining its historical
bare-boolean/string result shape. Java UBNFC and Rust's production parser are unchanged by
this adapter; Rust still returns owned Formula/Expression wrappers. The shared fixture records
these public root differences explicitly rather than calling them identical APIs.

## Verification

`P4ImplicitMappingTest` compares old full-inventory and new implicit selection on the same
committed CST for the existing corpus: 324 accepted inputs, every eligible whole-source
implicit type, final AST, CP span, source text and selection mode. Twelve inputs fail parsing
before mapping. Four rows (two unique x64 inputs present in both the formula corpus and file
fixtures) exceed the unchanged full-inventory cap and have separate positive expectations.
The Java Classic/UBNFC 350-case facade comparison also compares all AST fields and node CP spans.

`default-mapping-inputs.tsv` pins the two established positive benchmark files, SHA-256, CP
lengths and expected public root families. Java verifies hashes, source spans, facade modes,
retained snapshots across subsequent small partial-input rejection and the unchanged explicit inventory rejection.
The Rust test consumes the same files and expected CP/root families through `parse`, checks
the same small partial-input rejection, and retains the complete owned tree across later parses.

Use Java 21 and Rust 1.85 with an isolated Maven repository. The complete x64 regression requires
an unlaxer build containing parser #680's bounded tree-reachability index. The local candidate
was installed as 3.3.0 from source `de53a229`; this is not a new Central publication. Tiny's CI
source pin `fc75fe1e` / 3.3.0-SNAPSHOT already exposes the single-selection API, but its generated
mapper predates both the public-CST MappingGuard and the preferred-inventory 4096 limit. Its
existing 350-case facade comparison passes (328 accepted, 22 both rejected); that proves API
compatibility, not the newer bounded guard contract. The candidate's large-input success and
unchanged inventory rejection are separate results. Run the new bounded large-input assertions
against a fixed upstream source containing #680 in a separate repository/version override;
do not silently relabel the old pin or skip these assertions as successful.

```sh
RUSTUP_TOOLCHAIN=1.85.0 mvn -Dmaven.repo.local=/path/to/isolated-m2 -Dunlaxer.version=3.3.0 \
  -Dtinyexpression.skipRailroad=true \
  -Dtest=P4SourceMappingTest,P4ImplicitMappingTest,P4PreferredAstMapperDiagnosticsTest,UbnfcParityTest,P4ParserEngineTest test
RUSTUP_TOOLCHAIN=1.85.0 cargo test --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs --test default_mapping_inputs
```

These are semantic/resource-boundary checks on a shared development host. They do not attest
to an exclusive measurement environment or close parser #197/#184's final latency conditions.
Classic SAFE and Java/Rust UBNFC production timings remain separate evidence.
