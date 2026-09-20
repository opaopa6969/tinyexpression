# transaction frame pool 再評価実験（2026-09-21、不採用）

## 結論

unlaxer-parser#208 の再評価。#229 適用後の JFR allocation-by-site で transaction frame の cursor
（`EndExclusiveCursorImpl` ← `ParserCursor` ← `TransactionElement.createNew`）が weight 27% と最大に見えたため、
popped frame（`TransactionElement` + `ParserCursor` + cursor ×2 + `TokenList`）を ParseContext ごとの pool で再利用する
実装を A/B した。

**不採用。** Java public facade の 3-run 中央値は complex.tiny +1.10%、comparison-heavy.tiny +3.64% でいずれもノイズ内、改善なし。
精密な `gc.alloc.rate.norm` の前後差は -5.5% / -7.3% で、JFR の 27% とは大きく違った。frame 生成は JIT の escape analysis と
TLAB で十分安く、pool の分岐・reset コストと相殺した。実装は unlaxer-parser branch `perf/transaction-frame-pool-208`（`e08a45d`）に残す。

## 着手前 profile（#229 適用後、JFR ObjectAllocationSample）

`EndExclusiveCursorImpl` ← `ParserCursor.<init>` ← `TransactionElement.createNew` が weight 27.1%（unlaxer-parser docs ケース13 の観測）。

## Allocation（Java、JMH gc.alloc.rate.norm）

| Fixture | baseline | candidate | 変化 |
|---|---:|---:|---:|
| complex.tiny | 88,983,103 B | 84,117,648 B | -5.5% |
| comparison-heavy.tiny | 46,389,002 B | 42,993,987 B | -7.3% |

candidate 側の JFR allocation-by-site（1,638 サンプル）では `HashMap$Node` ← `putMapEntries` ← `ScopeStore$State.checkpoint` が 27% で最大となり、
次の候補（#213 scope mutation journal）に繋がった。

## 測定条件

- unlaxer-parser baseline: `1da37ce`（#232 merge commit）、candidate: `e08a45d`（branch `perf/transaction-frame-pool-208`）
- tinyexpression: `cf0ef0c`。Java は isolated Maven repo。Rust runtime は変更なし
- fixture / CPU / OS / JMH 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（Java public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 51.814 / 53.041 / 57.252 | **53.041** | 54.992 / 53.623 / 51.481 | **53.623** | **+1.10%** |
| Java | comparison-heavy | 29.873 / 29.706 / 29.119 | **29.706** | 29.516 / 31.902 / 30.788 | **30.788** | **+3.64%** |

## 正確性

- unlaxer-parser: Java 672 + 987 tests
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 教材

allocation-by-site の比率は「候補の発見」に使い、採否は精密な `gc.alloc.rate.norm` の前後差と timing の A/B で決める
（#214 ケース4 と同じ結論に 2 度目で到達）。詳細は unlaxer-parser `docs/performance-tuning-ja.md` ケース14。

## 生データ

`raw/2026-09-21-transaction-frame-pool/`: JMH 結果 `java-{baseline,candidate}-run{1,2,3}.json`、`java-gc-alloc-{baseline,candidate}.json`、
`java-jfr-allocation-complex-candidate.txt`
