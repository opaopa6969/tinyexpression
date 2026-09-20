# TinyExpression P4 final Java/Rust complex-expression benchmark（2026-09-20）

## 結論

`complex.tiny` の生成parser/mapper経路を、memoization無効（Off）と安全な失敗結果だけを
memoizeするモード（Safe）で3回ずつ測定した。3 runの中央値では、JavaのSafeは
parse-onlyで352.039 ms/op、parse+mapで347.880 ms/op、RustのSafeはそれぞれ
84.045 ms/op、89.453 ms/opだった。Safe同士ではRustがJavaよりparse-onlyで4.19倍、
parse+mapで3.89倍高速だった。

Safe failure memoizationの効果は大きく、JavaはOff比21.53〜22.26倍、Rustは
10.74〜10.84倍高速になった。mapping単体はJava 137.390 µs/op、Rust 16.416 µs/opで、
両実装とも複雑式の総時間はparser探索が支配している。

このSafe policyをTinyExpression production facadeへそのまま適用するのは、今回のPRでは
見送った。追加の互換性検証で、既存のfraud-formula性能testがSafeでは3.323秒まで退行し、
深いnested `if` が10秒deadlineを超えることを確認した。公開runtimeのlegacy memoizationでは
同じ5式が43/9/4/34/29 msで通る。Safeは正しさを優先した新しい比較対象であり、既存の
production policyを置き換えるには成功結果を含む安全な高速化が別途必要である。

したがってJava側の次の最適化はmapperではなく、`@longestChoice` を含む探索分岐と
alternate-root/result-family retryの除去を優先する。allocation、diagnostic frame、token treeは
その後にprofilerで分離する。成功結果のmemoizationや生成された直接parser codeも候補だが、
`ParseContext` のtransaction/state replayを弱めず、状態versionをkeyへ含める設計が前提になる。

## 条件

- fixture: `benchmarks/fixtures/complex.tiny`（325 Unicode code points / 332 UTF-8 bytes）
- tinyexpression基準revision: `6e5cfd91619b71a53e8f391469653d270208ca2e` とPR #138のbenchmark差分
- unlaxer generator/runtime revision: `c1499b909ad03fac95791a82f0268d6924a2d630`
- CPU: AMD Ryzen 9 7950X、16 cores / 32 logical CPUs
- OS: Linux `6.18.33.2-microsoft-standard-WSL2`、x86_64
- Java: Oracle JDK 21.0.9、JMH 1.37
- Rust: rustc/cargo 1.85.0、Criterion 0.5.1
- Java: 1 thread、2 forks、5 × 1秒 warmup、8 × 1秒 measurement、AverageTime
- Rust: 5秒 warmup、10秒 measurement、100 samples。遅いcaseではCriterionが100 samplesを
  得るため、測定区間を約89〜98秒へ自動延長
- 実行順: Java run 1 → Rust run 1 → Java run 2 → Rust run 2 → Java run 3 → Rust run 3
- 各runは同じself-hosted host上で、他のbenchmark/build/CIと重ねずに実行

各runの代表値はJMH `primaryMetric.score` とCriterion `mean.point_estimate` で、最終比較には
3 runの中央値を使う。JMHとCriterionは異なるharness・信頼区間・統計モデルを使うため、
言語処理系そのものの一般的な優劣ではなく、この生成frontendとruntimeの比較として読む。
ファイルI/O、process startup、fixture検証、最初のparser graph初期化は測定外である。

## 結果

parse系はms/op、map-onlyはµs/op。太字が3 run中央値である。

| Target | Operation | Run 1 | Run 2 | Run 3 | Median |
|---|---|---:|---:|---:|---:|
| Java | parse-only-off | 8,275.786 | **7,579.665** | 7,408.934 | **7,579.665 ms** |
| Java | parse-only-safe | 354.797 | 331.519 | **352.039** | **352.039 ms** |
| Java | map-only | 135.363 | 159.198 | **137.390** | **137.390 µs** |
| Java | parse+map-off | 8,125.348 | **7,742.100** | 7,285.200 | **7,742.100 ms** |
| Java | parse+map-safe | 341.676 | 371.979 | **347.880** | **347.880 ms** |
| Rust | parse-only-off | 955.186 | 892.944 | **910.872** | **910.872 ms** |
| Rust | parse-only-safe | 88.991 | **84.764** | 84.045 | **84.764 ms** |
| Rust | map-only | 16.915 | 15.445 | **16.416** | **16.416 µs** |
| Rust | parse+map-off | 1,004.156 | **960.379** | 953.138 | **960.379 ms** |
| Rust | parse+map-safe | 94.992 | 84.715 | **89.453** | **89.453 ms** |

太字のrun欄は中央値の所在を示す。

| Comparison | parse-only | parse+map |
|---|---:|---:|
| Java Safe speedup over Off | 21.53× | 22.26× |
| Rust Safe speedup over Off | 10.84× | 10.74× |
| Java / Rust, Off | 8.32× | 8.06× |
| Java / Rust, Safe | 4.19× | 3.89× |

map-onlyのJava / Rust比は8.37×だが、絶対時間はparseに比べて小さい。

## 解釈と次の改善候補

1. `@longestChoice` とalternate-root/result-family retryを整理し、1入力を複数rootで再parseする
   経路をなくす。これはunlaxer-parser #187で追跡する。
2. Java Safe経路をJFR/async-profilerで測り、parser allocation、diagnostic frame、token treeの
   比率を分離する。今回の数値だけから個別最適化の効果は断定しない。
3. 成功結果memoizationを追加する場合は、rollback後のbranch衝突を避けるstate versionと
   transaction lifecycle replayをJava/Rust双方で同じ受け入れテストにする。
4. Rustは現時点でstateful ruleをSafe対象から除外する一方、Javaはversioned `ScopeStore` を
   keyへ反映できる。この差を解消してからSafe policyの完全な言語間parityを主張する。
5. generated direct parser codeはcombinator runtimeを置き換えるのではなく、同じ
   `ParseContext` 契約を共有する追加backendとしてA/B測定する。
6. self-hosted CIはjobごとの空Maven repositoryを使う。同一version番号の開発artifactを
   共有 `~/.m2` にinstallしても、公開artifact向けFull verifyへ混入させない。

generated typed ASTを使う通常のcurated/supported経路には手書きevaluator fallbackはない。
ただしJava `P4PreferredAstMapper.parseRootToken` とRust `parse_alternate_root` には、現時点で
result-family/alternate-root retryが残るため、repository全体で「fallback/retryが厳密に0」とは
まだ主張しない。

## 生データと再現

- Java JMH: `2026-09-20-final-java-run1.json`〜`run3.json`
- Rust Criterion: `raw/2026-09-20-rust-final-run1/`〜`final-run3/`

```sh
benchmarks/run-java-parser-benchmark.sh 'P4ParserBenchmark.*' \
  -p fixture=complex.tiny -rf json -rff target/java-complex.json

cargo +1.85.0 bench --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs --features benchmarks --bench parser -- \
  complex --noplot --save-baseline local-complex
```

性能測定とは別に、canonical ASTとdiagnosticのJava/Rust parityは
`P4RustSharedFixtureAcceptanceTest` とRust conformance testsで検証する。
