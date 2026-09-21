# 入力サイズに対するスケーリング計測（2026-09-21）

## 結論

**線形ではない。** complex.tiny と comparison-heavy.tiny をそれぞれ 4 / 16 / 64 倍に伸ばした fixture（ネストの深さは同じ、長さだけ伸ばす）で
public facade を測ると、入力 63〜64 倍に対して Java は 86 倍 / 122 倍、Rust は 172 倍 / 193 倍の時間がかかった。x16 → x64 の区間は
Java が n^1.2〜1.3、Rust が n^1.3〜1.6 で、Rust は 2 乗に近づいている。21 KB の入力で Java 3.9 秒、Rust 0.65 秒。

Rust 側の主因はコードから特定できた: `Expr::Capture` が `captures_mut_map()`（`Rc::make_mut`）で全 capture の `HashMap<String, Vec<Span>>` を
deep copy する。`Sequence` の各要素が checkpoint を開いて captures の `Rc` を掴むため、capture 追加のほぼ毎回「それまでの全 capture 数」に
比例するコピーが起きる（unlaxer-parser #241）。Java 側の主因は未特定（unlaxer-parser #242 で追跡）。

## Fixture

- `complex-x{4,16,64}.tiny`: `var` 宣言 2N 個、N 個の `if … else match …` ブロックを `+` で繋いだ 1 つの式、N 個の `float bonusN` メソッド宣言
- `comparison-heavy-x{4,16,64}.tiny`: comparison-heavy.tiny の比較チェーンを `&` で N 回連結した 1 行の式
- いずれも Rust CLI `tinyexpression-rs parse` で `ok: true`。JMH `FacadeState` と Criterion `FACADE_FIXTURES` に登録した（既定の実行時間が伸びるので、
  A/B では従来どおり `-p fixture=…` / Criterion のフィルタで絞る）

## 測定条件

- unlaxer-parser `b21a965`（tinyexpression の pin）、tinyexpression `c7400b4`。Java は isolated Maven repo（同 rev の Java runtime）
- JMH: 既定（2 fork、warmup 5 × 1 s、measurement 8 × 1 s）、`P4ParserBenchmark.publicFacade`。Criterion: 既定（warmup 5 s、measurement 10 s、100 samples）
- 1 セッションずつ（倍率の比較が目的で、絶対値の精度は 3-run A/B より低い）

## 結果（public facade、ms/op）

| Fixture | bytes | 倍率 | Java ms/op | Java 倍率 | Java KB/s | Rust ms/op | Rust 倍率 | Rust KB/s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| complex | 332 | 1.0x | 44.7 ± 6.7 | 1.0x | 7 | 3.794 | 1.0x | 85 |
| complex-x4 | 1,293 | 3.9x | 180.0 ± 21.0 | 4.0x | 7 | 18.829 | 5.0x | 67 |
| complex-x16 | 5,179 | 15.6x | 788.1 ± 70.8 | 17.6x | 6 | 110.569 | 29.1x | 46 |
| complex-x64 | 20,923 | 63.0x | 3859.3 ± 333.0 | 86.3x | 5 | 654.400 | 172.5x | 31 |
| comparison-heavy | 179 | 1.0x | 26.4 ± 2.0 | 1.0x | 7 | 0.991 | 1.0x | 176 |
| comparison-heavy-x4 | 716 | 4.0x | 117.9 ± 11.5 | 4.5x | 6 | 4.030 | 4.1x | 174 |
| comparison-heavy-x16 | 2,864 | 16.0x | 559.7 ± 168.6 | 21.2x | 5 | 21.968 | 22.2x | 127 |
| comparison-heavy-x64 | 11,456 | 64.0x | 3230.3 ± 268.3 | 122.5x | 3 | 191.505 | 193.2x | 58 |

KB/s は入力バイト数 ÷ 時間。線形なら一定になるはずで、両 runtime とも入力が大きいほど下がっている。

## 追記: parser 単体と mapper 単体の分離（complex 系、unlaxer-parser `b21a965`）

`P4ParserBenchmark.parseOnlySafe` / `mapOnly`（JMH、1 fork、warmup 3 × 1 s、measurement 5 × 1 s）と Criterion `parse-only-safe` / `map-only`。
comparison-heavy 系は alternate entry が必要で parse-only 系では使えない。

| Fixture | サイズ倍率 | Java parseOnlySafe ms（倍率） | Java mapOnly ms（倍率） | Rust parse-only-safe ms（倍率） | Rust map-only ms（倍率） |
|---|---:|---:|---:|---:|---:|
| complex | 1.0x | 34.981（1.0x） | 0.138（1.0x） | 3.889（1.0x） | 0.018（1.0x） |
| complex-x4 | 3.9x | 153.038（4.4x） | 0.740（5.4x） | 21.210（5.5x） | 0.069（3.9x） |
| complex-x16 | 15.6x | 694.896（19.9x） | 3.276（23.7x） | 161.102（41.4x） | 0.466（26.0x） |
| complex-x64 | 63.0x | 2465.029（70.5x） | 38.412（278.0x） | 655.533（168.6x） | 1.252（69.7x） |

- **Java は parser がほぼ線形（63 倍で 70.5 倍）、mapper が強く超線形（278 倍）**。facade の超線形は生成 mapper と候補選択（`P4PreferredAstMapper.mapCandidates` が候補ごとに再 mapping と `SourceMappedAst` の全 span コピー）にある → unlaxer-parser #242
- **Rust は mapper が線形（69.7 倍）、parser が超線形（168.6 倍）** → runtime 側（#241 の capture COW を直しても 156 倍。残りは #245）

## 生データ

`raw/2026-09-21-input-size-scaling/`: `java-publicFacade.json`、`java-jmh-summary.txt`、`rust/<fixture>-estimates.json`、`rust-criterion.log`。
`raw/2026-09-21-input-size-scaling-split/`: `java-split.json`（parseOnlySafe / mapOnly）、`rust/{parse-only-safe,map-only}/<fixture>-estimates.json`
