# tinyexpression (Rust) — JVM 不要の配布物

`rust/` は Cargo workspace で、次の 2 crate からなる（issue #177 の段階 1〜5）。

| crate | 中身 | 配布 |
|---|---|---|
| [`tinyexpression-rs`](tinyexpression-rs/README.md) | ubnfc 生成の P4 parser、typed AST、Java 互換の評価器（`runtime`）、FormulaInfo loader、CLI `tinyexpression`。通常依存ゼロ、`#![forbid(unsafe_code)]` | crates.io（owner の token 待ち）＋ GitHub Release の CLI バイナリ |
| `tinyexpression-ffi` | C ABI（`include/tinyexpression.h`）と wasm32 export。unsafe な境界コードはこちらだけに置く | GitHub Release の `libtinyexpression.{so,a}` ＋ header、`tinyexpression.wasm` |

## Versioning

- **Rust 版は Java 版と同じ version 番号を使う**（owner 決定、issue #181）。`rust/Cargo.toml` の
  `[workspace.package] version` が唯一の定義で、両 crate がこれを継承する。現在 `2.0.0`
  （Java `org.unlaxer:tinyExpression:2.0.0` と同じ言語仕様・同じ P4 文法）。
- Java と Rust は同じ tag `v<version>` から出す。Java が patch だけ出す場合も Rust は同じ番号へ
  追随してよい（変更が無くても番号は揃える）。片側だけの破壊的変更で major を分けることはしない。
- `tinyexpression --version` と `te_version()` は crate version と vendored parser の ubnfc commit
  （`rust/ubnfc-pin.txt`）を出す。

## CLI

```sh
cargo build --release --manifest-path rust/Cargo.toml -p tinyexpression-rs
rust/target/release/tinyexpression --help
printf '%s' '(1 + 2) * 3' | rust/target/release/tinyexpression eval -
rust/target/release/tinyexpression run formulaInfo.fi
```

`parse` / `check` / `eval`（式 1 本）と `load` / `run`（FormulaInfo 文書）。出力は常に 1 行の JSON、
exit code は `--help` に列挙（0 成功 / 2 引数 / 3 parse / 4 mapping・型 / 5 評価 / 6 I/O / 7 load）。

## C ABI

```c
#include "tinyexpression.h"
char *json = NULL;
int32_t code = te_eval((const uint8_t *)"1 + 2", 5, &json);  /* 0, {"ok":true,"value":{...}} */
te_free(json);
```

`te_parse` / `te_check` / `te_eval` / `te_formula_info(src, len, run, seed, &out)` /
`te_eval_context` / `te_formula_info_context` / `te_eval_trace`（issue #201） / `te_version` /
`te_free`（wasm 用に `te_alloc` / `te_dealloc`）。入力は UTF-8 バイト列、出力は CLI と**同一の**
JSON（NUL 終端）、戻り値は CLI の exit code（境界で捕まえた panic だけ 70）。
`rust/tinyexpression-ffi/tests/c/run.sh` が gcc でリンクして往復する。

### ABI stability

- 安定面は **JSON 文書**であって struct ではない。境界を越えるのはバイト列・`size_t`・`int32_t`・
  `uint64_t`・`char *` だけ。
- 同じ major version の間、JSON の既存フィールドの意味は変えない（フィールド追加はありうるので、
  利用側は未知のフィールドを無視すること）。exit code の意味も変えない。
- 関数の signature を変えるときだけ `TE_ABI_VERSION`（`te_version()` の `"abi"`）を上げる。

## CalculationContext 付き評価（issue #201）

`eval` は context-free、`run` は空の context で評価する。変数を読む式は JSON リクエストで評価する:
CLI `tinyexpression eval-context [FILE|-]` / `run-context`、C ABI・wasm `te_eval_context` /
`te_formula_info_context`（`te_parse` と同じ呼び方、入力がリクエスト JSON）。playground が使う経路。

```json
{"formula": "if($member){$price * 2}else{$price}",
 "resultType": "float", "numberType": "float", "angle": "degree", "seed": 7,
 "variables": [{"name": "member", "type": "boolean", "value": true},
               {"name": "price", "type": "float", "value": "1.5"},
               {"name": "o", "map": "object", "type": "string", "value": "boxed"}],
 "externals": [{"class": "sample.Fee", "method": "calculate", "arity": 3,
                "registered": true, "result": {"type": "float", "value": "12.5"}}]}
```

- `variables[].type` は値の種類（`float`/`number`・`double`・`int`・`long`・`short`・`byte`・`boolean`・`string`）、
  `map` は入れる `CalculationContext` の map（`number`・`string`・`boolean`・`object`、既定は種類に対応する map）。
  数値は Java の `Float.parseFloat` 規則で読む。`nowHour` / `nowDayOfWeek` も普通の number 変数として渡す。
- `externals[]` は Java のリフレクションの代わりの定数スタブ。どの行にも無いクラスは `Class.forName` 失敗
  （`UnsupportedOperationException`）、`method` と `arity` が合わなければ method not found、`registered: false` は
  インスタンス未登録（`CalculationException`）。`result` は `{"type": "null"}` も可。
