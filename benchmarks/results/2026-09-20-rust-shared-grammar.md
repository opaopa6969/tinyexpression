# Rust共有grammar graph follow-up（2026-09-20）

## 結論

unlaxer-parser #185で、Rust生成parserの124-rule grammar graphを`OnceLock<Arc<[Rule]>>`として
process内で再利用するようにした。生成graphの構築と全rule cloneは通常parse経路から除去され、
cursor、capture、CST、diagnostic、scope、typed stateは引き続きparseごとの`ParseContext`に属する。

一方、TinyExpression P4のsteady-state parse時間には一貫した改善が見られなかった。
flat-arithmeticの`parse+map`は4.3%短縮したが、complexは3.3%、large-matchは6.8%長くなった。
これは異なるlocal run間のばらつきを含み、共有graphだけによる有意な改善とは判定しない。
6-rule microbenchmarkではsetup-onlyが364.1 ns/opから2.4 ns/opへ減ったため固定費除去自体は確認済みだが、
124-rule実式ではbacktracking探索が支配的である。次の性能課題はcontext-aware memoization
（unlaxer-parser #184）であり、grammar共有とは別に測る。

## 条件

- tinyexpression基準: base `fe58088890ab84804f48e46ee2d984e1e2607357` に、この文書を含む
  #117のdependency pin・再生成差分を適用したworking tree
- unlaxer generator/runtime revision: `3c38c96a08aa452f5b50f8682fe04f6409f1008c`
- 比較元: [Java/Rust parser benchmark](2026-09-20-java-rust-parser.md) のRust Criterion行
- CPU: AMD Ryzen 9 7950X、16 cores / 32 logical CPUs
- OS: Linux WSL2、x86_64
- Rust: rustc 1.85.0、Criterion 0.5.1
- 5秒warmup、10秒measurement、100 samples
- 遅いcaseは100 samplesを得るためCriterionが最大約173秒まで自動延長

両runは同じfixture、benchmark code、公開parse/map入口を使用したが、隔離された同一process内の
A/Bではない。したがって数%の増減を改善・退行の確証として扱わない。map-onlyはgrammar共有の
対象外であり、run間ノイズを見る参考値である。

## 結果

値はms/opのCriterion reported estimateと95% confidence interval。Criterionの
`estimates.json`でslopeが得られるcaseはslope、遅いcaseでslopeがnullのときはmeanを使う。

| Fixture | Operation | 旧revision | 共有grammar revision | estimate差 |
|---|---|---:|---:|---:|
| complex | parse-only | 863.339 [851.978, 877.337] | 888.450 [876.010, 902.470] | +2.9% |
| complex | map-only | 0.015800 [0.015597, 0.016054] | 0.017247 [0.016947, 0.017630] | +9.2% |
| complex | parse+map | 918.534 [910.305, 929.050] | 948.880 [937.970, 961.640] | +3.3% |
| flat-arithmetic | parse-only | 12.036 [11.833, 12.251] | 11.959 [11.726, 12.227] | -0.6% |
| flat-arithmetic | map-only | 0.073479 [0.072905, 0.074128] | 0.073347 [0.072780, 0.074076] | -0.2% |
| flat-arithmetic | parse+map | 12.409 [12.042, 12.811] | 11.872 [11.766, 11.981] | -4.3% |
| large-match | parse-only | 1,535.407 [1,520.091, 1,553.016] | 1,585.500 [1,569.400, 1,603.400] | +3.3% |
| large-match | map-only | 0.127957 [0.127309, 0.128638] | 0.145380 [0.140320, 0.150990] | +13.6% |
| large-match | parse+map | 1,516.278 [1,503.545, 1,531.531] | 1,619.100 [1,600.500, 1,640.100] | +6.8% |

## 再現

```sh
cargo +1.85.0 bench --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs --features benchmarks --bench parser -- --noplot
```

このfollow-upではJavaを再測定していない。Javaとの比較値は比較元文書のままであり、
Rustの新runと直接混ぜて新しい言語間倍率を算出しない。
