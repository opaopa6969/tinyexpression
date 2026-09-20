# ParseContext 失敗診断 bookkeeping の allocation 削減実験（2026-09-21）

## 結論

unlaxer-parser#215。失敗診断の進捗追跡が行っていた「比較して捨てる snapshot」（Java）と
「既にある expected の再確保・memo hit の clone」（Rust）をやめた。診断出力は変えていない。

public facade の 3-run 中央値は Java で complex.tiny **-44.36%**、comparison-heavy.tiny **-38.24%**、Rust で
**-29.17%**、**-25.40%** 短縮した。診断出力・AST・source span の parity test は変更なしで通るため**採用**した。
Java は allocation を約 75% 削減した結果、young GC と allocation 帯域の負荷がそのまま時間に効いた。Rust は
allocation 回数を約 85〜89% 削減し、`BTreeSet<String>` の挿入と memo hit の clone が消えた分が効いた。

## 実装

- Java `ParseContext`: `trackCursorProgress` / `registerFailureCandidate` で parse stack snapshot の生成前に深さを比較し、
  採用される場合だけ生成する。1 回の snapshot を global と memo frame で共有し、どの frontier にも届かない失敗は
  hint 収集前に return、memo diagnostic frame が空なら iterator を作らない。`snapshotStackElements` の中間 copy を省く。
- Rust `unlaxer-runtime`: expected 集合を `BTreeSet<Rc<str>>` にし、重複時は allocation なし。複数 frame への記録で
  `Rc` を共有し、memo hit は clone せず `remove` → 参照 replay → 再挿入。`ParseError.expected: Vec<String>` は不変。

## Allocation（public facade 1 parse）

| Runtime | Fixture | before | after | 変化 |
|---|---|---:|---:|---:|
| Java（JMH `gc.alloc.rate.norm`） | complex.tiny | 1,176,448,393 B | 298,889,654 B | -74.6% |
| Java | comparison-heavy.tiny | 431,940,718 B | 131,595,598 B | -69.5% |
| Rust（allocations / bytes） | complex.tiny | 789,084 / 17,715,406 | 89,265 / 10,112,156 | -88.7% / -42.9% |
| Rust | comparison-heavy.tiny | 82,094 / 4,017,463 | 13,478 / 3,021,260 | -83.6% / -24.8% |

変更前の attribution:

- Java（JFR、complex）: `ArrayDeque.iterator()` ← `trackCursorProgress` 48.8%、`snapshotStackElements` 12.9%、
  `localStackSnapshot` 系の `ArrayList` copy 約 25%
- Rust（per-site counter、complex）: `FailureDiagnostic::record` の expected 保存 86.0%、memo hit の clone 5.4%、
  Fragment/CST `Vec` 1.2%（対象外）

変更後に残った主な allocation: Java は `CollectingParser.collect` の `TokenList.stream()`（残量の約 43%、
commit 時の token 収集。別仮説）、Rust は Fragment/CST の `Vec`（#211 の範囲）。

## 測定条件

- unlaxer-parser baseline: `c243638`（#216 merge commit）、candidate: `90530cb`（PR #217）
- tinyexpression: `99678b3`（#147 merge commit）。Java は isolated Maven repo で baseline/candidate を切り替え、
  Rust は baseline を pinned rev、candidate を `--config patch` で unlaxer-parser worktree の path に向けて計測
- fixture: `complex.tiny`（325 code points）、`comparison-heavy.tiny`（179）
- CPU: AMD Ryzen 9 7950X、OS: Linux `6.18.33.2-microsoft-standard-WSL2`
- Java: Oracle JDK 21.0.9、JMH 1.37、5 × 1 秒 warmup、8 × 1 秒 measurement、2 forks
- Rust: rustc/cargo 1.85、Criterion 0.5、5 秒 warmup、10 秒 measurement、100 samples
- 各 run の JMH score / Criterion mean から 3-run 中央値。ホスト上で他の負荷なしに直列実行
- allocation 計測は別 run（1 fork、3 × 1 秒）。timing には使わない

## Timing（public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 324.001 / 321.120 / 315.319 | **321.120** | 178.221 / 178.669 / 178.662 | **178.662** | **-44.36%** |
| Java | comparison-heavy | 144.009 / 144.538 / 134.637 | **144.009** | 88.862 / 88.941 / 91.975 | **88.941** | **-38.24%** |
| Rust | complex | 25.784 / 25.916 / 27.139 | **25.916** | 19.378 / 18.357 / 16.966 | **18.357** | **-29.17%** |
| Rust | comparison-heavy | 3.786 / 3.500 / 3.469 | **3.500** | 2.611 / 2.844 / 2.565 | **2.611** | **-25.40%** |

単位は ms/op。各 run の JMH score / Criterion mean。Java 対 Rust の絶対値比較ではなく、各 runtime 内の baseline/candidate A/B として読む。
Java の run 間ばらつきは baseline で約 3%、candidate で 0.3% 未満。Rust candidate は 3 run で 16.97〜19.38 ms と幅があるが、
最も遅い run でも baseline の最速 run より 25% 以上速い。

## 正確性

- unlaxer-parser: Java 670 + 987 tests、Rust workspace tests（`checkpoint_allocation` / `diagnostic_allocation` 含む）、
  fmt / clippy `-D warnings`。同一 offset の失敗が順序に依らず最深 stack を保持する test を追加
- TinyExpression: p4-smoke 85 tests、Java 全 test。`P4PackratFraudFormulaTest` と `P4AstEvaluatorCalculatorTest` /
  `P4DslJavaCodeCalculatorTest` の一部はこのホストでは **baseline でも同様に失敗**する（「1 秒未満」の時間アサートと
  5 秒の probe deadline。tinyexpression#49 参照）。candidate の fraud formula #1 は 4,302 → 2,565 ms で baseline より速い。
  これらの gate は self-hosted CI の Full verify に委ねる
- tinyexpression-rs 32 tests（path patch）

## 生データ

`raw/2026-09-21-diagnostic-tracking-alloc/`:

- `java-{baseline,candidate}-run{1,2,3}.json`: JMH 結果
- `rust-{baseline,candidate}-run{1,2,3}/{complex,comparison-heavy}-estimates.json`: Criterion 結果
- `java-gc-alloc-{baseline,candidate}.json` / `java-gc-alloc-summary.txt`: JMH `-prof gc`
- `java-jfr-allocation-complex-{baseline,candidate}.txt` / `aggregate-jfr-allocation.py`: JFR allocation 集計
- `rust-allocation-attribution.txt`: Rust の per-site attribution と before/after

## 再現コマンド

```sh
# Java（isolated Maven repo へ unlaxer-parser を install しておく。flatten.skip は付けない）
TINYEXPRESSION_MAVEN_REPO_LOCAL=/path/to/isolated-m2 \
  benchmarks/run-java-parser-benchmark.sh 'P4ParserBenchmark.publicFacade' \
  -p 'fixture=complex.tiny,comparison-heavy.tiny' -rf json -rff /tmp/java.json

# Rust
cargo +1.85 bench --locked --manifest-path rust/Cargo.toml -p tinyexpression-rs --features benchmarks --bench parser -- 'public-facade' --noplot
```
