# expected 名の intern 化実験（2026-09-21、Rust、memo 破棄コスト）

## 結論

unlaxer-parser#251。#245 の後も complex-x64 の parse 224 ms のうち parse 終了時の失敗 memo 破棄が約 44 ms。expected の名前を `ParseContext` ごとの
interner で `u32` にし、失敗 memo と global expected を `Vec<u32>` で持つ（実装は codex に委譲、レビュー済み）。`ParseError.expected` は不変。

**採用。** base fixture の Criterion 3-run 中央値は complex.tiny **-21.66%**（3.091 → 2.421 ms）、comparison-heavy.tiny **-15.91%**（0.890 → 0.749 ms）。
x64 は complex -26.7%（倍率 79 → 70）、comparison-heavy -24.6%（94 → 84）、parser 単体 81 → 71 倍。

## 着手前の計測（codex、release、SafeFailures、x1 / x64、`3c64061` → 変更後）

| 入力 | 区間 | 前 | 後 |
|---|---|---:|---:|
| x1 | parse 本体 | 2.616 ms | 2.159 ms |
| x1 | context + 結果の drop | 0.340 ms | 0.159 ms |
| x64 | parse 本体 | 168.9 ms | 137.2 ms |
| x64 | context + 結果の drop | 54.1 ms | 30.1 ms |

## 測定条件

- unlaxer-parser baseline: `3c64061`（master、path patch）、candidate: `c90556d`（PR [#254](https://github.com/opaopa6969/unlaxer-parser/pull/254)、path patch）
- tinyexpression: `514858f`。Criterion 既定。base fixture は 3 run、x4 / x16 / x64 と `parse-only-safe` は各 1 run

## Timing（Rust、ms/op）

base fixture（3-run 中央値）:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust | complex | 2.911 / 3.091 / 3.130 | **3.091** | 2.421 / 2.557 / 2.362 | **2.421** | **-21.66%** |
| Rust | comparison-heavy | 0.876 / 0.890 / 0.966 | **0.890** | 0.736 / 0.749 / 0.761 | **0.749** | **-15.91%** |

public facade（x1 は run1、x4 以上は 1 run）:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 2.911（1.0x） | 2.421（1.0x） | -16.8% |
| complex-x4 | 3.9x | 15.038（5.2x） | 9.370（3.9x） | -37.7% |
| complex-x16 | 15.6x | 54.521（18.7x） | 44.638（18.4x） | -18.1% |
| complex-x64 | 63.0x | 229.409（78.8x） | 168.236（69.5x） | -26.7% |
| comparison-heavy | 1.0x | 0.876（1.0x） | 0.736（1.0x） | -16.0% |
| comparison-heavy-x4 | 4.0x | 3.564（4.1x） | 2.907（3.9x） | -18.4% |
| comparison-heavy-x16 | 16.0x | 18.292（20.9x） | 13.322（18.1x） | -27.2% |
| comparison-heavy-x64 | 64.0x | 82.326（93.9x） | 62.095（84.3x） | -24.6% |

parse-only-safe（parser 単体、1 run）:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 3.012（1.0x） | 2.452（1.0x） | -18.6% |
| complex-x4 | 3.9x | 14.007（4.7x） | 10.117（4.1x） | -27.8% |
| complex-x16 | 15.6x | 58.395（19.4x） | 42.307（17.3x） | -27.6% |
| complex-x64 | 63.0x | 244.341（81.1x） | 173.604（70.8x） | -28.9% |

## 正確性

- unlaxer-parser: `cargo fmt` / `clippy -D warnings` / `test --workspace --locked --offline`（150 tests、intern の同一 id・順序・一括 replay・allocation 0 の 4 test を追加）、`unlaxer-alloc-audit` 契約維持（登録済み名の再記録 1024 回で allocation 0）、Java `RustNativeEmitterTest`
- CST / 失敗診断 / JSON / Display のバイト一致（codex 計測）。公開 API 不変

## 生データ

`raw/2026-09-21-intern-expected/`: Criterion `rust-{baseline,candidate}-run{1,2,3}/`、`rust-scaled-{baseline,candidate}/`、`rust-parseonly-{baseline,candidate}/`
