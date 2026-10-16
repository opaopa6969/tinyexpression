# Fraud production acceptance and timing (Tiny #257)

The previous `P4PackratFraudFormulaTest` caught every `RuntimeException` and accepted elapsed
values below **two** seconds while promising one second. A fast failure therefore passed. It
now requires successful parse+map, a non-null `IfExpr`, complete source CP range, valid nested
node ranges and unchanged AST across first-call/warm-up/warm calls. Every sample must take
strictly less than **1,000,000,000 ns**. Exceptions and failed assertions propagate; the 90-second
JUnit timeout is a hang guard, not an alternative performance threshold.

Both languages read the unchanged five inputs from
`rust/tinyexpression-rs/tests/fixtures/fraud-alert.tsv`; the adjacent `fraud-acceptance.json`
pins input SHA-256, CP length and manually expected conditional counts. The comparison checks
numeric then/else branch ASTs independently (fraud 1–4: 1/0; fraud 5: then 1,1,5,5,5), followed
by complete cross-language AST/CP equality. Null, partial input, invalid span, altered branch,
missing/duplicate observations, wrong entry, cross-language differences and the exact one-second
boundary are negative gates.

## Production entries and representation

Java calls `P4PreferredAstMapper.parseDetailed(source, ExpressionTypes._float)` with a scoped
`P4ParserEngine.UBNFC` override. Rust calls public `tinyexpression_rs::parse(source)` once. Tests
add no alternate-entry parse/retry. The facades' existing internal root-selection behavior
remains production behavior, including the retries tracked separately in Tiny #256.

Java selects the numeric `IfExpr`; Rust can return the enclosing `FormulaExpr`. The Rust test
retains its **raw AST** and selects the conditional for observation only. It accepts only
whole-input Formula wrappers without imports/declarations/methods/code blocks, Expression
wrappers, and Binary wrappers with an AST operand and empty operators/right operands. Anything
else fails. This projection is outside timing and changes no production parse.

The other existing public shape difference is explicit in Rust `generated/compat.rs`: ubnfc's
synthetic operand `BinaryExpr(left=null, op=[text], right=[])` is folded to `AstValue::Text`.
Java retains that node. The Python comparator folds only this exact shape (including the
existing single-quote lexical normalization). Raw Java AST and raw/selected Rust AST remain
in the reports. All actual node CP spans are independently bounds-checked; the comparator
compares the spans of common nodes and does **not** claim identical publication of text-value
spans absent from Rust canonical JSON. No arbitrary source reparse or text fallback is used.

Successful public facade calls expose no failure diagnostic. Reports explicitly use
`diagnostic:null`; this does not claim absence/equality of private speculative failures. Java
production currently asks ubnfc for diagnostics and occurrences, while Rust defaults both off;
reports retain that difference. Invalid/trailing input must produce an explicit public failure.
Memo is ubnfc's production default packrat in both cases. The Classic-only
`tinyexpression.p4.memoize` property is neither set nor cited as enabling this production gate.

## Reproduce semantic and loaded-host regression checks

Use Java 21 and Rust 1.85. Build against the intended parser artifact in an isolated Maven
repository, explicitly overriding Tiny's default dependency; do not install over a published
artifact in the ordinary user Maven repository.

```sh
RUSTUP_TOOLCHAIN=1.85.0 mvn -B -Dmaven.repo.local=/path/to/isolated-m2 \
  -Dunlaxer.version=3.3.0 -Dtinyexpression.skipRailroad=true \
  -Dtest=P4PackratFraudFormulaTest,FraudAcceptanceTest test
RUSTUP_TOOLCHAIN=1.85.0 cargo test --manifest-path rust/Cargo.toml --locked \
  -p tinyexpression-rs --test fraud_acceptance -- --nocapture
RUSTUP_TOOLCHAIN=1.85.0 python3 -m unittest discover -s scripts/tests -p test_fraud_production.py -v
RUSTUP_TOOLCHAIN=1.85.0 python3 scripts/check-fraud-production.py \
  --parser-m2 /path/to/isolated-m2 --parser-commit FULL_40_HEX_PARSER_SHA \
  --output /path/to/evidence
```

The two tests truncate stale reports before starting and persist successful samples before
checking the time limit, so an over-budget observation is retained. The final comparator
requires all five inputs × three phases × two languages; incomplete/error runs cannot pass.
The output contains raw reports, source commit/dirty state, input identities, grammar/pin
hashes, exact parser JAR hashes, toolchain versions, native platform and verdict. Archive the
Maven/Cargo logs and dependency resolution alongside these files. Use a new output directory
for each run and preserve failed-run evidence. A dirty-tree record is development evidence;
repeat on the final clean source revision before citing its acceptance.

