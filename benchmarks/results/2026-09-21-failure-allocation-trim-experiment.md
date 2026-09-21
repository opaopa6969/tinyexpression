# 失敗診断まわりの割当削減実験（2026-09-21、Rust）

## 結論

unlaxer-parser#255。master `ab6a368` の差分計測で失敗診断の記録を止めると x1 38.7% / x64 44.9% 短縮、memo が保持する `Rc<Vec<u32>>` の 75% が
要素 1 個。(1) 単一 expected の inline 化、(2) `CaptureStore` の capture 名 `to_owned()` 排除、(3) memo hit 前の `Arc::clone(&rules)` 排除の 3 点を
入れた（実装は codex に委譲、項目ごとに 1 commit、レビュー済み）。

**採用。** base fixture の Criterion 3-run 中央値は complex.tiny **-9.02%**（2.492 → 2.267 ms）、comparison-heavy.tiny **-19.00%**（0.774 → 0.627 ms）。
x64 は complex -13.1%（倍率 70 → 66）、comparison-heavy -32.0%（78 → 72）、parser 単体 70 → 64 倍。効果はほぼ項目 1（単一 expected の inline 化）。

## 着手前の差分計測（codex、master `ab6a368`、complex x1 / x64、CPU 時間の中央値）

| 省いた対象 | x1 短縮 | x64 短縮 |
|---|---:|---:|
| 失敗診断の記録 | 38.7% | 44.9% |
| CST / capture 構築 | 15.0% | 10.4% |
| checkpoint の payload | 7.4% | 0.4% |
| trivia skip | 2.8% | 0.3% |
| 全部省いた残存 | 26.8% | 25.0% |

## 項目ごとの簡易計測（codex）

| 段階（codex 簡易計測、CPU 時間中央値） | x1 | x64 |
|---|---:|---:|
| base `ab6a368` | 2.500 ms | 170.0 ms |
| +1 単一 expected の inline 化 | 2.468（-1.3%） | 149.0（**-12.4%**） |
| +2 capture 名の `to_owned()` 排除 | 2.500（+1.3%） | 145.0（-2.7%） |
| +3 memo hit 前の `Arc::clone` 排除 | 2.500（±0） | 149.0（+2.8%、ばらつき内） |

## 測定条件

- unlaxer-parser baseline: `ab6a368`（path patch）、candidate: `60ed3de`（PR [#256](https://github.com/opaopa6969/unlaxer-parser/pull/256)、path patch）
- tinyexpression: `f985672`。Criterion 既定。base fixture は 3 run、x4 / x16 / x64 と `parse-only-safe` は各 1 run

## Timing（Rust、ms/op）

base fixture（3-run 中央値）:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust | complex | 2.454 / 2.495 / 2.492 | **2.492** | 2.252 / 2.267 / 2.330 | **2.267** | **-9.02%** |
| Rust | comparison-heavy | 0.856 / 0.746 / 0.774 | **0.774** | 0.626 / 0.627 / 0.629 | **0.627** | **-19.00%** |

public facade（x1 は run1、x4 以上は 1 run）:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 2.454（1.0x） | 2.252（1.0x） | -8.3% |
| complex-x4 | 3.9x | 9.283（3.8x） | 9.054（4.0x） | -2.5% |
| complex-x16 | 15.6x | 43.358（17.7x） | 38.426（17.1x） | -11.4% |
| complex-x64 | 63.0x | 170.736（69.6x） | 148.416（65.9x） | -13.1% |
| comparison-heavy | 1.0x | 0.856（1.0x） | 0.626（1.0x） | -26.9% |
| comparison-heavy-x4 | 4.0x | 2.861（3.3x） | 2.341（3.7x） | -18.2% |
| comparison-heavy-x16 | 16.0x | 11.979（14.0x） | 10.268（16.4x） | -14.3% |
| comparison-heavy-x64 | 64.0x | 66.312（77.5x） | 45.096（72.0x） | -32.0% |

parse-only-safe（parser 単体、1 run）:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 2.457（1.0x） | 2.343（1.0x） | -4.6% |
| complex-x4 | 3.9x | 9.204（3.7x） | 8.597（3.7x） | -6.6% |
| complex-x16 | 15.6x | 41.107（16.7x） | 36.149（15.4x） | -12.1% |
| complex-x64 | 63.0x | 172.424（70.2x） | 150.355（64.2x） | -12.8% |

## 正確性

- unlaxer-parser: `cargo fmt` / `clippy -D warnings` / `test --workspace --locked --offline`（154 tests、単一→複数の昇格・replay 共有・rollback 後の capture 検索・rule 診断順序の 4 test を追加）、`unlaxer-alloc-audit`（診断 11 → 9、capture 1,034 → 522、intern 済み名 0、checkpoint 0 / 2）、Java `RustNativeEmitterTest`
- CST / 失敗診断の SHA-256 が 4 段階すべてで一致（codex）。公開 API 不変

## 生データ

`raw/2026-09-21-failure-allocation-trim/`: Criterion `rust-{baseline,candidate}-run{1,2,3}/`、`rust-scaled-{baseline,candidate}/`、`rust-parseonly-{baseline,candidate}/`
