# StringSource 構築コスト削減実験（2026-09-21）

## 結論

unlaxer-parser#228。`StringSource` の `codePoints()` IntStream をループに置き換え、空 source の resolver を共有した（Java のみ）。
Java public facade の 3-run 中央値は complex.tiny **-8.73%**、comparison-heavy.tiny **-12.14%**、allocation は -14% / -17%。
Source の値・位置・cursor range は不変で既存 test が全て通るため**採用**した。

## 着手前 profile（baseline = #218 適用後 `d3093cf`、JFR ObjectAllocationSample 1,847 サンプル）

`IntPipeline$Head` ← `String.codePoints()` ← `StringSource.<init>` が allocation の 26.0%。`PositionResolverImpl.<init>` の HashMap 1.4%、
`CursorRange.of` の cursor 1.3%。適用後（1,799 サンプル）は `ParseStackElement`（診断 snapshot）が 44.5% で最大（→ #229）。

## Allocation（Java、JMH gc.alloc.rate.norm）

| Fixture | before | after | 変化 |
|---|---:|---:|---:|
| complex.tiny | 151,339,288 B | 129,900,726 B | -14.2% |
| comparison-heavy.tiny | 78,253,655 B | 64,456,926 B | -17.6% |

## 測定条件

- unlaxer-parser baseline: `7e07c2c`（#226 merge commit、runtime は `d3093cf` と同一）、candidate: `932022b`（PR #231）
- tinyexpression: `f4d7ba2`。Java は isolated Maven repo。Rust runtime は変更なしのため Rust timing は取らない
- fixture / CPU / OS / JMH 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（Java public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 68.846 / 68.094 / 69.997 | **68.846** | 58.853 / 63.884 / 62.837 | **62.837** | **-8.73%** |
| Java | comparison-heavy | 37.020 / 36.335 / 38.193 | **37.020** | 32.667 / 32.526 / 31.444 | **32.526** | **-12.14%** |

## 正確性

- unlaxer-parser: Java 672 + 987 tests（初版は `Source.EMPTY` の静的初期化順で unlaxer-dsl 全 test が失敗し、holder class で修正）
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 生データ

`raw/2026-09-21-string-source-construction/`: JMH 結果 `java-{baseline,candidate}-run{1,2,3}.json`、`java-gc-alloc-*`、`java-jfr-allocation-complex-*`
