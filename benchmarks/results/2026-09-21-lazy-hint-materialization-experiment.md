# ParseContext 失敗診断 hint の遅延展開実験（2026-09-21）

## 結論

unlaxer-parser#220。#219 後も Java CPU の約 66% を占めていた「frontier 失敗ごとの hint 再登録」を、出所（失敗 parser と
innermost TerminalSymbol）の記録だけにし、hint への展開を診断要求時へ送った。Rust は frame への記録を innermost だけにし、
pop 時に親へ merge する形にした。

public facade の 3-run 中央値は Java で complex.tiny **-17.98%**、comparison-heavy.tiny **-12.46%**、Rust で
**-53.22%**、**-31.37%** 短縮した。診断出力・memo 保存内容・AST・source span の test は変更なし（memo frame の expected を
読むテスト 1 本は materialize helper 経由に変更）で通るため**採用**した。#207 時点からの累積は Java complex 321 → 98 ms/op、
Rust complex 27.2 → 5.6 ms/op。

## 実装

- Java: `ExpectedSources`（parser identity set × failed/terminal、first-seen 順）を global と各 memo frame が保持。frontier 失敗は
  identity set 挿入 2 回。memo は出所集合を保存・merge で replay。`getParseFailureDiagnostics()` 等で要求時に cache 済み候補を
  出所順に展開し重複排除（展開は決定的、順序は first-seen、重複排除は順序と可換 → 従来と同じリスト）
- Rust: `fail_at` は innermost frame と top-level だけを更新し、memo 化 rule の frame は pop 時（成功・失敗とも）に親へ
  max-merge。より遠い子は `Rc<Vec>` を共有し親の次回書き込みで copy-on-write（memo 保存内容は不変）。4 層ネストで
  成功 pop と失敗 pop を含む test で memo 保存順と sort 済み `ParseError.expected` を固定

## 着手前 profile（baseline = #219 適用後 `b443676`、JFR ExecutionSample）

| Fixture | 診断 | commit の token 収集・listener | transaction bookkeeping | dispatch 等 |
|---|---:|---:|---:|---:|
| complex.tiny（189 samples） | 66.1% | 13.8% | 11.1% | 9.0% |
| comparison-heavy.tiny（148 samples） | 66.2% | 13.5% | 6.1% | 14.2% |

self frame 上位: `HashMap.putVal` 16% / 23%、`registerFailureCandidate` 11% / 8%。

## 変更後 profile（Java candidate `f9eb905`）

| Fixture | 診断 | commit の token 収集・listener | transaction bookkeeping | dispatch 等 |
|---|---:|---:|---:|---:|
| complex.tiny（122 samples） | 56.6% | 20.5% | 9.0% | 13.9% |
| comparison-heavy.tiny（63 samples） | 28.6% | 23.8% | 9.5% | 38.1% |

self frame 上位: `IdentityHashMap.put` 16%、`ExpectedSources.addAll` 7%（memo replay の merge と全 open frame への出所登録）。

## Allocation（Java、JMH `gc.alloc.rate.norm`、public facade 1 parse）

| Fixture | before | after |
|---|---:|---:|
| complex.tiny | 183,704,103 B | 181,351,559 B |
| comparison-heavy.tiny | 94,147,559 B | 91,726,397 B |

allocation は #219 で既に削減済みで、この施策は CPU が対象。

## 測定条件

- unlaxer-parser baseline: `b443676`（#221 merge commit）、candidate: `4658223`（PR #222）
- tinyexpression: `0212452`（#149 merge commit）。Java は isolated Maven repo、Rust は pin 済み rev と worktree の path patch で A/B
- fixture / CPU / OS / JMH / Criterion 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 118.594 / 124.915 / 119.034 | **119.034** | 96.369 / 97.635 / 108.313 | **97.635** | **-17.98%** |
| Java | comparison-heavy | 59.471 / 59.703 / 61.601 | **59.703** | 55.029 / 52.264 / 51.858 | **52.264** | **-12.46%** |
| Rust | complex | 12.004 / 12.073 / 11.135 | **12.004** | 5.782 / 5.616 / 5.118 | **5.616** | **-53.22%** |
| Rust | comparison-heavy | 1.895 / 1.918 / 1.955 | **1.918** | 1.398 / 1.297 / 1.316 | **1.316** | **-31.37%** |

単位は ms/op、各 run の JMH score / Criterion mean。Java 対 Rust の絶対値比較ではなく各 runtime 内の A/B として読む。
Java candidate の run3（108.3 ms）は他 2 run より遅いが、それでも baseline の最速 run（118.6 ms）より速い。

## 正確性

- unlaxer-parser: Java 671 + 987 tests（`MemoDiagnosticFrameTest` は memo frame の expected を materialize する helper 経由に変更）、
  Rust workspace tests（`diagnostic_allocation` / `checkpoint_allocation` / nested frame merge test を含む全 pass、tinyexpression-rs 32 tests）
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 生データ

`raw/2026-09-21-lazy-hint-materialization/`: JMH 結果 `java-{baseline,candidate}-run{1,2,3}.json`、Criterion `rust-*-run*/`、`java-gc-alloc-*`、`java-jfr-cpu-*`、`aggregate-jfr-cpu.py`
