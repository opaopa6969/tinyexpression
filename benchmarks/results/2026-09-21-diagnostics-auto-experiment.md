# 診断方針 `Auto` を既定にする（2026-09-21、設計問答 提案2 の最小案、unlaxer-parser #261）

## 結論

`ParseOptions` の既定診断方針を `Detailed` から `Auto` に変えた（Rust PR #265、Java PR [#266](https://github.com/opaopa6969/unlaxer-parser/pull/266)）。Auto は準備時に 1 回解決する:
生成 entry point（失敗時に `Detailed` で再解析できる）で、未宣言の custom / 手書き parser を含まない grammar なら `DetailedOnFailure`、
含むなら `Detailed`、低水準 API（`ParseContext` 直接使用）では常に `Detailed`。custom parser は Rust `Expr::CustomWith`、Java `DiagnosticsAgnostic`
で性質を宣言する。実装は codex に委譲（Rust / Java 並行）、レビュー済み。

- Rust: tinyexpression の生成 grammar は custom を含まないので既定で `DetailedOnFailure` に解決され、facade が **-33.8% / -23.5%**、x64 -37% / -31%
- Java: tinyexpression の生成 entry は marker 未宣言の手書き `StringLiteralParser` を含み、facade は低水準 `new ParseContext(...)` を使うため、
  どちらも `DETAILED` に解決される。**既定変更だけでは tinyexpression の Java は速くならない**（tinyexpression #168 で marker 付与と経路変更を行う）。
  設計どおり、低水準 API 利用者の観測結果は変わらない

## Rust（Criterion、baseline = master `5f70015`（既定 Detailed）、candidate = `2ca4148`（既定 Auto）、path patch）

public facade 3-run 中央値:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust | complex | 2.152 / 2.155 / 2.164 | **2.155** | 1.400 / 1.426 / 1.569 | **1.426** | **-33.83%** |
| Rust | comparison-heavy | 0.622 / 0.618 / 0.615 | **0.618** | 0.473 / 0.473 / 0.471 | **0.473** | **-23.49%** |

public facade（x1 は run1、x4 以上は 1 run）:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 2.152（1.0x） | 1.400（1.0x） | -34.9% |
| complex-x4 | 3.9x | 8.481（3.9x） | 5.326（3.8x） | -37.2% |
| complex-x16 | 15.6x | 35.367（16.4x） | 22.337（16.0x） | -36.8% |
| complex-x64 | 63.0x | 146.879（68.3x） | 92.642（66.2x） | -36.9% |
| comparison-heavy | 1.0x | 0.622（1.0x） | 0.473（1.0x） | -23.9% |
| comparison-heavy-x4 | 4.0x | 2.434（3.9x） | 1.643（3.5x） | -32.5% |
| comparison-heavy-x16 | 16.0x | 9.536（15.3x） | 6.759（14.3x） | -29.1% |
| comparison-heavy-x64 | 64.0x | 45.487（73.1x） | 31.257（66.0x） | -31.3% |

parse-only-safe（parser 単体、1 run）:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 2.138（1.0x） | 1.313（1.0x） | -38.6% |
| complex-x4 | 3.9x | 8.416（3.9x） | 5.263（4.0x） | -37.5% |
| complex-x16 | 15.6x | 34.683（16.2x） | 21.973（16.7x） | -36.6% |
| complex-x64 | 63.0x | 143.510（67.1x） | 90.814（69.2x） | -36.7% |

失敗入力（complex-half / complex-tail、facade、codex の CPU 時間計測 155 回中央値）: 4.825 → 8.313 ms（+72%）、2.549 → 4.307 ms（+69%）。
CST / AST / `ParseError` / `ParseDiagnostic` は前後で一致。

## Java（JMH、baseline = master `5f70015`、candidate = `83ac362`、isolated Maven repo）

public facade 3-run 中央値（facade は低水準 API → `DETAILED` に解決、変化なしが期待値）:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 40.675 / 42.814 / 40.761 | **40.761** | 41.862 / 43.481 / 41.988 | **41.988** | **+3.01%** |
| Java | comparison-heavy | 26.293 / 25.063 / 26.366 | **26.293** | 25.093 / 24.825 / 26.613 | **25.093** | **-4.56%** |

生成 entry で既定（Auto）と明示 `DETAILED`（JMH 2 fork × 5 iteration）:

| Fixture | entryDetailed ms | entryAuto ms（既定） | 差 |
|---|---:|---:|---:|
| complex-x64 | 2440.9 | 2342.6 | -4.0% |
| complex-half | 15.1 | 14.8 | -2.0% |

tinyexpression の生成 entry は未宣言の手書き `StringLiteralParser` を含むため Auto は `DETAILED` に解決され、一致する。

## 正確性

- Rust: `cargo fmt` / `clippy -D warnings` / `test --workspace --locked --offline`（164 tests）、`unlaxer-alloc-audit`（Auto と解決先の allocation プロファイル一致）、Java `RustNativeEmitterTest`、tinyexpression-rs 32
- Java: 683 + 970 tests（`AutoDiagnosticsTest` 4 件、`JavaDetailedOnFailureRuntimeTest` 3 件を追加、golden 4 件再生成）、p4-smoke 85
- 公開 API は additive（Rust `Diagnostics::Auto` / `Expr::CustomWith` / `grammar_allows_deferred_diagnostics`、Java `Diagnostics.AUTO` / `resolveDiagnostics` / `DiagnosticsAgnostic` / `DiagnosticsSafety`）

## 生データ

`raw/2026-09-21-diagnostics-auto/`: Rust `rust-{baseline,candidate}-run{1,2,3}/`、`rust-scaled-*/`、`rust-parseonly-*/`。Java `java-{baseline,candidate}-run{1,2,3}.json`、`java-entry-modes.json`