- 成功は `{"ok":true,"value":{...},"text":"<String.valueOf>"}`。失敗は `"stage":"create"`（計算機の生成、parse
  失敗なら `diagnostic` 付き、exit 3/4）か `"apply"`（評価、exit 5）と `"error":{"kind":<Java 例外>,"message":...}`。
  リクエスト自体の誤りは `"stage":"request"`（exit 2）。
- wasm32 では method の入れ子 `call` の既定上限を 48 にしている（native は 256）。評価器がホストエンジンの
  スタック（V8 で約 1 MB）上で動くため、深い再帰がインスタンスごと trap する前に `StackOverflowError` にする。

### 評価 trace（issue #201 段階 3）

`tinyexpression eval --trace [FILE|-]`（式だけ、空の context）/ `eval-context --trace [FILE|-]`（上のリクエスト）、
C ABI・wasm `te_eval_trace`（入力は `te_eval_context` と同じリクエスト JSON）。応答は `eval-context` の応答に
`"trace"` を足したもの:

```json
{"ok":true,"value":{...},"text":"7.0",
 "trace":{"steps":14,"recorded":14,"truncated":false,
          "root":{"kind":"IfExpr","span":[0,33],"value":{...},"text":"7.0","children":[
            {"kind":"ComparisonExpr","span":[3,9],"value":{"kind":"boolean","value":true},"text":"true","children":[...]},
            {"kind":"Leaf","span":[8,9],"leaf":"1", ...}]}}}
```

- 1 ノード = tree walker（Java の `P4TypedAstEvaluator` 相当、`Program::eval_tree`）の 1 ステップ。`kind` は AST ノード名、
  `BinaryExpr` の文字列オペランド（数値リテラル・`$変数`・文字列）は `"kind":"Leaf"` と `"leaf"`。`span` はソースの
  code point 範囲、`value`/`text` は結果（`value` は `eval` と同じ形）、失敗したステップは `"error"`。
  評価されなかった分岐は含まれない（評価順 = 子の順）。
- 生成できない式は `"trace":null`。記録は 20,000 ステップまで（超えたら `"truncated":true`、`steps` は総数）。
- trace は観測だけで結果を変えない（Java 差分 gate が全行を trace 有り・無しで評価して一致を要求する）。
  closure 版（`Program::compile`）には hook が無く、trace を頼まない評価（`eval-context`）は従来どおり。
- Rust API: `Program::eval_tree_traced(context, host, &mut dyn TraceHook)`、記録器は `runtime::TraceRecorder`。

## wasm32

```sh
cargo build --profile release-small --target wasm32-unknown-unknown \
  --manifest-path rust/Cargo.toml -p tinyexpression-ffi
node rust/examples/wasm/node-smoke.mjs      # ブラウザ無しで demo の binding を実行
```

`tinyexpression.wasm` は import ゼロ（wasm-bindgen 不使用）で、C ABI と同じ関数を export する。
[`examples/wasm/`](examples/wasm/) の `tinyexpression.mjs` がブラウザと node 共通の薄い binding、
`index.html` が 20 行のブラウザ demo。

| profile | `tinyexpression.wasm` | gzip -9 |
|---|---:|---:|
| `release`（opt-level 3） | 3,498,076 B | 598 KB |
| `release-small`（opt-level "z"、LTO、codegen-units 1、panic=abort、strip） | 2,089,119 B | 395 KB |

（2026-09-25、rustc 1.98、ubnfc cefdbd7。大半は vendored parser の生成コード。wasm-opt 未適用）

- wasm32 には parser の stack escalation thread（ubnfc D-070）が無い。極端に深い入れ子は
  trap ではなく `maximum parse depth exceeded` の parse エラー（exit 3）になる。
- `wasm32-unknown-unknown` には時計が無い。`inTimeRange` 等は Java と同じく変数
  `nowHour` / `nowDayOfWeek` を読むので影響しないが、`run` の `random()` は呼び出し側の `seed` で決まる。

## 配布経路

- **GitHub Release**: `.github/workflows/release-rust.yml`（`workflow_dispatch`、`dry_run` 既定 true）が
  Linux x86_64 CLI、`libtinyexpression`（so/a + header）、wasm32 を build・smoke test し、
  tag `v<version>` の Release に添付する（tag は事前に存在している必要がある）。
- **crates.io**: `tinyexpression-rs` は `cargo publish --dry-run` が通る状態。本番 publish は
  同 workflow の最後で **`CARGO_REGISTRY_TOKEN` secret があるときだけ**走る（無ければ skip）。
  この開発マシンからは crates.io への publish が 403 なので、token の登録と初回 publish は owner が行う。
- `tinyexpression-ffi` は crate としては出さない（`publish = false`）。

## vendored parser の pin

`rust/ubnfc-pin.txt` の ubnfc commit から `rust/scripts/regenerate-ubnfc.sh --write` で再生成する。
`rust/check-generated.sh` は byte 一致（ubnfc checkout が無い CI では SHA-256 manifest）と、
`tinyexpression-rs/src/api.rs` の `UBNFC_COMMIT` が pin と一致することを検査する。
Java 側の vendored parser（`scripts/regenerate-ubnfc-parser.sh` / `UBNFC_PIN`、issue #194）とは別の pin である。
