# memo hit 時の失敗診断一括 replay 実験（2026-09-21、Rust）

## 結論

unlaxer-parser#236（#214 Phase 3 の続き、Rust のみ）。safe-failure memo hit 時に memoized `FailureDiagnostic` の `expected` を
1 件ずつ `fail_at_shared` で戻していた経路を、diagnostic 単位の一括 merge（`FailureDiagnostic::merge` / `ParseContext::replay_failure`）に置き換えた。

**採用。** Rust public facade の Criterion 3-run 中央値は complex.tiny **-9.08%**（4.058 → 3.690 ms）、comparison-heavy.tiny **-6.09%**
（1.031 → 0.968 ms）で、両 fixture とも baseline と candidate の run が分離した。memo hit 率が高い complex.tiny（46%）の方が効果が大きい。
`ParseError.expected` の内容・順序は不変（4 test で固定）、公開 API は不変。

## 着手前 profile（master `11d3239`、`Instant` 一時 instrumentation、非計測 wall time を分母）

| 領域 | complex | comparison-heavy |
|---|---:|---:|
| failure diagnostic record / merge / replay | 29.4% | 28.6% |
| rule dispatch / その他（残差） | 32.7% | 47.0% |
| CST / Token 構築 | 17.0% | 6.7% |
| memo lookup / insert | 11.4% | 12.4% |
| checkpoint / commit / rollback | 7.0% | 3.5% |
| scope / journal | 2.6% | 1.8% |

1 parse あたり complex: diagnostic record 42,358（うち memo hit replay 33,873）、memo probe 13,902 / hit 6,440。
comparison-heavy: record 5,889（replay 3,280）、probe 3,549 / hit 496。

## 測定条件

- unlaxer-parser baseline: `11d3239`（#234 merge commit、tinyexpression の pin）、candidate: `1efcee3`（rebase 後。PR [#238](https://github.com/opaopa6969/unlaxer-parser/pull/238)）
- tinyexpression: `036aaf4`。candidate は worktree の path patch（`cargo --config 'patch...unlaxer-runtime.path=...'`）
- fixture / CPU / OS / Criterion 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（Rust public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust | complex | 4.030 / 4.058 / 4.347 | **4.058** | 3.703 / 3.690 / 3.673 | **3.690** | **-9.08%** |
| Rust | comparison-heavy | 1.092 / 1.031 / 1.031 | **1.031** | 0.956 / 0.998 / 0.968 | **0.968** | **-6.09%** |

## 正確性

- unlaxer-parser: `cargo fmt` / `clippy -D warnings` / `test --workspace --locked --offline`（139 tests、memo hit replay の順序・前進・同位置・手前を固定する 4 test を追加）、`unlaxer-alloc-audit` の診断 allocation 契約維持、Java `RustNativeEmitterTest`
- TinyExpression: tinyexpression-rs 32 tests（path patch）

## 生データ

`raw/2026-09-21-bulk-diagnostic-replay/`: Criterion `rust-{baseline,candidate}-run{1,2,3}/`
