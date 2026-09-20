# parse stack snapshot の persistent 親リンク構造実験（2026-09-21）

## 結論

unlaxer-parser#235（#214 Phase 3 の続き）。#229 の `Parser[]` + `int[]` snapshot を、frame ごとの不変 node を親リンクで繋ぐ
persistent 構造にし、`ParseFrame` が現在の offset で作った node をキャッシュする（Java のみ。Rust は #220 以降 snapshot を持たない）。

**採用。** Java public facade の 3-run 中央値は complex.tiny **-15.83%**（49.919 → 42.016 ms/op）、comparison-heavy.tiny **-10.05%**
（28.650 → 25.770 ms/op）で、両 fixture とも baseline と candidate の run が完全に分離した。allocation は -20.9% / -14.5%。
診断 stack の内容・順序は不変（既存 test で固定）。

## 着手前の精密計測（master `11d3239`、一時カウンタ、public facade 1 parse）

| fixture | `snapshotStackElements()` 回数 | 平均 stack 深さ | 概算バイト | 総 allocation 比 |
|---|---:|---:|---:|---:|
| complex.tiny | 25,224 | 41.9 | 19.5 MB | 23.5% |
| comparison-heavy.tiny | 17,269 | 21.7 | 7.1 MB | 15.3% |

呼び元の大半は memo diagnostic frame 側（complex: 同 offset でより深い 11,396 回、memo frontier 前進 4,751 回）。
JFR ObjectAllocationSample の site 比率（comparison-heavy 47.6%）は過大で、精密値は 15.3%。

## Allocation（Java、JMH gc.alloc.rate.norm）

| Fixture | baseline | candidate | 変化 |
|---|---:|---:|---:|
| complex.tiny | 82,964,099 B | 65,641,335 B | **-20.9%** |
| comparison-heavy.tiny | 46,361,449 B | 39,651,879 B | **-14.5%** |

## 測定条件

- unlaxer-parser baseline: `11d3239`（#234 merge commit）、candidate: `5ec402b`（PR [#237](https://github.com/opaopa6969/unlaxer-parser/pull/237)）
- tinyexpression: `036aaf4`。Java は isolated Maven repo。Rust runtime は変更なし
- fixture / CPU / OS / JMH 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（Java public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 50.725 / 48.978 / 49.919 | **49.919** | 44.610 / 42.016 / 41.936 | **42.016** | **-15.83%** |
| Java | comparison-heavy | 28.650 / 28.927 / 27.972 | **28.650** | 25.770 / 25.797 / 25.627 | **25.770** | **-10.05%** |

## 正確性

- unlaxer-parser: Java 672 + 987 tests（診断 stack の内容・順序は既存 test で固定済み）
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 生データ

`raw/2026-09-21-persistent-stack-snapshot/`: JMH 結果 `java-{baseline,candidate}-run{1,2,3}.json`、`java-gc-alloc-{baseline,candidate}.json`
