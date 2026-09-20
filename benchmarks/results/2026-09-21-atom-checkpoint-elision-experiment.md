# 失敗時に状態を変えない原子式の checkpoint 省略実験（2026-09-21、Rust、不採用）

## 結論

unlaxer-parser#239（#214 Phase 3 の続き、Rust のみ）。`ParseContext::expression` が全ての `Expr` に開いていた checkpoint を、
失敗経路で transactional state（position / matched_position / nodes / captures / state / scopes）に触らない原子式
（`Literal` / `Number` / `Identifier` / `Backreference` / `Empty` / `JavaEmpty` / `Eof` / `Error` / `Any` / `CharRange` / `Except` /
`Until` / `JavaUntil` / `JavaLookahead`）では省く。`Quoted` / `CodeStart` / `CodeEnd` / `Custom` / 複合式は従来どおり。

**不採用。** Rust public facade の Criterion 3-run 中央値は session 1 で complex.tiny -3.23% / comparison-heavy.tiny -2.27%（run の範囲が重なる）、
session 2 で +0.47% / -0.18%。改善は再現せず、効果が測れないため master には入れない。実装（differential test 3 件込み、142 tests 成功）は
unlaxer-parser branch `perf/elide-atom-checkpoint-239`（`d5ff7e4`）に残す。原子式 1 回の checkpoint は captures / state が空なら `Rc` clone も無く、
コンパイル後はほぼ構造体初期化だけで、着手前計測の「checkpoint 領域 7.0%」は `Instant` 区間の固定費（16〜18 ns）による過大評価だったと考えられる。

## 着手前 profile（master `11d3239`、`Instant` 一時 instrumentation）

checkpoint / commit / rollback が complex.tiny 7.0% / comparison-heavy.tiny 3.5%。1 parse あたり checkpoint 53,984 / 15,133 回、
rollback 40,747 / 13,642 回。

## 測定条件

- unlaxer-parser baseline: `b21a965`（#238 merge commit、tinyexpression の pin）、candidate: `d5ff7e4`（branch `perf/elide-atom-checkpoint-239`、PR なし）
- tinyexpression: `036aaf4` + #157 の pin。candidate は worktree の path patch
- fixture / CPU / OS / Criterion 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（Rust public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust（session 1） | complex | 3.869 / 3.847 / 4.077 | **3.869** | 3.845 / 3.744 / 3.740 | **3.744** | -3.23% |
| Rust（session 1） | comparison-heavy | 1.025 / 1.042 / 0.988 | **1.025** | 1.002 / 0.986 / 1.015 | **1.002** | -2.27% |
| Rust（session 2） | complex | 3.800 / 3.818 / 3.748 | **3.800** | 3.829 / 3.762 / 3.818 | **3.818** | +0.47% |
| Rust（session 2） | comparison-heavy | 1.002 / 1.004 / 1.002 | **1.002** | 1.032 / 1.000 / 0.986 | **1.000** | -0.18% |

## 正確性

- unlaxer-parser: `cargo fmt` / `clippy -D warnings` / `test --workspace --locked --offline`（142 tests、differential test 3 件を追加）、
  `unlaxer-alloc-audit` の契約維持、Java `RustNativeEmitterTest`
- TinyExpression: tinyexpression-rs 32 tests（path patch）

## 生データ

`raw/2026-09-21-atom-checkpoint-elision/`（session 1）と `raw/2026-09-21-atom-checkpoint-elision-rust2/`（session 2）: Criterion `rust-{baseline,candidate}-run{1,2,3}/`
