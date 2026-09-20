# Java memo diagnostic frame の innermost 更新と pop 時 merge 実験（2026-09-21）

## 結論

unlaxer-parser#223。#220 後も Java CPU の約 40%（complex.tiny）を占めていた「全 open memo frame の更新」を、Rust が #220 で
採った「innermost frame だけ更新し pop 時に親へ max-merge」に置き換えた。Rust runtime は変更なし。

Java public facade の 3-run 中央値は complex.tiny **-26.66%**、comparison-heavy.tiny **-16.56%** 短縮した。診断出力・memo 保存内容・
AST・source span の test は変更なしで通るため**採用**した。#207 時点からの累積は Java complex 321 → 75 ms/op（-77%）、
comparison-heavy 135 → 40 ms/op（-71%）。Rust runtime は変更が無いため Rust の timing と pin（`4658223`）は #220 のまま。

## 実装（Java）

- 失敗・progress・trial・memo replay は global と `memoDiagnosticFrames.peekFirst()` だけを更新
- `discardMemoDiagnosticFrame` が popped frame を親へ merge（max offset、同点なら深い stack、出所集合、trials）
- frame の stack は absolute snapshot を共有し、rule-local suffix は replay 時に `stackBaseDepth` から subList で取得
- 等価性: max-union は結合的・可換、子 frame 内の事象は親から見て連続区間。成功 pop / 失敗 pop を含む 3 層ネストの test で
  memo hit の replay が初回と同じ診断を返すことを固定

## Profile（baseline = #220 適用後 `6ac93e5`）

Java CPU（JFR ExecutionSample）:

| Fixture | 診断 | commit の token 収集・listener | transaction bookkeeping | dispatch 等 |
|---|---:|---:|---:|---:|
| complex.tiny before（122 samples） | 56.6% | 20.5% | 9.0% | 13.9% |
| complex.tiny after（51 samples） | 35.3% | 29.4% | 9.8% | 25.5% |
| comparison-heavy.tiny before（63 samples） | 28.6% | 23.8% | 9.5% | 38.1% |
| comparison-heavy.tiny after（52 samples） | 21.2% | 25.0% | 7.7% | 46.2% |

allocation（JMH `gc.alloc.rate.norm`）: complex 181,351,559 → 165,038,565 B/op、comparison-heavy 91,726,397 → 86,272,082 B/op。

## 測定条件

- unlaxer-parser baseline: `6ac93e5`（#222 merge commit）、candidate: `88c0ec8`（PR #224）
- tinyexpression: `577d6ed`（#150 merge commit）。Rust pin は `4658223`（#220）のまま。Java は isolated Maven repo で baseline/candidate を切り替え。Rust runtime は変更なしのため Rust timing は取らない
- fixture / CPU / OS / JMH 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 102.182 / 103.553 / 99.720 | **102.182** | 73.519 / 74.945 / 78.742 | **74.945** | **-26.66%** |
| Java | comparison-heavy | 47.588 / 46.877 / 48.770 | **47.588** | 40.684 / 39.708 / 39.473 | **39.708** | **-16.56%** |

単位は ms/op、各 run の JMH score。baseline の中央値（102.2 ms）は #220 実験の candidate 中央値（97.6 ms）より遅いが、
同一セッション内の A/B なので差分で読む。

## 正確性

- unlaxer-parser: Java 672 + 987 tests（ネスト frame の merge test を追加）
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 生データ

`raw/2026-09-21-innermost-frame-merge/`: JMH 結果 `java-{baseline,candidate}-run{1,2,3}.json`、`java-gc-alloc-*`、`java-jfr-cpu-*`、`aggregate-jfr-cpu.py`
