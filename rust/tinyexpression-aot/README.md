# tinyexpression-aot — 明示許可付き Rust 本文のネイティブビルド

信頼された作者の `rustcodeblock` をコンパイルし、TinyExpression の `external` から呼ぶ
実行ファイルを作る。通常の parser / evaluator / IDE からは呼ばれない。

**Rust 本文は任意コードであり sandbox ではない。作者をアプリケーション開発者と同等に
信頼できる場合だけ `--allow-rust-code` を指定すること。** ビルド時のファイル読み取りや
実行時の I/O・プロセス終了などを防がない。生成先は信頼された親ディレクトリ内に置く。

## 実行例

リポジトリからビルドする場合（Rust 1.85 以上、対象の標準ライブラリと linker が必要）:

```sh
cargo build --locked --release --manifest-path rust/Cargo.toml -p tinyexpression-aot
rust/target/release/tinyexpression-aot build --allow-rust-code --out /tmp/tinyexpression-example-build rust/tinyexpression-aot/examples/greeting.tiny
printf '%s' '{"resultType":"string","variables":[{"name":"prefix","type":"string","value":"Hello, "},{"name":"name","type":"string","value":"Rust"}]}' | /tmp/tinyexpression-example-build/program
```

出力先はまだ存在しないディレクトリを指定する。生成済みなら別の名前を使う。
結果は `Hello, Rust`。同じ context JSON を読み、`formula` は省略可能（指定するなら
ビルド時の全文と完全一致が必要）。空 stdin は `{}`。生成物は Java / Cargo / rustc の
ない PATH でも動くが、対象 OS の通常の動的ライブラリまで不要になるわけではない。

配布された `tinyexpression-aot` 自体は runtime のソースを内蔵しているため、式の build に
Cargo や Java、リポジトリ checkout は不要。`rustc` と linker は build 時だけ使う。
ソースから指定された Cargo 依存の取得や build script の実行は行わない。
CI / release の CLI archive には `greeting.tiny` も同梱する。
展開先では `./tinyexpression-aot build --allow-rust-code --out NEW_DIRECTORY greeting.tiny` で使える。

| 配布物 | 動作 |
|---|---|
| `tinyexpression parse` | 構文解析・AST 出力。本文を実行しない |
| `tinyexpression eval-context` | 汎用の typed-AST runtime。未リンクの Rust 本文は `CB005` で拒否 |
| `tinyexpression-aot build` | 明示許可を検査してから Rust 本文と runtime を compile/link |
| 生成された `program` | 固定ソースとネイティブ本文を持つ実行物。DSL は既存 typed-AST 評価器で実行 |

これは **DSL 全式の機械語 lowering ではない**。式の parse を実行時に省くものでもない。
詳細・Java 対応表・診断・再現情報・未対応事項は
[AOT 契約](https://github.com/opaopa6969/tinyexpression/blob/master/docs/rust-codeblock-aot.md) を参照。
