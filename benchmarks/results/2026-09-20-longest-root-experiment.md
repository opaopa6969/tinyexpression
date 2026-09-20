# `@longestChoice` root dispatch experiment（2026-09-20）

## 結論

`@longestChoice` は TinyExpression P4 の Number / Boolean / String / Object /
MethodInvocation / parenthesized expression の曖昧性を一度の root parse で正しく解決できた。
Java と Rust の parity、source span、既存 Java semantic root の互換 projection も維持でき、
汎用 alternate-root fallback は削除可能だった。

しかし `complex.tiny` の Safe memoization では、従来版に対して Java が約 6.0〜6.2 倍、
Rust が約 2.3 倍遅くなった。このため production grammar への適用は採用しない。
`@longestChoice` は局所的な曖昧性を解く opt-in combinator とし、root dispatch の次の候補は
FIRST 集合または先頭 token に基づく予測分岐とする。

## 実験した変更（不採用）

`Formula` の expression を、次の root 専用 rule に置き換えた。

```ubnf
@longestChoice
@mapping(ExpressionExpr, params=[value])
RootExpression ::=
    NumberExpression @value
  | BooleanExpression @value
  | StringExpression @value
  | ObjectExpression @value
  | MethodInvocation @value
  | '(' Expression @value ')' ;
```

Java `LongestChoice` は各候補を transaction 内で試し、rollback 後に勝者を再 parse する。
Rust は候補ごとの `ParseContext` snapshot から勝者状態を復元するため再 parse しない。
この実装差が、同じ文法変更でも Java の悪化倍率が大きい一因と考えられる。

## 正確性の確認

- Java: P4 backend parity、precedence、source mapping、共有 root-expression corpus
- Rust: workspace 32 tests（parser CLI、numeric/scalar evaluator を含む）
- bare comparison の Java 公開形状 `BooleanOrExpr` は、再 parse ではなく parse 後の
  source-preserving compatibility projection で維持できることを確認
- 候補版では Java/Rust とも汎用 alternate-root retry を削除して確認

候補差分は性能判定後に取り下げた。測定 JSON と本レポートだけを保存する。

## 測定条件

- fixture: `benchmarks/fixtures/complex.tiny`（325 Unicode code points）
- baseline tinyExpression: `b88fda19f47daa98fd5ad23e40483782989a2a4a`
- candidate unlaxer-parser: `ededf5fa1a549443e6dd7f759030a5971f96ed32`
- Java: JDK 21.0.9、JMH 1.37、5 warmup × 1 s、8 measurement × 1 s、2 forks
- Rust: rustc 1.85.0、Criterion 0.5.1、5 s warmup、100 samples
- host: AMD Ryzen 9 7950X、WSL2 Linux 6.18.33.2
- 比較対象: `parse-only-safe` と `parse+map-safe`
- 3 run の平均値から中央値を採用

## 結果

単位は ms/op。baseline は
[`2026-09-20-final-java-rust-complex.md`](2026-09-20-final-java-rust-complex.md)
の3-run中央値である。

| Runtime | Operation | Baseline | Candidate runs | Candidate median | 倍率 |
|---|---|---:|---:|---:|---:|
| Java | parse-only-safe | 352.039 | 2,273.853 / 2,122.430 / 2,105.888 | **2,122.430** | **6.03×** |
| Java | parse+map-safe | 347.880 | 2,197.040 / 2,082.493 / 2,155.826 | **2,155.826** | **6.20×** |
| Rust | parse-only-safe | 84.764 | 200.646 / 196.365 / 196.998 | **196.998** | **2.32×** |
| Rust | parse+map-safe | 89.453 | 204.809 / 201.985 / 202.796 | **202.796** | **2.27×** |

生データ:

- Java: `2026-09-20-longest-root-java-run1.json`〜`run3.json`
- Rust: `2026-09-20-longest-root-rust.json`

## 教材としてのポイント

1. 正しい combinator が、適切な dispatch strategy とは限らない。
2. transaction は副作用を隔離できるが、探索した枝の仕事量までは消せない。
3. 同じ意味論でも、Java の rollback + winner replay と Rust の snapshot restore で
   cost model は変わる。
4. fallback を 0 にすること自体を目標にせず、通常入力の hot path と診断互換性を
   同時に測る必要がある。
5. 次は grammar analysis で候補を絞り、判別不能な局所だけを longest-choice に渡す。

## 再現コマンド

```sh
TINYEXPRESSION_MAVEN_REPO_LOCAL=/path/to/isolated-m2 \
  benchmarks/run-java-parser-benchmark.sh \
  'P4ParserBenchmark.(parseOnlySafe|parseAndMapSafe)' \
  -p fixture=complex.tiny -rf json -rff /tmp/java-longest-root.json

cargo +1.85.0 bench --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs --features benchmarks --bench parser -- \
  'safe/complex' --noplot
```
