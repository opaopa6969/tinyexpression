# Formula suffix factoring experiment (2026-09-20)

## Decision

**Rejected.** Keep the existing Java/Rust alternate-root retry for now.

Moving the method/EOF suffix inside every `Formula` choice branch made all 36 shared root
fixtures succeed through the generated `Formula` entry point, but it slowed the workload that
actually needs alternate-family dispatch: Java by **23.35%** and Rust by **12.12%** at the
median of three independent runs. The normal complex document improved by only 1--2%, which
does not compensate for the targeted regression.

The rejected production patch is not included in the repository. This commit retains the
public-facade benchmark, comparison-heavy corpus, raw measurements, and the result so that the
same design is not rediscovered without its cost evidence.

## Question and candidate

The generated `Formula` root tries `NumberExpression` first. A source such as `1 < 2` can commit
the numeric prefix before the outer `EOF` observes the remaining comparison. The production
facades therefore retry Boolean/String/Object roots in a fresh `ParseContext`; 10 of the 36
shared root-expression fixtures use that path.

The candidate moved `{ MethodDeclaration } EOF` into each ordered-choice branch:

```ubnf
Formula ::= prefixes { Annotation } (
    FormulaNumberExpression { MethodDeclaration } EOF
  | FormulaBooleanExpression { MethodDeclaration } EOF
  | FormulaStringExpression { MethodDeclaration } EOF
  | FormulaObjectExpression { MethodDeclaration } EOF
  | FormulaMethodInvocation { MethodDeclaration } EOF
  | FormulaParenthesizedExpression { MethodDeclaration } EOF
);
```

Each helper mapped back to the existing `ExpressionExpr` wrapper. The Java and Rust facade
retries were then removed. The intended benefit was that an incomplete numeric branch would
roll back within the same context before the Boolean branch ran, avoiding a second parser
context and a second top-level parse.

## Correctness gate

The experimental worktree passed:

- Rust `tinyexpression-rs`: 32 tests.
- Java root/parity/source-mapping selection: 35 tests.
- Direct generated `Formula` parsing: 36/36 shared root fixtures, up from 26/36 without the
  facade retry.
- Existing public AST shape and Java/Rust family selection were preserved; Java used a
  parse-free semantic-root projection after mapping.

This confirms that the design is semantically viable. It was rejected strictly on the measured
cost, not because the retry is required for correctness.

## Protocol

Both revisions used the same checked-in source files and production-facing APIs:

- `complex.tiny`: the existing full document hot path.
- `comparison-heavy.tiny`: 16 chained arithmetic comparisons, chosen to exercise the existing
  alternate-family path rather than benchmark an unreachable branch.
- Java: JMH 1.37, average time, 5 x 1 s warmup, 8 x 1 s measurement, two forks, three complete
  process runs.
- Rust: Criterion 0.5.1, 5 s warmup, 10 s measurement, 100 samples, three complete process runs.
- Result: median of each run's reported mean; lower is better.

Baseline revision: `d18c7ddaae5934764d1c68c3edf1b4cbee88ba59`.
The candidate changed only the authoritative grammar, regenerated Rust frontend, and Java/Rust
facade compatibility code needed to remove the retry. Both measurements used the same benchmark
harness.

Environment: AMD Ryzen 9 7950X (16 cores/32 threads), Linux 6.18 under WSL2, Oracle JDK 21.0.9,
Rust 1.85.1. The host was not isolated, so the repeated-run medians are decision evidence rather
than a universal performance claim.

## Results

| target / fixture | baseline runs (ms/op) | candidate runs (ms/op) | baseline median | candidate median | delta |
|---|---|---|---:|---:|---:|
| Java / complex | 353.010, 348.436, 342.391 | 344.945, 341.032, 332.792 | 348.436 | 341.032 | -2.12% |
| Java / comparison-heavy | 148.783, 138.826, 136.781 | 171.244, 169.364, 182.737 | 138.826 | 171.244 | **+23.35%** |
| Rust / complex | 79.529, 80.638, 82.611 | 79.346, 79.804, 83.811 | 80.638 | 79.804 | -1.03% |
| Rust / comparison-heavy | 7.211, 8.029, 7.383 | 7.870, 8.466, 8.278 | 7.383 | 8.278 | **+12.12%** |

The cross-language direction is consistent: factoring the suffix is neutral-to-small-positive
for the first root family, but worse for sources that must reject that family. The likely cost
is the larger same-context speculative transaction and rollback across duplicated suffix
branches. Creating a fresh context was not the dominant cost assumed by the hypothesis.

## Raw data

- Java JMH JSON:
  `2026-09-20-formula-{baseline,candidate}-java-run{1,2,3}.json`
- Rust Criterion estimates:
  `raw/2026-09-20-rust-formula-{baseline,candidate}-run{1,2,3}/`

## Tuning lesson and next boundary

`ParseContext` is a feature boundary, not overhead to remove by intuition. It carries rollback,
captures, diagnostics, and user state. A fresh-context retry can be cheaper than keeping a large
ordered choice transactional, even when it repeats parsing.

A future attempt should first profile transaction checkpoint bookkeeping and reduce empty or
unused snapshots in both runtimes. It should keep the public-facade benchmark introduced here,
and must beat the existing comparison-heavy path without regressing `complex.tiny` before
replacing the compatibility retry.
