# ParseContext 失敗診断 hint 収集の CPU 削減実験（2026-09-21）

## 結論

unlaxer-parser#219。#215 適用後の Java CPU の 77〜84% を占めていた失敗診断の hint 収集を、出力を変えずに
incremental / cached にした。

public facade の 3-run 中央値は Java で complex.tiny **-30.71%**、comparison-heavy.tiny **-33.70%**、Rust で
**-35.89%**、**-23.98%** 短縮した。診断出力（expected の内容と順序、parse stack、memo replay）・AST・source span の
test は変更なしで通るため**採用**した。#215 と合わせ、#207 時点から Java complex は 321 → 123 ms/op、Rust complex は
27.2 → 10.9 ms/op。

## 実装（Java）

- hint / parser 名リストの重複判定を key 集合で O(1) 化（順序付きリストは維持）
- `expectedHintCandidatesFor(parser)` を parser ごとに cache（parser graph と `TerminalSymbol.expectedDisplayTexts()` は 1 parse の間は不変）
- open な `TerminalSymbol` frame を `startParse` / `endParse` で追跡し、`deepestTerminalHintCandidate` の stack 走査を除去
- memo hit の replay を copy なしで merge し、rebased stack は採用される場合だけ生成

## 実装（Rust）

CPU attribution（`raw/2026-09-21-java-cpu-after-215/rust-cpu-attribution.txt`）では失敗診断 43.7% / 29.5%、
memo 45.9% / 25.4%（inclusive）で Java と同じ構図だった。変更:

- frame の expected を `BTreeSet<Rc<str>>` → 遅延生成の `Rc<Vec<Rc<str>>>`。失敗しない memo rule では allocation しない
- `fail_at` は既に先の位置で失敗している frame を skip し、重複判定は `Rc::ptr_eq` → 文字列比較
- memo hit は `remove` → replay → `insert` をやめ、保存済み診断を O(1) clone して共有文字列のまま replay
- `ParseError.expected` は生成時に sort + dedup（従来の `BTreeSet` 順と一致）。`diagnostic_allocation` の性質（同一失敗 1 回と 1,024 回で allocation 数一致）は維持

## 着手前 profile（baseline `629709f`、JFR ExecutionSample）

| Fixture | 診断（hint 収集 / snapshot / memo replay） | commit の token 収集・listener | transaction bookkeeping | parser dispatch 等 |
|---|---:|---:|---:|---:|
| complex.tiny（419 samples） | 77.3% | 12.7% | 6.0% | 4.1% |
| comparison-heavy.tiny（339 samples） | 84.4% | 7.7% | 2.7% | 5.3% |

self frame 上位: `String.equals` 33% / 39%、`deepestTerminalHintCandidate` 11% / 8%、`collectExpectedCandidatesIterative` 4% / 9%。
同じ profile から #209（effect summary、bookkeeping 上限 6% 未満）と Rust checkpoint（6.4 ns/回、2% 未満）を不採用と判断した
（`raw/2026-09-21-java-cpu-after-215/`）。

## 変更後 profile（candidate `05574f6`）

| Fixture | 診断 | commit の token 収集・listener | transaction bookkeeping | dispatch 等 |
|---|---:|---:|---:|---:|
| complex.tiny（189 samples） | 66.1% | 13.8% | 11.1% | 9.0% |
| comparison-heavy.tiny（148 samples） | 66.2% | 13.5% | 6.1% | 14.2% |

self frame 上位: `HashMap.putVal` 16% / 23%、`registerFailureCandidate` 11% / 8%、`String.equals` 7% / 10%。
残る診断コストは frontier ごとの hint 再登録で、unlaxer-parser#220（pair 記録と遅延展開）として切り出した。

## Allocation（public facade 1 parse、JMH `gc.alloc.rate.norm`）

| Fixture | before | after | 変化 |
|---|---:|---:|---:|
| complex.tiny | 298,889,654 B | 183,704,103 B | -38.5% |
| comparison-heavy.tiny | 131,595,598 B | 94,147,559 B | -28.5% |

## 測定条件

- unlaxer-parser baseline: `629709f`（#217 merge commit）、candidate: `05574f6`（PR #221）
- tinyexpression: `a4d2a7e`（#148 merge commit）。Java は isolated Maven repo で baseline/candidate を切り替え
- Rust は baseline を pin 済み rev `90530cb`、candidate を unlaxer-parser worktree の path patch で計測
- fixture / CPU / OS / JMH 設定は [2026-09-21 diagnostic-tracking-alloc experiment](2026-09-21-diagnostic-tracking-alloc-experiment.md) と同じ。timing は他負荷なしの直列 3 run

## Timing（public facade、3-run 中央値、ms/op）

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 175.103 / 177.431 / 185.600 | **177.431** | 123.616 / 122.938 / 117.131 | **122.938** | **-30.71%** |
| Java | comparison-heavy | 90.602 / 88.884 / 87.172 | **88.884** | 58.933 / 59.413 / 58.704 | **58.933** | **-33.70%** |
| Rust | complex | 16.856 / 17.069 / 17.347 | **17.069** | 10.943 / 11.518 / 10.725 | **10.943** | **-35.89%** |
| Rust | comparison-heavy | 2.476 / 2.507 / 2.667 | **2.507** | 1.845 / 1.906 / 2.017 | **1.906** | **-23.98%** |

単位は ms/op、各 run の JMH score / Criterion mean。Java 対 Rust の絶対値比較ではなく各 runtime 内の A/B として読む。
Rust candidate の run 間幅（10.7〜11.5 ms）は baseline の最速 run（16.9 ms）より十分小さい。

## 正確性

- unlaxer-parser: Java 670 + 987 tests、hint の first-seen 順序と重複排除を固定する test を追加
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 付随する負の結果

- `raw/2026-09-21-collect-tokenlist-218/`: #218（commit 時の `TokenList.stream()` 除去）は allocation 差 -1.06% / -0.66% で不採用
- `raw/2026-09-21-java-cpu-after-215/`: #209 の着手前 profile（Java bookkeeping 6% 未満、Rust checkpoint 6.4 ns/回）

## 生データ

`raw/2026-09-21-diagnostic-hint-cpu/`: JMH 結果 `java-{baseline,candidate}-run{1,2,3}.json`、`java-gc-alloc-*`、`java-jfr-cpu-*`、`aggregate-jfr-cpu.py`