`cold` means the **first call for this input in this test process**. Other tests in the same JVM or Rust test binary can already have parsed inputs, and later
inputs can use warmed runtime/compiler state. `pid` and `first_observation_in_process` identify
observations, without claiming that the parser or process was otherwise cold. `warmup` is a separate second
call; `warm` is the third call. Do not aggregate these phases or relabel all first-input calls
as fresh-process latency. Parsing and AST conversion are timed; validation, canonical JSON,
input I/O, report I/O, JVM/process startup and build time are excluded. Public Rust `parse`
includes its owned AST conversion; Java includes its facade selection/conversion.

## Final performance acceptance protocol (not attested by these checks)

This correction establishes actual acceptance and a one-second regression guard. Its report
always says `measurement_eligible:false`. It does not close parser #197/#184, prove Classic SAFE
performance, or replace those issues' root-selection/retry requirements.

For final dedicated-host measurement:

1. Pin the clean Tiny/parser commits, grammar and toolchains; build before timing, hash binaries
   and dependency JARs, verify semantic checks, save exact commands/options and native resource
   limits. Preserve the parser #652/#653 finite-budget fixes for any separate Classic study.
2. Reserve an idle host: no CI/builds/JFR/sampling profiler or competing benchmark. Record CPU,
   memory, OS, governor/container/cgroup limits, runtime heap/stack flags, load before/after and
   an explicit truthful exclusivity attestation. Shared-host runs stay exploratory evidence.
3. Run at least five fresh processes per input/language. To select one case in a fresh JVM use
   `-Dtinyexpression.fraud.id=fraud#1 -Dtinyexpression.fraud.output=/absolute/java-1.jsonl`
   with only `-Dtest=P4PackratFraudFormulaTest`; invoke each case in its own Maven/Surefire process.
   For Rust use `FRAUD_ID=fraud#1 FRAUD_OUTPUT=/absolute/rust-1.jsonl` and run
   `cargo test --manifest-path rust/Cargo.toml --locked -p tinyexpression-rs --test fraud_acceptance
   fraud_formulas_are_accepted_and_mapped_within_one_second -- --exact --nocapture` with
   `RUSTUP_TOOLCHAIN=1.85.0` (omit other tests so they cannot pre-warm the process). Repeat for ids 1–5. Run language processes sequentially, not concurrently. Retain distinct
   PIDs, all first-call/warm-up/warm samples and failed samples; never choose only a fast run.
4. Compare complete AST/source-position/public-diagnostic observations before evaluating
   latency. Require every measured parse+map call < 1 second; do not average away a miss or
   raise it to two seconds. Report first-call and warm distributions independently, including
   maxima and process-startup exclusion. Keep a hard process hang deadline and count timeout,
   exception, partial/null result or unexpected diagnostic as failure.
5. Archive commands, environment, input/source/build hashes, raw times and semantic verdicts.
   Separate ubnfc production, Classic SAFE and legacy evidence by backend/API. The ordinary
   semantic comparator cannot promote records into a dedicated-host attestation.

The protocol is preparation only. No dedicated/exclusive-host final measurement was performed
by this change. Concurrent parser CI/build activity existed during development verification.

## Mandatory ordinary CI gate and dependency identity

The existing `ubnfc-parser` job runs Java acceptance alongside its parity suite, Rust's production
fraud integration and the collector/provenance negative tests, then explicitly requires all 30
observations. These steps have no `continue-on-error`; an exception, missing observation,
comparison difference or negative-gate failure makes the job fail. An `always()` collector step
writes a failed summary even when an earlier test failed, and an `always()` artifact upload
preserves `fraud-production-acceptance` raw reports, input/source/JAR hashes, provenance,
metadata and verdict. The existing Java Surefire artifact remains separate. CI evidence remains
shared-host verification with `measurement_eligible:false`.

This job's dependency route is **Tiny's declared Maven version**, currently `3.3.0-SNAPSHOT`,
not the locally tested candidate `3.3.0`. Its existing installer builds the fixed
`.github/unlaxer-source-pin` (`fc75fe1e25e538d47908f05ea2039f585aef696e`) into an empty job-local
Maven repository when that version is unavailable on Central. The installer's optional
`UNLAXER_PROVENANCE_FILE` records the actual checked-out commit after successful installation.
If the declared version becomes available on Central, it instead records `maven-central` with
no inferred source commit. Failed installation clears stale success provenance. The collector
uses the recorded version to hash the actual dependency JARs; it never substitutes the local
candidate SHA or assumes the source pin produced a published artifact.

Local runs can keep `--parser-commit FULL_SHA --parser-version 3.3.0` (the latter defaults to
3.3.0); those records are explicitly labeled `reported-local-source`. CI instead passes
`--parser-provenance target/fraud-acceptance/parser-dependency.json`. The two representations
are mutually exclusive, so evidence cannot silently label a published GAV as a source build.
No parser version, source pin, ubnfc generation pin or production behavior changes in this wiring.
