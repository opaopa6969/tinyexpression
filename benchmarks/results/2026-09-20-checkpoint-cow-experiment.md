# ParseContext checkpoint copy-on-write experiment（2026-09-20）

## 結論

unlaxer-parserのJava/Rust runtimeで、transaction開始時のrollback payload複製を、最初のmutation
まで遅延するcopy-on-writeへ変更した。TinyExpressionのproduction-facing `public-facade` を
各3回A/B測定し、変更を採用した。

3-run中央値はJavaで `complex.tiny` が1.30%、`comparison-heavy.tiny` が3.71%短縮した。
Rustはそれぞれ66.06%、54.20%短縮した。Javaの改善は小さいが回帰せず、Rustでは従来の
`HashMap` / scope / capture deep cloneをcheckpointごとに行うコストが明確に除去された。

## 実装

- Javaは新しい `MutationAwareTransactionalState` だけを遅延snapshot対象にする。既存の
  `TransactionalState` は互換性のため従来どおりeagerにsnapshotする。
- Java組み込みの `ScopeStore.State` は、scope、宣言、診断、referenceのmutation直前にbarrierを
  呼び、全open transaction frameのうち未snapshotのframeだけへundo stateを保存する。
- Rustはcapture、user state、scopeを `Rc` でcheckpointと共有し、mutation時の
  `Rc::make_mut` だけがdeep copyする。空payloadはcheckpointに割り当てない。
- Java/Rust双方に既定OFFの6 counterを追加した。計測OFF時のbenchmark hot pathには集計を
  持ち込まない。

cursor、CST/token長、capture、scope、user state、診断、source position、memoization version、
nested transactionのcommit/rollback契約は変更していない。Javaの第三者拡張点も既定では従来契約を
維持する。

## Counter profile

Formulaがfull consumeしない場合にBoolean、String、Objectをfresh contextで試すproduction facadeの
順序を再現し、safe failure memoizationを有効にした1 parseの合計である。

| Runtime / fixture | Opened | Commit | Rollback | Nonempty snapshot | Empty checkpoint | Deep copy |
|---|---:|---:|---:|---:|---:|---:|
| Java / complex | 41,917 | 7,967 | 33,950 | 1,341 | 40,576 | 1,341 |
| Java / comparison-heavy | 24,830 | 4,082 | 20,748 | 11 | 24,819 | 11 |
| Rust / complex | 53,985 | 13,238 | 40,747 | 53,983 | 2 | 1,621 |
| Rust / comparison-heavy | 15,135 | 1,492 | 13,643 | 14,593 | 542 | 132 |

Javaのpayload counterは登録された `TransactionalState` snapshot、Rustはcapture/state/scopeの
共有snapshotを数える。内部表現が異なるため絶対値をJava対Rustで比較しない。各runtime内では、
Javaの大半のframeがstate snapshot不要であり、Rustでもdeep copyはnonempty checkpointより
はるかに少ないことが分かる。

## 測定条件

- TinyExpression baseline/candidate base: `f0ae28d3`
- unlaxer-parser baseline: `2db8d0ea5ec528470d4a860462da40ea81bf12b6`
- unlaxer-parser candidate: `74527e553cdc095eebf4153e8c9f981470495dbf`
- fixture: `complex.tiny`（325 code points / 332 bytes）、`comparison-heavy.tiny`（179 / 179）
- CPU: AMD Ryzen 9 7950X、16 cores / 32 logical CPUs
- OS: Linux `6.18.33.2-microsoft-standard-WSL2`、x86_64
- Java: Oracle JDK 21.0.9、JMH 1.37、5 × 1秒warmup、8 × 1秒measurement、2 forks
- Rust: rustc/cargo 1.85.0、Criterion 0.5.1、5秒warmup、10秒measurement、100 samples
- 各runのJMH score / Criterion meanから3-run中央値を採用

JMHとCriterionは異なるharnessなので、Java対Rustの速度比ではなく、各runtime内のbaseline/candidate
A/Bとして読む。counterもinstrumentation有効時の単発profileであり、timed benchmarkでは無効である。

## 結果

単位はms/op。倍率が小さいほど速い。

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 321.388 / 325.559 / 331.553 | **325.559** | 345.758 / 321.311 / 316.706 | **321.311** | **-1.30%** |
| Java | comparison-heavy | 140.144 / 139.374 / 151.760 | **140.144** | 134.251 / 134.946 / 155.527 | **134.946** | **-3.71%** |
| Rust | complex | 80.028 / 83.107 / 79.981 | **80.028** | 27.161 / 26.310 / 27.727 | **27.161** | **-66.06%** |
| Rust | comparison-heavy | 7.832 / 7.361 / 7.800 | **7.800** | 3.573 / 3.841 / 3.505 | **3.573** | **-54.20%** |

生データ:

- Java: `2026-09-20-checkpoint-cow-{baseline,candidate}-java-run1.json`〜`run3.json`
- Rust: `raw/2026-09-20-rust-checkpoint-cow-{baseline,candidate}-run1/`〜`run3/`

## 検証

- Java: mutationなし、nested transaction、late registration、listener、scope/diagnostic/reference、
  memoization versionを含むruntime回帰test
- Rust: capture、typed user state、scope、nested commit/rollback、empty/nonempty checkpoint counter、
  copy-on-write deep copy counterを含むworkspace test
- TinyExpression: Java P4 backend parity/source mapping/Rust共有fixture、Rust 32 tests、公開facadeの
  両fixture full parse

## 再現コマンド

```sh
TINYEXPRESSION_MAVEN_REPO_LOCAL=/path/to/isolated-m2 \
  benchmarks/run-java-parser-benchmark.sh \
  'P4ParserBenchmark.publicFacade' \
  -p 'fixture=complex.tiny,comparison-heavy.tiny' -rf json -rff /tmp/java.json

cargo +1.85 bench --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs --features benchmarks --bench parser -- \
  'public-facade' --noplot
```
