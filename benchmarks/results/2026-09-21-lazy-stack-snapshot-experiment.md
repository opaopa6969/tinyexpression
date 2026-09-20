# 診断 stack snapshot のプリミティブ配列化実験（2026-09-21）

## 結論

unlaxer-parser#229。frontier 前進ごとに生成していた `ParseStackElement` の列を `Parser[]` + `int[]` の `StackSnapshot` にし、
`ParseFailureDiagnostics` 要求時にだけ展開する（Java のみ。Rust は #220 で snapshot を持たない）。

Java public facade の 3-run 中央値は comparison-heavy.tiny **-8.02%**（baseline 32.5〜34.5 ms と candidate 30.0〜31.8 ms の run が分離）、
complex.tiny **-1.53%**（ノイズ内）。allocation は精密に -13%（complex）/ -9%（comparison-heavy）。報告される stack は同一で既存 test が
全て通るため**採用**した。timing 効果は fixture により 0〜8% と見積もる。

## 着手前 profile（#228 適用後、JFR ObjectAllocationSample 1,799 サンプル）

`ParseStackElement` が allocation の 44.5%（`registerFailureCandidate` 29.6%、`trackCursorProgress` 12.5% + 1.7%）。

## Allocation（Java、JMH gc.alloc.rate.norm、baseline = #218 build）

| Fixture | before | after |
|---|---:|---:|
| complex.tiny | 151,339,288 B | 131,382,382 B |
| comparison-heavy.tiny | 78,253,655 B | 71,111,161 B |

適用後の最大項目は transaction frame の cursor（`EndExclusiveCursorImpl` ← `ParserCursor.<init>` ← `TransactionElement.createNew`）27.1%。

## 測定条件

- unlaxer-parser baseline: `6d93550`（#231 merge commit、#228 適用後）、candidate: `776a8f1`（PR #232）
- tinyexpression: `f4d7ba2`。Java は isolated Maven repo。Rust runtime は変更なし
- fixture / CPU / OS / JMH 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（Java public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 63.133 / 61.312 / 62.163 | **62.163** | 62.168 / 61.212 / 60.303 | **61.212** | **-1.53%** |
| Java | comparison-heavy | 32.486 / 34.520 / 33.923 | **33.923** | 31.832 / 31.203 / 30.015 | **31.203** | **-8.02%** |

## 正確性

- unlaxer-parser: Java 672 + 987 tests（診断 stack の内容・順序は既存 test で固定済み）
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 生データ

`raw/2026-09-21-lazy-stack-snapshot/`: JMH 結果 `java-{baseline,candidate}-run{1,2,3}.json`、`java-gc-alloc-*`、`java-jfr-allocation-complex-*`
