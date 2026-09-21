# 失敗 memo の position バケット化実験（2026-09-21、Rust、入力サイズに対する超線形の修正）

## 結論

unlaxer-parser#245。#241 の後も Rust parser 単体は 63 倍入力で 168 倍だった。回数カウンタは全て線形で、時間の超線形は失敗 memo 表
（x64 で 345,583 エントリ・約 53 MiB）のキャッシュ局所性と parse 終了時の破棄（541 倍）にあった。memo を position 256 単位のバケットに分割し、
非 ASCII 入力の byte → code point 変換を逆引き表で O(1) にした（実装は codex に委譲、レビュー済み）。

**採用。** base fixture の Criterion 3-run 中央値は complex.tiny **-4.01%**（3.072 → 2.949 ms）、comparison-heavy.tiny **-6.94%**（0.943 → 0.878 ms）。
x64 では complex **-48.9%**（435.7 → 222.7 ms、倍率 142 → 75）、comparison-heavy **-45.3%**（141.6 → 77.5 ms、倍率 150 → 89）、parser 単体は 145 → 77 倍。
残る 1.2〜1.4 倍は parse 終了時の memo 破棄が主（unlaxer-parser #245 に記録、arena 化が次の候補）。

## 着手前の計測（codex、master `fcfd7c5`、complex x1 / x4 / x16 / x64、`parse_tree_detailed_with_options(SafeFailures)`）

| カウンタ | x64 / x1 |
|---|---:|
| 入力バイト数 | 63.0 |
| rule 呼び出し / checkpoint / diagnostic record / memo insert / CST node / capture push / scope journal | 59.7〜63.6 |
| `code_point` 二分探索比較 | 97.8 |
| `LongestChoice` / `Lookahead` | 0 回 |

| 区間（時間） | x1 | x64 | 倍率 |
|---|---:|---:|---:|
| 解析本体 | 2.612 ms | 223.0 ms | 85.4 |
| memo 破棄 | 0.345 ms | 186.7 ms | **541.5** |

## 測定条件

- unlaxer-parser baseline: `fcfd7c5`（#246 merge、path patch）、candidate: `2598cad`（PR [#248](https://github.com/opaopa6969/unlaxer-parser/pull/248)、path patch）
- tinyexpression: `1f8842b`。Criterion 既定。base fixture は 3 run、x4 / x16 / x64 と `parse-only-safe` は各 1 run

## Timing（Rust、ms/op）

base fixture（3-run 中央値）:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust | complex | 3.077 / 2.977 / 3.072 | **3.072** | 2.949 / 3.011 / 2.892 | **2.949** | **-4.01%** |
| Rust | comparison-heavy | 0.943 / 0.948 / 0.913 | **0.943** | 0.872 / 0.878 / 0.893 | **0.878** | **-6.94%** |

public facade（x1 は run1、x4 以上は 1 run）:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 3.077（1.0x） | 2.949（1.0x） | -4.2% |
| complex-x4 | 3.9x | 15.755（5.1x） | 12.607（4.3x） | -20.0% |
| complex-x16 | 15.6x | 81.427（26.5x） | 52.414（17.8x） | -35.6% |
| complex-x64 | 63.0x | 435.675（141.6x） | 222.657（75.5x） | -48.9% |
| comparison-heavy | 1.0x | 0.943（1.0x） | 0.872（1.0x） | -7.5% |
| comparison-heavy-x4 | 4.0x | 3.531（3.7x） | 3.351（3.8x） | -5.1% |
| comparison-heavy-x16 | 16.0x | 18.083（19.2x） | 15.413（17.7x） | -14.8% |
| comparison-heavy-x64 | 64.0x | 141.636（150.1x） | 77.469（88.8x） | -45.3% |

parse-only-safe（parser 単体、1 run）:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 3.056（1.0x） | 2.821（1.0x） | -7.7% |
| complex-x4 | 3.9x | 13.177（4.3x） | 12.512（4.4x） | -5.1% |
| complex-x16 | 15.6x | 82.669（27.1x） | 51.782（18.4x） | -37.4% |
| complex-x64 | 63.0x | 443.931（145.3x） | 218.026（77.3x） | -50.9% |

## 正確性

- unlaxer-parser: `cargo fmt` / `clippy -D warnings` / `test --workspace --locked --offline`（147 tests、バケット境界・非 ASCII span・境界外拒否の 4 test を追加）、`unlaxer-alloc-audit` 契約維持、Java `RustNativeEmitterTest`
- CST / 失敗診断 / `CheckpointMetrics` の fingerprint 一致（codex 計測）。公開 API 不変

## 生データ

`raw/2026-09-21-memo-buckets/`: Criterion `rust-{baseline,candidate}-run{1,2,3}/`、`rust-scaled-{baseline,candidate}/`、`rust-parseonly-{baseline,candidate}/`
