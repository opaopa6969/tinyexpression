# ParseContext transaction frame 再利用の allocation 監査（2026-09-20）

## 結論

unlaxer-parser#208（Java transaction frame 再利用と Rust checkpoint allocation 監査）は、
production 変更なしの**不採用**とした。

- Java: transaction frame（`TransactionElement` / `ParserCursor` / cursor / `TokenList`）の allocation は
  public facade 1 parse の allocation pressure の 1.42%（complex.tiny）〜3.00%（comparison-heavy.tiny）
  に留まる。frame pool や配列 stack で削れる上限がこの範囲で、#207 の run 間ノイズ（±3〜5%）に埋もれる。
- Rust: `Checkpoint` は stack value で、payload handle は `Rc` の refcount 増減だけ。追加 checkpoint 4,096 個で
  追加 allocation 0 を test で固定した（unlaxer-parser `rust/unlaxer-alloc-audit/tests/checkpoint_allocation.rs`）。
- 監査で見つかった実際の Java allocation hotspot は失敗診断の進捗追跡（`trackCursorProgress` /
  `snapshotStackElements` / `localStackSnapshot`）で、allocation の約 75〜78% を占める。
  unlaxer-parser#215 として次候補に切り出した。

A/B timing benchmark は実施していない。候補実装を作らなかったため比較対象がなく、baseline の timing は
[checkpoint copy-on-write experiment](2026-09-20-checkpoint-cow-experiment.md) の candidate 値
（Java complex 321.311 / comparison-heavy 134.946 ms/op、Rust 27.161 / 3.573 ms/op）がそのまま現行値である。

## 測定条件

- unlaxer-parser baseline: `5770ed3dfb41aa528422cbce6e454332623e64c9`（#207 merge commit）
- tinyexpression: `a195067d`（#146 merge commit）
- fixture: `complex.tiny`（325 code points）、`comparison-heavy.tiny`（179）
- CPU: AMD Ryzen 9 7950X、OS: Linux `6.18.33.2-microsoft-standard-WSL2`
- Java: Oracle JDK 21.0.9、JMH 1.37。allocation 計測は 1 fork、3 × 1 秒 warmup、3 × 1 秒 measurement。
  `-prof gc` で `gc.alloc.rate.norm`、`-prof jfr` で `jdk.ObjectAllocationSample`（stack depth 8）を取得
- Rust: rustc/cargo 1.85、counting `GlobalAlloc` で 1 parse の allocation 数と bytes を計数

allocation 計測は timing benchmark ではないので、この run の us/op は採否判断に使わない。

## Java allocation profile

`gc.alloc.rate.norm`（1 parse あたり）:

| Fixture | B/op | GC count / iteration |
|---|---:|---:|
| complex.tiny | 1,176,448,393 | 4 |
| comparison-heavy.tiny | 431,940,718 | 2〜3 |

JFR allocation pressure の内訳（object class ← 呼び出し元）:

| Fixture | site | 割合 |
|---|---|---:|
| complex | `ArrayDeque$DeqIterator` ← `ArrayDeque.iterator` ← `ParseContext.trackCursorProgress` | 48.84% |
| complex | `Object[]` ← `Arrays.copyOfRange` ← `ArrayList$SubList.toArray` ← `ArrayList.<init>`（`localStackSnapshot`） | 11.91% |
| complex | `ParseStackElement` ← `snapshotStackElements` ← `trackCursorProgress` / `registerFailureCandidate` | 12.80% |
| complex | `Object[]` ← `ArrayList.<init>` ← `localStackSnapshot` / `snapshotStackElements` | 11.75% |
| complex | transaction frame 関連合計（`EndExclusiveCursorImpl` 0.49%、`CodePointIndex` 0.27%、`TokenList` の `ArrayList` 0.27%、`TransactionElement` 0.04%、`checkpointTransactionalState` の stream 0.26% ほか） | **1.42%** |
| comparison-heavy | `ArrayList` ← `localStackSnapshot` ← `trackCursorProgress` ← `endParse` | 37.69% |
| comparison-heavy | `Object[]` ← `copyOfRange` / `ArrayList.<init>`（`localStackSnapshot` / `snapshotStackElements`） | 21.92% |
| comparison-heavy | `ParseStackElement` ← `snapshotStackElements` | 16.65% |
| comparison-heavy | transaction frame 関連合計 | **3.00%** |

