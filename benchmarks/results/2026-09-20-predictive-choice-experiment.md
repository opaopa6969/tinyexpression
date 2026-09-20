# `@predictiveChoice` experiment（2026-09-20）

## 結論

unlaxer-parser #203 で、ordered choice の意味論を維持したまま、保守的な FIRST prefix により
不可能な候補だけを除外する `@predictiveChoice` を Java / Rust の runtime、UBNF frontend、
generator に実装した。TinyExpression P4 の `Expression` へ適用し、正確性testと3 runの
benchmarkを行った。

この文法では production grammar への適用を見送る。3 run中央値はJava parse-onlyが2.25%
短縮、Rust parse+mapが5.19%短縮した一方、Java parse+mapは10.27%増加し、Rust parse-onlyも
1.75%増加した。run間のばらつきも大きく、総合的な改善とは判定できない。

原因は、`Expression` の候補間で `if`、`match`、`(`、変数、数値、関数などのFIRST集合が
広く重複するためである。また公開facadeのalternate-root fallbackは施策前後とも36ケース中
10ケースであり、この注釈では削減されなかった。汎用機能自体は、prefixが明確に分離した
dispatch rule向けのopt-in最適化としてunlaxer-parserへ残す。

## 実験した変更（TinyExpressionでは不採用）

```ubnf
@mapping(ExpressionExpr, params=[value])
@predictiveChoice
Expression ::=
    NumberExpression @value
  | BooleanExpression @value
  | StringExpression @value
  | ObjectExpression @value
  | MethodInvocation @value
  | '(' Expression @value ')' ;
```

生成器はliteral、number、identifier、quoted tokenから保守的なFIRST predictorを作る。
nullable、再帰、custom parser、解析不能な要素は `Any` とし、候補を除外しない。予測候補が
すべて失敗した場合は全候補を元の順序で再試行し、受理範囲とfarthest diagnosticを維持する。

実験中、rule参照が共有される大きな文法でpredictor式が指数的に複製される問題も検出した。
FIRST atomを平坦化・宣言順dedupし、rule単位でmemoizeし、64 atomを超えた集合は安全に
`Any`へ戻すよう修正した。TinyExpressionの生成宣言は約80 KiBから4,181文字になった。

## 正確性とfallback

- Java: P4 backend parity、precedence、source mapping、Rust root/numeric/scalar共有fixture
  （51 passed、1 skipped）
- Rust: `tinyexpression-rs`（32 passed）
- unlaxer-parser: Java runtime/codegen、Rust runtime/native frontend/Java frontend backend、
  nullable・再帰・trivia・diagnostic・memoization・FIRST上限を検証
- alternate-root probe: baseline `primary=26 / fallback=10 / total=36`
- candidate probe: `primary=26 / fallback=10 / total=36`

候補差分は性能判定後にTinyExpressionから取り下げた。benchmark結果だけを保存する。

## 測定条件

- fixture: `benchmarks/fixtures/complex.tiny`（325 Unicode code points / 332 UTF-8 bytes）
- TinyExpression baseline: `2e10536e`
- unlaxer-parser baseline: `ededf5fa1a549443e6dd7f759030a5971f96ed32`
- candidate: unlaxer-parser #203 の `@predictiveChoice` 実装
- CPU: AMD Ryzen 9 7950X、16 cores / 32 logical CPUs
- OS: Linux `6.18.33.2-microsoft-standard-WSL2`、x86_64
- Java: Oracle JDK 21.0.9、JMH 1.37、5 × 1秒 warmup、8 × 1秒 measurement、2 forks
- Rust: rustc/cargo 1.85.0、Criterion 0.5.1、5秒 warmup、10秒 measurement、100 samples
- 比較対象: `parse-only-safe` と `parse+map-safe`
- 各runのJMH score / Criterion meanから3 run中央値を採用

JMHとCriterionは異なるharnessであり、数%の差にはhost noiseも含む。この比較は同じruntime
内のbaseline/candidate A/Bとして読み、JavaとRustという言語一般の性能差には外挿しない。

## 結果

単位はms/op。baselineは
[`2026-09-20-final-java-rust-complex.md`](2026-09-20-final-java-rust-complex.md)
のSafe 3-run中央値である。倍率が1未満なら短縮、1超なら増加を表す。

| Runtime | Operation | Baseline | Candidate runs | Candidate median | 倍率 |
|---|---|---:|---:|---:|---:|
| Java | parse-only-safe | 352.039 | 344.122 / 351.762 / 334.956 | **344.122** | **0.978×** |
| Java | parse+map-safe | 347.880 | 317.731 / 383.617 / 398.160 | **383.617** | **1.103×** |
| Rust | parse-only-safe | 84.764 | 88.107 / 83.694 / 86.248 | **86.248** | **1.018×** |
| Rust | parse+map-safe | 89.453 | 87.038 / 81.258 / 84.811 | **84.811** | **0.948×** |

生データ:

- Java: `2026-09-20-predictive-java-run1.json`〜`run3.json`
- Rust: `raw/2026-09-20-rust-predictive-run1/`〜`run3/`

## 判断と次の仮説

1. `@predictiveChoice` は分離度の高い固定prefix dispatchへ限定し、注釈ごとにbenchmarkする。
2. TinyExpressionの次施策では、generic alternate-root fallback 10件をgrammar/root APIで直接
   解消する方が、重複FIRST集合の内部最適化より効果を期待できる。
3. predictor評価回数と実際に省略した候補数を任意の計測hookで可視化できれば、適用前に
   分岐の選別力を評価できる。これはruntime hot pathでは既定OFFにする。
4. 成功結果memoizationやdirect parser backendを試す場合も、`ParseContext` のtransaction、
   scope、capture、診断、source spanの契約を共通acceptance testで維持する。

## 再現コマンド

```sh
TINYEXPRESSION_MAVEN_REPO_LOCAL=/path/to/isolated-m2 \
  benchmarks/run-java-parser-benchmark.sh \
  'P4ParserBenchmark.(parseOnlySafe|parseAndMapSafe)' \
  -p fixture=complex.tiny -rf json -rff /tmp/java-predictive.json

cargo +1.85.0 bench --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs --features benchmarks --bench parser -- \
  'safe/complex' --noplot
```
