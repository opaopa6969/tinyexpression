# 詳細診断を失敗時に作るモード `DetailedOnFailure` の実験（2026-09-21、Rust、opt-in）

## 結論

unlaxer-parser#257（設計問答の提案1）。`ParseOptions::with_diagnostics(Diagnostics::DetailedOnFailure)` で初回 parse の失敗診断記録を止め、
失敗時だけ `Detailed` で再解析する。成功入力で **-48%（x1）〜 -35%（x64）**、失敗入力で +46〜+73%（2 回 parse）。既定は `Detailed` のまま、
opt-in として採用。実装は codex に委譲、レビュー済み。

## モード比較（同じ候補 runtime `fc03b21`、Criterion、`parser::parse_tree_detailed_with_options(SafeFailures)`、warm-up 3 s / measurement 6 s / 50 samples）

| Fixture | 入力 | Detailed ms | DetailedOnFailure ms | 変化 |
|---|---|---:|---:|---:|
| complex | 成功（そのまま） | 2.875 | 1.411 | -50.9% |
| complex | 失敗（前半で切断） | 4.932 | 8.548 | +73.3% |
| complex | 失敗（末尾に `@`） | 2.472 | 3.979 | +61.0% |
| complex-x4 | 成功（そのまま） | 9.842 | 6.218 | -36.8% |
| complex-x4 | 失敗（前半で切断） | 5.023 | 7.421 | +47.7% |
| complex-x4 | 失敗（末尾に `@`） | 10.196 | 14.790 | +45.1% |
| complex-x16 | 成功（そのまま） | 39.266 | 27.253 | -30.6% |
| complex-x16 | 失敗（前半で切断） | 16.048 | 27.764 | +73.0% |
| complex-x16 | 失敗（末尾に `@`） | 57.304 | 65.773 | +14.8% |
| complex-x64 | 成功（そのまま） | 161.297 | 104.725 | -35.1% |
| complex-x64 | 失敗（前半で切断） | 70.233 | 102.727 | +46.3% |
| complex-x64 | 失敗（末尾に `@`） | 160.116 | 242.633 | +51.5% |

codex の CPU 時間計測（7 プロセス中央値）: 成功 complex -35.1% / x64 -38.5%、comparison-heavy（`BooleanExpression` 入口）-28.3% / x64 -34.9%、
失敗 complex 前半 +75.3%、末尾 `@` +52.1%。全ケースで受理 / 拒否、CST、capture、scope、`ParseError`、`ParseDiagnostic` が一致。

## 既定モードの退行確認

baseline = master `81a960a`、candidate = `fc03b21`、既定 `Detailed`、public facade 3-run 中央値:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Rust | complex | 2.381 / 2.393 / 2.324 | **2.381** | 2.353 / 2.255 / 2.225 | **2.255** | **-5.29%** |
| Rust | comparison-heavy | 0.658 / 0.662 / 0.638 | **0.658** | 0.634 / 0.636 / 0.637 | **0.636** | **-3.28%** |

退行なし（x64 も complex -8.5%、comparison-heavy -3.2%）。

## 測定条件

- unlaxer-parser baseline: `81a960a`（path patch）、candidate: `fc03b21`（PR [#258](https://github.com/opaopa6969/unlaxer-parser/pull/258)、path patch）
- tinyexpression: `514858f`（fixture）。モード比較は一時 crate（`raw/2026-09-21-detailed-on-failure/diag-modes/diag-bench.rs`）

## 正確性

- unlaxer-parser: `cargo fmt` / `clippy -D warnings` / `test --workspace --locked --offline`（160 tests、`diagnostics_policy.rs` 4 test + 内部 2 test を追加）、
  `unlaxer-alloc-audit`（1024 同一失敗 2 → 0、memo 使用の成功 16 → 8）、Java `RustNativeEmitterTest`（生成文字列不変）
- 公開 API は additive（`Diagnostics`、`ParseOptions.diagnostics`、`with_diagnostics`）。低水準 API では再解析しない（README に記載）

## 生データ

`raw/2026-09-21-detailed-on-failure/`: `diag-modes/`（モード比較の Criterion estimates と bench ソース）、`rust-{baseline,candidate}-run{1,2,3}/`、`rust-scaled-*/`、`rust-parseonly-*/`
