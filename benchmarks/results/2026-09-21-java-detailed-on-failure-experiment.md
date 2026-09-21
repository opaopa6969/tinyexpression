# Java 版 `DETAILED_ON_FAILURE` 診断モードの実験（2026-09-21、Java、opt-in）

## 結論

unlaxer-parser#259（設計問答の提案1 の Java 版）。`ParseOptions.withDiagnostics(Diagnostics.DETAILED_ON_FAILURE)` で初回 parse の失敗診断
（frontier 追跡、失敗候補の登録、memo diagnostic frame、hit 時 replay）を止め、生成 parser の entry point が失敗時だけ `DETAILED` で再解析する。
既定は `DETAILED` のまま。実装は codex に委譲、レビュー済み。

**採用（opt-in）。** parser 単体（`parseOnlySafe` 相当）で complex **-17.1%**（34.3 → 28.4 ms）、complex-x64 **-24.5%**（2,585 → 1,951 ms）。
生成 entry point（parse + mapping）では -7.4%、失敗入力は再解析で +58.6% / +79.4%。既定 `DETAILED` の退行なし（-3.9% / -2.7%）。

## 着手前の差分計測（計測専用 build、`parseOnlySafe`、1 fork × 5 iteration）

| Fixture | master `516b809` | 診断停止 build | 短縮 |
|---|---:|---:|---:|
| complex | 40.7 ms | 27.9 ms | 31.6% |
| complex-x64 | 2,800.5 ms | 1,997.8 ms | 28.7% |

## モード比較

同じ候補 runtime、JMH 1 fork × 5 iteration、`SAFE_FAILURES`:

| 経路 | Fixture | DETAILED ms | DETAILED_ON_FAILURE ms | 変化 |
|---|---|---:|---:|---:|
| parser 単体（成功） | complex | 34.3 | 28.4 | -17.1% |
| parser 単体（成功） | complex-x64 | 2585.0 | 1951.4 | -24.5% |
| entry（成功） | complex | 36.5 | 33.8 | -7.4% |
| entry（失敗: 前半で切断） | complex-half | 16.5 | 26.1 | +58.6% |
| entry（失敗: 末尾 `@`） | complex-tail | 36.6 | 65.6 | +79.4% |

## 既定モードの退行確認

baseline = master `516b809`（`m2-master`）、candidate = `20238bf`、既定 `DETAILED`、public facade 3-run 中央値:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 42.983 / 42.850 / 42.344 | **42.850** | 40.970 / 41.181 / 45.126 | **41.181** | **-3.90%** |
| Java | comparison-heavy | 25.625 / 25.556 / 25.876 | **25.625** | 24.842 / 25.460 / 24.946 | **24.946** | **-2.65%** |

## 測定条件

- unlaxer-parser baseline: `516b809`（isolated Maven repo）、candidate: `20238bf`（PR [#260](https://github.com/opaopa6969/unlaxer-parser/pull/260)）
- tinyexpression: `99e8ce3`（fixture）

## 正確性

- unlaxer-parser: Java 679 + 990 tests（`DetailedOnFailureTest` 7 件、`JavaDetailedOnFailureRuntimeTest` 3 件を追加、golden 2 件再生成）、`RustNativeEmitterTest`（Rust 生成文字列不変）
- TinyExpression: p4-smoke 85（candidate repo）。4 入力で Token 木・cursor・memo hit 数が一致、失敗入力は再解析後の診断が `DETAILED` と一致（codex）
- 公開 API は additive。低水準 API は再解析しない（Javadoc / `docs/java-diagnostics-policy.md`）

## 生データ

`raw/2026-09-21-java-detailed-on-failure/`: `java-modes.json`（parseOnlySafe / parseOnlyDeferred / entryDetailed / entryDeferred）、`java-{baseline,candidate}-run{1,2,3}.json`、`java-parseonly-{master,jdiag}.json`（診断停止 build の差分計測）
