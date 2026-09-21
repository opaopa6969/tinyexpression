# 生成 mapper の選択処理を線形にする実験（2026-09-21、Java、入力サイズに対する超線形の修正）

## 結論

unlaxer-parser#242。parser 単体は 63 倍入力で 70.5 倍（ほぼ線形）、mapper 単体は 278 倍で、超線形は生成 mapper と候補選択にある
（[分離計測](2026-09-21-input-size-scaling.md)）。generator を変更し、(1) `findBestMappedToken` の部分木ごとの最良候補をメモ化（`BEST_MEMO`）、
(2) `SourceMappedAst` の全 span コピーを凍結レイヤ（`SpanLayer`）に置き換えるコードを生成する。tinyexpression 側は再生成のみで、
選ばれる token・AST・span は不変。

**採用。** base fixture の JMH 3-run 中央値は complex.tiny **-2.71%**（43.559 → 42.377 ms）、comparison-heavy.tiny **-3.27%**（26.532 → 25.664 ms）。
x64 では facade -11.3% / -6.4%、mapper 単体（`mapOnly`）は x1〜x16 で -30%。選ばれる token・AST・span は不変。
倍率は facade で 86 → 84 倍、122 → 118 倍と大きくは変わらず、残りは (1) parser 自体の 1.12 倍、(2) mapper の x16 → x64 の局所性の崖、
(3) facade が候補型ごとに公開 mapping を呼び直す回数（x64 で約 960 ms、契約上 generator 側では再利用しない）に分解できる（docs ケース20 参照）。

## 着手前（[2026-09-21 input size scaling](2026-09-21-input-size-scaling.md)）

Java public facade は入力 63 倍で 86 倍、64 倍で 122 倍。x64 JFR: `findBestMappedToken` inclusive 79.6%（complex-x64）/ 59.2%（comparison-heavy-x64）、x1 では 17.6%。

## 測定条件

- unlaxer-parser baseline: `b21a965`（Java runtime は #235 相当）、candidate: `682b38f`（PR [#247](https://github.com/opaopa6969/unlaxer-parser/pull/247)）。いずれも isolated Maven repo
- tinyexpression: `c7400b4` + #159 の fixture。JMH 既定。base fixture は 3 run、x4 / x16 / x64 は candidate 1 run（baseline は scaling 計測の値）

## Timing（Java public facade、ms/op）

base fixture（3-run 中央値）:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 42.198 / 44.099 / 43.559 | **43.559** | 40.727 / 42.377 / 42.989 | **42.377** | **-2.71%** |
| Java | comparison-heavy | 26.532 / 25.571 / 27.666 | **26.532** | 26.410 / 25.012 / 25.664 | **25.664** | **-3.27%** |

x4 / x16 / x64（candidate 1 run、baseline は [scaling 計測](2026-09-21-input-size-scaling.md) の値）:

| Fixture | bytes | サイズ倍率 | baseline ms/op（倍率） | candidate ms/op（倍率） | 変化 |
|---|---:|---:|---:|---:|---:|
| complex | 332 | 1.0x | 44.711（1.0x） | 40.814（1.0x） | -8.7% |
| complex-x4 | 1,293 | 3.9x | 179.977（4.0x） | 169.136（4.1x） | -6.0% |
| complex-x16 | 5,179 | 15.6x | 788.081（17.6x） | 700.864（17.2x） | -11.1% |
| complex-x64 | 20,923 | 63.0x | 3859.322（86.3x） | 3424.045（83.9x） | -11.3% |
| comparison-heavy | 179 | 1.0x | 26.369（1.0x） | 25.577（1.0x） | -3.0% |
| comparison-heavy-x4 | 716 | 4.0x | 117.931（4.5x） | 111.024（4.3x） | -5.9% |
| comparison-heavy-x16 | 2,864 | 16.0x | 559.655（21.2x） | 480.079（18.8x） | -14.2% |
| comparison-heavy-x64 | 11,456 | 64.0x | 3230.332（122.5x） | 3022.909（118.2x） | -6.4% |

mapper 単体（`mapOnly`、1 fork、5 iteration）:

| Fixture | mapOnly baseline ms（倍率） | mapOnly candidate ms（倍率） | 変化 |
|---|---:|---:|---:|
| complex | 0.138（1.0x） | 0.097（1.0x） | -29.7% |
| complex-x4 | 0.740（5.4x） | 0.480（4.9x） | -35.1% |
| complex-x16 | 3.276（23.7x） | 2.259（23.3x） | -31.0% |
| complex-x64 | 38.412（278.0x） | 33.875（348.8x） | -11.8% |

## 正確性

- unlaxer-parser: unlaxer-dsl 987 tests（golden snapshot 再生成）
- TinyExpression: p4-smoke 85 tests（candidate repo で mapper を再生成）

## 生データ

`raw/2026-09-21-mapper-linear-selection/`: JMH `java-{baseline,candidate}-run{1,2,3}.json`（base fixture）、`java-scaled-candidate.json`、`java-maponly-scaled-candidate.json`