`trackCursorProgress` は `startParse` / `endParse` / `consume` ごとに呼ばれ、`memoDiagnosticFrames` が
空でも `ArrayDeque` の iterator を生成し、cursor が frontier に達するたびに parse stack 全体を
`ParseStackElement` のリストへ snapshot する。これは transaction frame ではなく失敗診断の bookkeeping である。

## Rust checkpoint allocation

`Checkpoint` 構造体は `position` / `matched_position` / `nodes.len()` の scalar と
`Option<Rc<HashMap>>` / `Option<Rc<StateMap>>` / `Option<Rc<ScopeStore>>` の handle だけを持ち、
`checkpoint()` は `Rc::clone`、`restore()` は代入と `truncate`、`commit_checkpoint()` は counter 更新のみ。

unlaxer-parser の `checkpoint_allocation` test は、同じ入力・同じ仕事に対して checkpoint 層だけを
1 層と 9 層（8 層 × 512 反復 = 4,096 個の追加 checkpoint）で比較する。

| シナリオ | 1 層 | 9 層 | 追加 allocation |
|---|---:|---:|---:|
| 空 payload / commit（`Optional` 層） | 0 | 0 | 0 |
| 非空 payload（capture + typed state + scope 宣言） / commit | 0 | 0 | 0 |
| 空 payload / rollback（単要素 `Sequence` 層で失敗伝播） | 1,024 | 1,024 | 0 |
| 非空 payload / rollback | 1,024 | 1,024 | 0 |

rollback 側の 1,024 は 512 反復ごとに 1 回失敗する literal の診断記録（`expected.to_owned()` と
`BTreeSet` への挿入で 2 allocation / 失敗）で、checkpoint 層数に依存しない。commit 側は 512 反復の
literal 一致を含めて allocation 0 だった。

TinyExpression public facade 1 parse 全体の allocation 数は complex.tiny 789,084（17.7 MB）、
comparison-heavy.tiny 82,094（4.0 MB）で、#207 の checkpoint 数（53,985 / 15,135）より多いが、上記から
checkpoint には帰属しない。`FailureDiagnostic::record` の `expected.to_owned()` と `Fragment` 生成が
次の attribution 候補で、#215 で Java の診断 allocation と対称に扱う。

## 判断

- Java frame pool / 配列 stack: 不採用。削減上限 1.4〜3.0% の allocation は timing に有意な差を生まない。
- Rust frame pool: 不要。allocation ゼロを test で固定した。
- 次候補: unlaxer-parser#215（診断進捗追跡の allocation 削減）。#214 の Phase 1 直後に挿入を提案。

## 生データ

`raw/2026-09-20-frame-reuse-audit/`:

- `java-gc-alloc-baseline.json` / `java-gc-alloc-baseline-summary.txt`: JMH `-prof gc` の結果
- `java-jfr-allocation-complex.txt` / `java-jfr-allocation-comparison-heavy.txt`: JFR allocation sample の集計
- `aggregate-jfr-allocation.py`: `jfr print --events jdk.ObjectAllocationSample --stack-depth 8` 出力の集計 script
- `rust-allocation-count-baseline.txt`: Rust 1 parse の allocation 数

## 再現コマンド

```sh
# Java allocation（isolated Maven repo に unlaxer-parser を install しておく。flatten.skip は付けない）
TINYEXPRESSION_MAVEN_REPO_LOCAL=/path/to/isolated-m2 \
  benchmarks/run-java-parser-benchmark.sh 'P4ParserBenchmark.publicFacade' \
  -p 'fixture=complex.tiny,comparison-heavy.tiny' -f 1 -wi 3 -i 3 \
  -prof gc -rf json -rff /tmp/java-gc.json

TINYEXPRESSION_MAVEN_REPO_LOCAL=/path/to/isolated-m2 \
  benchmarks/run-java-parser-benchmark.sh 'P4ParserBenchmark.publicFacade' \
  -p 'fixture=complex.tiny' -f 1 -wi 2 -i 3 -prof 'jfr:dir=/tmp/jfr'
jfr print --events jdk.ObjectAllocationSample --stack-depth 8 /tmp/jfr/**/profile.jfr > /tmp/alloc.txt
python3 benchmarks/results/raw/2026-09-20-frame-reuse-audit/aggregate-jfr-allocation.py /tmp/alloc.txt

# Rust checkpoint allocation（unlaxer-parser repository）
cargo +1.85 test --locked --manifest-path rust/Cargo.toml -p unlaxer-alloc-audit --test checkpoint_allocation
```
