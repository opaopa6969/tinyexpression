# Java / Rust generated parser benchmark

This benchmark compares the generated Java and Rust frontends from the same
TinyExpression P4 UBNF. It is an opt-in developer benchmark, not a CI timing
gate.

Recorded results:

- [2026-09-20 Java/Rust parser benchmark](results/2026-09-20-java-rust-parser.md)
- [2026-09-20 Rust shared-grammar follow-up](results/2026-09-20-rust-shared-grammar.md)

## Corpus

All benchmark processes read the same checked-in UTF-8 files once during trial
setup. Setup also verifies that every frontend accepts the complete document.
The benchmark is a throughput experiment, not a semantic-equivalence proof:
the repository's `P4RustSharedFixtureAcceptanceTest` separately checks canonical
Java/Rust AST and diagnostic parity on the shared conformance fixtures.

| fixture | purpose | Unicode code points | UTF-8 bytes |
|---|---|---:|---:|
| `complex.tiny` | declarations, annotation, boolean logic, functions, `if`, `match`, method declaration, non-BMP text | 325 | 332 |
| `flat-arithmetic.tiny` | 256 operands in one left-associative arithmetic expression | 1,426 | 1,426 |
| `large-match.tiny` | declaration and 64 match cases | 1,380 | 1,380 |

## Measurement boundaries

Each language reports three steady-state operations:

- `parse-only`: parse the complete source into its native parse tree.
- `map-only`: map an already parsed tree/token into the generated typed AST.
- `parse+map`: run the generated parser and mapper path from source to typed AST.

File I/O, process startup, JSON serialization, full-input benchmark validation,
and the first lazy Java parser lookup are outside the timed region. The Java
parse path returns the committed grammar-root token without rendering or
reducing its tree. Its default and memoized measurements use the same manual
parser/mapper path, so the `ParseContext.enableMemoize()` flag is the only A/B
difference. Java uses JMH and Rust uses Criterion; their reported distributions
are more useful than a single wall-clock loop. Do not compare CLI startup
timing with these values.

These are direct generated-frontend measurements. TinyExpression's production
`P4PreferredAstMapper` facade enables Java packrat memoization by default; the
memoized JMH rows therefore approximate its parser policy more closely than the
non-memoized baseline, while deliberately excluding facade-specific work.

The current implementations do not perform identical internal setup. Java
reuses a lazy singleton parser graph and its mapper serializes access to global
identity-based source maps. Since unlaxer-parser `3c38c96`, Rust also reuses an
immutable process-wide grammar graph while keeping every mutable `ParseContext`
local. The benchmark intentionally measures the generated/runtime implementations
currently shipped while keeping the cross-target operation boundaries comparable;
other internal differences must still be considered when interpreting
language-level conclusions.

## Running

Run commands from the repository root. The Java benchmark is enabled only by
its Maven profile, and the Rust benchmark is an explicit Cargo bench target.

```sh
benchmarks/run-java-parser-benchmark.sh P4ParserBenchmark

cargo +1.85.0 bench --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs --features benchmarks --bench parser
```

To benchmark against an unlaxer build installed in an isolated Maven
repository, set `TINYEXPRESSION_MAVEN_REPO_LOCAL` to that repository path. The
wrapper builds the complete test/runtime classpath explicitly so JMH forked
VMs use the same generated frontend; invoking JMH through Maven's in-process
classloader is intentionally avoided.

Use an otherwise idle machine, retain the raw JMH/Criterion output, and record
the Java, Rust, OS, CPU, repository, and pinned unlaxer revisions with results.
