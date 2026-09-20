# scope 状態の mutation journal 実験（2026-09-21）

## 結論

unlaxer-parser#213（#214 Phase 5、scope 領域のみ）。Java `ScopeStore.State` と Rust `ScopeStore` の rollback を、
frame 初回 mutation 時の全複製（copy-on-write）から mutation ごとの逆操作 journal に置き換えた。

**採用。** Rust は public facade で complex.tiny -18.0%、comparison-heavy.tiny -21.5%（3-run 中央値）。
Java は allocation が complex.tiny で -6.8%、timing は complex.tiny -1.8% / -3.0%（2 セッション）と小さいが一貫して短縮。
comparison-heavy.tiny（scope を使わない）の Java timing は session 1 で +8.5% だったが session 2 で +1.3% となり、
変化する経路が無いことと合わせて noise と判断した。Rust では scope を使わない fixture でも短縮しており、
`Rc<ScopeStore>` clone/drop と `Checkpoint` payload という checkpoint 固定費が消えたことが理由。

## 着手前 profile

- Java（#208 再評価、JFR ObjectAllocationSample 1,638 サンプル）: `HashMap$Node` ← `putMapEntries` ← `ScopeStore$State.checkpoint` が weight 27%
- Rust counter（#207）: complex.tiny 1 parse で COW deep copy 1,621 回（大半が scope）

## Counter / Allocation

- Rust（complex.tiny）: COW deep copy 1,621 → 920（scope 由来 701 → 0）、scope journal entry 709。opened / committed / rolled_back / payload 判定は一致
- Java allocation（JMH gc.alloc.rate.norm）: complex 88,983,103 → 82,964,099 B/op（-6.8%）、comparison-heavy 46,389,002 → 46,361,449 B/op（±0、scope なし）

## 測定条件

- unlaxer-parser baseline: `1da37ce`（#232 merge commit）、candidate: `b158487`（rebase 後。merge commit `11d3239`）（PR [#234](https://github.com/opaopa6969/unlaxer-parser/pull/234)）
- tinyexpression: `cf0ef0c`。Java は isolated Maven repo、Rust は pin 済み rev `01baf7e`（runtime は master と同一）と worktree の path patch で A/B
- fixture / CPU / OS / JMH / Criterion 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust | complex | 4.917 / 5.075 / 4.988 | **4.988** | 3.972 / 4.089 / 4.194 | **4.089** | **-18.01%** |
| Rust | comparison-heavy | 1.296 / 1.354 / 1.318 | **1.318** | 1.066 / 1.018 / 1.034 | **1.034** | **-21.54%** |
| Java（session 1） | complex | 53.604 / 51.436 / 51.097 | **51.436** | 50.098 / 53.703 / 50.510 | **50.510** | -1.80% |
| Java（session 1） | comparison-heavy | 28.370 / 28.121 / 30.909 | **28.370** | 30.790 / 32.740 / 29.216 | **30.790** | +8.53%（noise、下記） |
| Java（session 2） | complex | 50.841 / 51.871 / 52.178 | **51.871** | 51.275 / 48.187 / 50.324 | **50.324** | -2.98% |
| Java（session 2） | comparison-heavy | 29.083 / 28.815 / 27.900 | **28.815** | 29.177 / 29.381 / 27.837 | **29.177** | +1.26% |

Java session 2 は session 1 の comparison-heavy +8.5% を確認するために Java だけ再実行したもの（生データは
`raw/2026-09-21-scope-mutation-journal-java2/`）。

## 正確性

- unlaxer-parser: Java 672 + 987 tests、Rust workspace tests（nested commit → outer rollback、LongestChoice の勝者/敗者分離、同名再宣言の rollback、capture/state COW との独立性を追加）、fmt / clippy
- TinyExpression: p4-smoke 85 tests（candidate repo）、tinyexpression-rs 32 tests（path patch）

## 生データ

`raw/2026-09-21-scope-mutation-journal/`: JMH 結果 `java-{baseline,candidate}-run{1,2,3}.json`、Criterion `rust-{baseline,candidate}-run{1,2,3}/`、`java-gc-alloc-{baseline,candidate}.json`。`raw/2026-09-21-scope-mutation-journal-java2/`: Java session 2 の JMH 結果
