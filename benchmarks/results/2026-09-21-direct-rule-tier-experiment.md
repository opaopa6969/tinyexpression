# direct-rule-call execution tier 実験（2026-09-21、不採用）

## 結論

unlaxer-parser#210。生成 Rust parser に opt-in の direct tier（rule ごとの生成関数、既定 OFF）を実装し、TinyExpression
public facade で combinator/interpreter tier と比較したが、有意な改善がなく**不採用**。実装は unlaxer-parser の参照用 branch
`perf/direct-rule-tier-210`（`7666f66`）に残し、tinyexpression の生成物と pin は変更しない。

## 計測（Criterion `public-facade`、tier ごとに 3 run、mean の中央値、ms/op）

| Tier | Fixture | Run 1 | Run 2 | Run 3 | 中央値 | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Combinator | complex | 5.2363 | 5.1158 | 5.0537 | 5.1158 | |
| Direct（第 2 段） | complex | 4.9654 | 5.0761 | 4.9715 | **4.9715** | **-2.8%** |
| Combinator | comparison-heavy | 1.2969 | 1.2779 | 1.3949 | 1.2969 | |
| Direct（第 2 段） | comparison-heavy | 1.3301 | 1.2812 | 1.2970 | **1.2970** | **±0** |

第 1 段（sub-expression ごとに関数）: complex +11.2%、comparison-heavy -2.6%（各 1 run）。

## 生成量・build 時間（`rust/tinyexpression-rs/src/generated/parser.rs`、cold `cargo build --release`）

| 項目 | Combinator | Direct 第 1 段 | Direct 第 2 段 |
|---|---:|---:|---:|
| 関数数 | 9 | 1,372 | 134 |
| bytes | 44,258 | 355,762 | 209,135 |
| 行数 | 212 | 7,423 | 3,331 |
| build | 3.49 s | 10.93 s | 7.82 s |

TinyExpression P4 は 124 rule 中 112 が direct、12 が `RuleEffects` により rule 全体 fallback。

## 測定条件

- unlaxer-parser: master `d3093cf` 相当の runtime に direct tier を追加した worktree（branch `perf/direct-rule-tier-210`）
- tinyexpression: `346e270` を `mktemp -d` にコピーし、direct tier ON で parser を再生成、path patch でローカル runtime を使用（tracked ファイルは変更なし）
- rustc/cargo 1.85、Criterion 0.5、既定設定。計測は Codex が同一ホストで直列に実施

## 正確性

- unlaxer-parser: combinator と direct の観測等価 test（結果・cursor・`expected`・CST・capture・checkpoint metrics、memo OFF / SafeFailures、fallback → interpreter → direct の往復）、workspace fmt / clippy / test、native と Java `RustBackend` の byte parity（`RustNativeEmitterTest`）
- tinyexpression-rs（direct ON 再生成）: 32 tests pass

## 判断

#220 後の Rust CPU attribution（`raw/2026-09-21-innermost-frame-merge/rust-cpu-attribution-after-220.txt`）で memo 25%、checkpoint 12%、
CST 8% は direct 化でも不変であり、削れるのは enum dispatch と間接参照のみ。生成量 4.7 倍・build 2.2 倍に対し timing 効果は
run 間ノイズ相当のため採用しない。Java 側は JIT の virtual dispatch 最適化があり、Rust の結果から着手しなかった。
次候補は memo lookup/insert のコスト（Rust 25%、Java `PackratMemoTable`）。
