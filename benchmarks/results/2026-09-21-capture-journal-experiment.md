# capture の mutation journal 実験（2026-09-21、Rust、入力サイズに対する超線形の修正）

## 結論

unlaxer-parser#241。`Expr::Capture` の copy-on-write（checkpoint が `Rc` を掴んでいる間の capture 追加で capture map 全体を deep copy）を、
#213 と同じ mutation journal（`CaptureStore`）に置き換えた。

**採用。** base fixture の Criterion 3-run 中央値は complex.tiny **-19.58%**（3.862 → 3.106 ms）、comparison-heavy.tiny **-12.18%**（1.005 → 0.883 ms）。
x64 では -28.1% / -21.3%。ただし入力 63〜64 倍に対する倍率は complex 172 → 156 倍、comparison-heavy 193 → 162 倍で、まだ超線形の要因が残る
（unlaxer-parser #245 で切り分け）。

## 着手前（[2026-09-21 input size scaling](2026-09-21-input-size-scaling.md)）

Rust public facade は入力 63〜64 倍に対して complex 172 倍、comparison-heavy 193 倍。

## 測定条件

- unlaxer-parser baseline: `b21a965`（tinyexpression の pin）、candidate: PR [#246](https://github.com/opaopa6969/unlaxer-parser/pull/246)（merge `fcfd7c5`）。candidate は worktree の path patch
- tinyexpression: `c7400b4` + #159 の fixture。Criterion 既定。base fixture は 3 run、x4 / x16 / x64 は candidate 1 run（baseline は scaling 計測の値）

## Timing（Rust public facade、ms/op）

base fixture（3-run 中央値）:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust | complex | 3.710 / 3.862 / 4.101 | **3.862** | 3.013 / 3.122 / 3.106 | **3.106** | **-19.58%** |
| Rust | comparison-heavy | 1.005 / 1.010 / 0.994 | **1.005** | 0.932 / 0.883 / 0.881 | **0.883** | **-12.18%** |

x4 / x16 / x64（candidate 1 run、baseline は [scaling 計測](2026-09-21-input-size-scaling.md) の値）:

| Fixture | bytes | サイズ倍率 | baseline ms/op（倍率） | candidate ms/op（倍率） | 変化 |
|---|---:|---:|---:|---:|---:|
| complex | 332 | 1.0x | 3.794（1.0x） | 3.013（1.0x） | -20.6% |
| complex-x4 | 1,293 | 3.9x | 18.829（5.0x） | 14.653（4.9x） | -22.2% |
| complex-x16 | 5,179 | 15.6x | 110.569（29.1x） | 77.340（25.7x） | -30.1% |
| complex-x64 | 20,923 | 63.0x | 654.400（172.5x） | 470.704（156.2x） | -28.1% |
| comparison-heavy | 179 | 1.0x | 0.991（1.0x） | 0.932（1.0x） | -6.0% |
| comparison-heavy-x4 | 716 | 4.0x | 4.030（4.1x） | 3.720（4.0x） | -7.7% |
| comparison-heavy-x16 | 2,864 | 16.0x | 21.968（22.2x） | 19.022（20.4x） | -13.4% |
| comparison-heavy-x64 | 11,456 | 64.0x | 191.505（193.2x） | 150.774（161.8x） | -21.3% |

## 正確性

- unlaxer-parser: `cargo fmt` / `clippy -D warnings` / `test --workspace --locked --offline`（143 tests、rollback / nested / 順序 / LongestChoice の 4 test を追加）、`unlaxer-alloc-audit` 契約維持、Java `RustNativeEmitterTest`
- 公開 API 不変

## 生データ

`raw/2026-09-21-capture-journal/`: Criterion `rust-{baseline,candidate}-run{1,2,3}/`（base fixture）、`rust-scaled-candidate/`
