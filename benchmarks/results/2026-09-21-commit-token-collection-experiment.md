# commit 時と parse 後の token 経路の Stream 除去実験（2026-09-21）

## 結論

unlaxer-parser#218（CPU 基準で reopen）。`CollectingParser.collect`、`Token` コンストラクタ、`TokenList.toSource`、
`AbstractTokenReducer.reduce` の Stream pipeline を事前サイズ付きリストと添字ループに置き換えた（Java のみ。Rust は該当なし）。

Java public facade の 3-run 中央値は complex.tiny **-6.49%**、comparison-heavy.tiny **-3.43%**（前段 candidate の別セッションでは
-10.20% / -6.23%）。差は run 間ノイズ（±5% 程度）に近いが 2 セッション 12 run で方向が一貫し、allocation は精密に -8.3% / -9.3%。
Token の内容・順序・parent・source は不変で既存 test が全て通るため**採用**した。timing 効果は 5% 前後と見積もる。

## 着手前 profile（baseline = #223 適用後 `02ada0a`、JFR ExecutionSample）

「commit の token 収集・listener」が complex.tiny 29.4%、comparison-heavy.tiny 25.0%。深い stack（depth 24）での呼び出し元は
`AbstractTokenReducer.reduce`（`stream().map().forEach()` と `isEmpty`）、`CollectingParser.collect`、`Token.<init>`、`TokenList.toSource`。

## Allocation（Java、JMH `gc.alloc.rate.norm`）

| Fixture | before（#223） | after | 変化 |
|---|---:|---:|---:|
| complex.tiny | 165,038,565 B | 151,339,288 B | -8.3% |
| comparison-heavy.tiny | 86,272,082 B | 78,253,655 B | -9.3% |

## 測定条件

- unlaxer-parser baseline: `02ada0a`（#224 merge commit）、candidate: `17bfe6d`（PR #225）
- tinyexpression: `29608f4`（#151 merge commit）。Rust runtime は変更なしのため Rust timing は取らず、pin（`4658223`）も #220 のまま
- fixture / CPU / OS / JMH 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（Java public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 77.836 / 76.848 / 75.458 | **76.848** | 71.632 / 71.858 / 77.740 | **71.858** | **-6.49%** |
| Java | comparison-heavy | 40.461 / 40.613 / 41.461 | **40.613** | 37.474 / 39.221 / 43.641 | **39.221** | **-3.43%** |

1 回目の A/B（candidate = `collect` と `toSource` のみ、raw は保存せず要約のみ）: complex 81.235 → 72.951 ms（-10.20%）、
comparison-heavy 40.689 → 38.156 ms（-6.23%）。2 回目（上表、`Token.<init>` と `AbstractTokenReducer.reduce` を追加した最終 candidate）の
raw を保存している。

## 正確性

- unlaxer-parser: Java 672 + 987 tests
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 生データ

`raw/2026-09-21-commit-token-collection/`: JMH のテキスト出力 `java-{baseline,candidate}-run{1,2,3}.log`（`-rff` の JSON は worktree 整理時に失われたため、同じ run の JMH 標準出力を保存。score と各 iteration の値を含む）、`java-gc-alloc-*`、`java-jfr-cpu-*`、`aggregate-jfr-cpu.py`
