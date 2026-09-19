# TinyExpression P4 Java/Rust parser benchmark（2026-09-20）

## 結論

生成frontendの直接APIを既定の `ParseContext` で呼ぶ比較では、Rustの `parse+map` はJavaよりfixture別に3.82〜12.41倍高速だった。ただし、Javaの生成 `ParseContext` でpackrat memoizationを明示的に有効にすると、複雑式は160.63倍、64-case matchは32.29倍改善した。したがって、測定結果は言語間の本質的な速度差ではなく、探索方針の差が支配的であることを示す。

TinyExpressionのproduction facadeである `P4PreferredAstMapper` はmemoizationを既定ONにしている。このため、Javaの実運用方針に近いのはmemoized行であり、non-memoized行は生成frontendの直接APIとRustの現行APIを同じ明示的な境界で比較するbaselineである。

mapping単体は両実装ともparseより十分小さい。次の改善対象は、状態を持つ `ParseContext` の契約を保存したJava/Rust共通の明示的memoization方針と、Rust生成grammar graphの再利用である。

- context-aware memoization: [unlaxer-parser#184](https://github.com/opaopa6969/unlaxer-parser/issues/184)
- Rust grammar graph reuse: [unlaxer-parser#185](https://github.com/opaopa6969/unlaxer-parser/issues/185)

## 条件

- tinyexpression基準revision: `96cc6c61e0253430fca1e86009b609477b013db6`
- unlaxer generator/runtime revision: `77b3ca1b59f6e50d31c455a8a8c9930bc8fce3d5`
- CPU: AMD Ryzen 9 7950X、16 cores / 32 logical CPUs
- OS: Linux `6.18.33.2-microsoft-standard-WSL2`、x86_64
- Java: Oracle JDK 21.0.9、JMH 1.37
- Rust: rustc 1.85.0、Criterion 0.5.1
- Java: 1 thread、2 forks、5 × 1秒 warmup、8 × 1秒 measurement、AverageTime
- Rust: 5秒 warmup、10秒 measurement、100 samples。Criterionは遅いcaseで100 samplesを得るため、実測区間を最大約152秒まで自動延長

各値はms/opで、`平均 [confidence interval]` と表記する。JMHは99.9% CI、Criterionは95% CIであり、統計手法も同一ではない。ファイルI/O、process startup、JSON serialization、fixtureの全入力検証、Java parser graphの最初のlazy lookupは測定外である。Javaのmemoized/non-memoized A/Bは、production facadeを経由せず、同じ生成parser/mapper経路で `ParseContext.enableMemoize()` の有無だけを変更した。

これは共有開発機上のlocal measurementであり、publication-gradeの隔離環境で得た値ではない。またJavaはlazy singleton parser graphを再利用し、Rustは現状 `rules()` とruntime所有sliceをparseごとに構築する。数値をJava/Rust言語そのものの優劣として解釈してはならない。

## Corpus

| Fixture | 内容 | Code points | UTF-8 bytes |
|---|---|---:|---:|
| `complex.tiny` | 宣言、Unicode annotation、boolean/string membership、関数、`if`、`match`、method宣言 | 325 | 332 |
| `flat-arithmetic.tiny` | 左結合の算術式、256 operands | 1,426 | 1,426 |
| `large-match.tiny` | 宣言と64 match cases | 1,380 | 1,380 |

## Java JMH

| Fixture | Operation | ms/op [99.9% CI] |
|---|---|---:|
| complex | parse-only | 5,802.922 [5,508.126, 6,097.718] |
| complex | map-only | 0.128716 [0.114244, 0.143187] |
| complex | parse+map | 5,822.431 [5,562.909, 6,081.952] |
| complex | parse-only memoized | 34.615 [30.488, 38.741] |
| complex | parse+map memoized | 36.248 [30.399, 42.096] |
| flat-arithmetic | parse-only | 182.312 [160.859, 203.764] |
| flat-arithmetic | map-only | 0.478797 [0.468353, 0.489241] |
| flat-arithmetic | parse+map | 154.012 [136.927, 171.097] |
| flat-arithmetic | parse-only memoized | 63.384 [62.245, 64.524] |
| flat-arithmetic | parse+map memoized | 76.334 [61.676, 90.992] |
| large-match | parse-only | 5,635.709 [5,381.252, 5,890.166] |
| large-match | map-only | 1.235444 [1.185610, 1.285278] |
| large-match | parse+map | 5,799.519 [5,415.491, 6,183.547] |
| large-match | parse-only memoized | 170.183 [135.899, 204.468] |
| large-match | parse+map memoized | 179.602 [159.314, 199.891] |

## Rust Criterion

| Fixture | Operation | ms/op [95% CI] |
|---|---|---:|
| complex | parse-only | 863.339 [851.978, 877.337] |
| complex | map-only | 0.015800 [0.015597, 0.016054] |
| complex | parse+map | 918.534 [910.305, 929.050] |
| flat-arithmetic | parse-only | 12.036 [11.833, 12.251] |
| flat-arithmetic | map-only | 0.073479 [0.072905, 0.074128] |
| flat-arithmetic | parse+map | 12.409 [12.042, 12.811] |
| large-match | parse-only | 1,535.407 [1,520.091, 1,553.016] |
| large-match | map-only | 0.127957 [0.127309, 0.128638] |
| large-match | parse+map | 1,516.278 [1,503.545, 1,531.531] |

## 比較

| Fixture | Java/Rust parse-only | Java/Rust parse+map | Java memoizationによるparse+map改善 |
|---|---:|---:|---:|
| complex | 6.72× | 6.34× | 160.63× |
| flat-arithmetic | 15.15× | 12.41× | 2.02× |
| large-match | 3.67× | 3.82× | 32.29× |

productionのJava parse policyに近いmemoized行と現行Rustの `parse+map` を比べると、complexではJavaが25.34倍、large-matchではJavaが8.44倍高速だった。一方flat-arithmeticではRustが6.15倍高速だった。memoizationの効果はgrammar探索形状に強く依存する。

Java/Rustのmap-onlyはJavaが6.52〜9.66倍遅いが、絶対時間は最大でも1.24 ms/opである。今回のfixtureではparser探索が全体を支配するため、まずparse policyを改善し、その後にmapperのglobal identity mapとallocationをprofileするのが妥当である。

## 再現

repository rootで実行する。

```sh
benchmarks/run-java-parser-benchmark.sh P4ParserBenchmark \
  -rf json -rff target/jmh-parser-results.json

cargo +1.85.0 bench --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs --features benchmarks --bench parser
```

benchmark fixtureは両frontendで全入力acceptanceを確認する。これは性能実験であり、benchmark corpus自体の意味的同値性の証明ではない。canonical ASTとdiagnosticのJava/Rust parityは別の `P4RustSharedFixtureAcceptanceTest` が共有conformance fixtureで検証する。
