# Rust CodeBlock AOT と型付き external binding

tinyexpression #229。Rust 本文を実際に型検査・リンクして、元ソースを内蔵したネイティブ
実行物から呼ぶ。本文の保存だけだった #228 から実行へ進める。

> **Warning**: Java code blocks compile and execute arbitrary code on the JVM. Only use this feature when formula authors are fully trusted. Do not expose this capability to untrusted users.

Rust にも同じ信頼が必要。**AOT は sandbox ではない**。許可された本文は build 時に
`include!` 等でファイルを読み、実行時に I/O・通信・任意のプロセス操作もできる。
parse / check / IDE / 通常 eval は compiler を起動しない。ソース内の宣言、Java の
グローバル設定、JSON リクエストから Rust の実行許可を得ることはできない。

## 使い方と境界

[ビルド・実行例](../rust/tinyexpression-aot/README.md) と
[実際に build できる式](../rust/tinyexpression-aot/examples/greeting.tiny) を参照。

`Builder::new(BuildOptions { allow_rust_code: true, ..Default::default() })`
の `build(source, fresh_directory)` が library API。CLI は
`tinyexpression-aot build --allow-rust-code --out NEW_DIRECTORY [--rustc PATH] [--target TRIPLE] [FILE|-]`。
build と execute は別操作。build 時には `register` も external 関数も呼ばない。

本文は block ごとの Rust module になり、次の hook を export する。

```rust
pub fn register(class: &mut ClassBindings) -> Result<(), BindingError>
```

`class.bind::<(f32, String), bool, _>("method", |variables, (n, s)| Ok(...))`
で型付き登録する。`variables: &dyn Variables` は呼出地点の scoped context であり、
宣言・引数の shadowing を反映する。callback は `FnMut` で状態を持てる。
0 引数は `()`、1〜6 引数は tuple、任意個数は再帰的な `Cons<Head, Tail>` を使う。
Rust lifetime を越える borrowed 値は受け渡さず、必要な値を所有・clone する。

`runtime::bindings::Registry` は既存 `ExternalHost` を実装する。
クラス名→method 名＋引数個数で解決し、未登録 instance は `NotRegistered`、不明 class / method、
引数不一致、呼出失敗を明示する。`set_registered(false)` は呼出前に拒否する。
Rust callback / hook の unwind panic は失敗に変換するが、abort や `exit` は防がない。

Java reflection は同名・同 arity の overload の選択順を保証しないため、Rust は重複を
`CB006` で拒否する。任意の JVM class を Rust へロードする仕組みではない。
組込み context は読み取り専用。Java の任意クラスアクセス・context の任意変更と同等の
ホスト権限 API までは提供しない（Rust closure 自身が保持する状態は変更できる）。

## 型対応

| Rust 引数 / 返却型 | Java external 境界 / runtime Value |
|---|---|
| `f32`, `f64` | `float` / `Float`, `double` / `Double`。Number 変換または文字列 parse、parse 失敗は既存 Java と同じゼロ |
| `i32`, `i64` | `int` / `Integer`, `long` / `Long`。Java の Number 変換・整数 parse |
| `i16`, `i8` | `short`, `byte`。対応する boxed 値を受け取る。primitive short は Byte の widening も可 |
| `bool` | `boolean` / `Boolean`。既存 Java と同じ true の大小文字非依存判定 |
| `String` | 非 null の `String.valueOf` 相当 |
| `Option<T>` | nullable boxed/reference。`None` ↔ `Value::Null`。`Option<i16>` は boxed Short なので Byte widening をしない |
| `Value` | Java `Object` 相当の全 tagged 値（null を含む） |
| `HostObject` | opaque object（class 名・表示文字列・任意の共有 Rust payload）。JVM object 自体は移せない |
| `()`（返却） | Java void/null |

非 `Option` の引数は null を拒否する。callback の `Err(ExternalError::Failed(...))` は
Java の反射呼出失敗と同じ calculator エラー種別になるが、対象言語の stack trace や
compiler の文言まで一致させる契約ではない。
既存の calculator が null の最終結果を拒否することや、number 式で大きい long に生じる
丸めは変えない。共通 oracle は `9007199254740993` の number 経由での丸めと、object 経由の
精度保持を別々に検査する。これを AOT が導入した精度低下としては扱わない。
また、既存 DSL の NumberExpression に包まれた未定義変数は、型付き `as object` であっても
引数到達前にゼロへ変換される場合がある。nested object 呼出しも StringExpression 経由なら
null → 空文字になる。両言語で共通 oracle を持つ既存の制約であり、Rust binding の
`Option<T>` を用意しても DSL 内のこの変換を取り消さない。ホストから直接登録関数を呼ぶ
`Registry` は真の `Value::Null` を渡せることを別テストで固定する。

## 元ソースとの対応

`CompiledBlock` は exact identifier・exact body とコンパイル済み hook を持つ。
`LinkedCode::new(source, blocks)` は **全ブロックの対応を検査してから** hook を呼ぶ。
件数・順序・名前・本文が違えば `CB007`。通常の `Program::new` の `CB005` を削除したり、
ソースから本文を除去したりしていない。`LinkedCode::program` / `externals` は既存 Host API、
tree / closure 評価で使える。`evaluate_request` は通常の context JSON を受け取る。
コンパイル済み class はリクエストの stub より優先する。

`LinkedCode` は信頼された埋込みホスト用 API でもあり、本文と関数ポインタが本当に同じ
コンパイル産物かを暗号的に証明するものではない。配布済みバイナリを信頼する責任はホストにある。

## 生成物と再現情報

本文は `blocks/block_0.rs` のような index 付き固定名へ書く。label は Rust identifier や
ファイル path に挿入しない。出力先は新規ディレクトリのみで、既存ファイルを削除・上書きしない。
部分出力は調査用に残し、成功時だけ `artifact.json` を書く。失敗時は可能なら `failure.json`。
成功判定には終了コードと `artifact.json` を使い、ディレクトリや一時 binary の存在だけを使わない。
悪意のある同時書込みに対する filesystem sandbox ではないので出力親を信頼する必要がある。

`building.json` は build 入力の snapshot、`artifact.json` はその成功結果を含む。
source SHA-256、ubnfc commit、P4 文法の SHA-256、生成 parser を含む runtime 全ソースの inventory/hash、
binding ABI、builder source hash、toolchain の `rustc -vV`、target、最適化オプション、
builder の固定依存と完全な `builder.lock` の hash、完成 binary の hash を保持する。
`buildId` は `building.json` から `buildId` を除いた canonical JSON に対する SHA-256。
同一 buildId は入力同一性であって binary の bit-for-bit 再現性の主張ではない。

実行物は vendored runtime と標準ライブラリだけをリンクする。式から Cargo dependency / build
script を指定する機構はない。build tool の `serde_json` / `sha2` は Cargo.lock で固定する。
再利用するのは **同一 Builder 内の runtime rlib のみ**。source inventory・toolchain・target・
設定の key とファイル hash を再確認し、本文を含む executable は毎回 compile/link する。
永続共有 cache、未検証 binary のロード、失敗 build の executable 再利用は行わない。

## 診断

`CB001`〜`CB005` は [ソース保持契約](code-block-source-contract.md) のまま。
`CB006`: binding 登録失敗、`CB007`: 元ソースとの不一致。
build は `CB100`: 式の構文、`CB101`: Rust compiler、`CB102`: toolchain 起動、
`CB103`: filesystem、`CB104`: build option の失敗を JSON で返す。

compiler の JSON byte span は無加工の本文内 UTF-8 位置から元ソースの Unicode code point
`[start,end)` へ戻す。各 location は `source` / `wrapper` / `runtime` を区別する。
hook 不在など wrapper 起因の位置は block の name span に対応づける。
Rust compiler のメッセージ本文は compiler version に依存する。本文の Unicode / CRLF と
複数 block、wrapper エラーは実際の `rustc` を使って検証する。

## 検証と残る境界

Java `NativeBindingConformanceTest` と Rust `native_shared.rs` は
`src/test/resources/native-bindings/cases.json` を同じ oracle とし、同義の Java / Rust 本文を
実際にコンパイル・実行する。数値各型、文字列、boolean、nullable、opaque object、scoped
context、複数 block、左からの副作用順序・短絡・失敗条件を検査する。
未登録 class の一件のみ両側の既存 stub 登録機構で状態を構成する。その他の本文呼出しは stub ではない。
Rust は native 実行時の PATH を空にする。独立したテストで preflight の compiler 非起動、
キャッシュ破損、出力衝突、label、診断、既定拒否、CLI を検査する。

```sh
mvn test -Dtinyexpression.skipRailroad=true -Dtest=NativeBindingConformanceTest
cargo test --locked --manifest-path rust/Cargo.toml -p tinyexpression-aot
cargo test --locked --manifest-path rust/Cargo.toml -p tinyexpression-rs --test native_bindings
```

full-spec 全体の完了ではない。本文は typed AST に保持され、通常の AST-only 評価も未リンク
Rust ブロックを拒否する（[#234](https://github.com/opaopa6969/tinyexpression/issues/234)）。
本文中に三連 backtick がある場合は [4 個以上の長い fence](code-block-source-contract.md#長い-fence-による本文内-backtick-の保持)
を使う。従来の三連構文の受理は変更しない。専用 JSON entry point は eval-context で、
FormulaInfo 全体の AOT、AOT binary の DAP / LSP 統合、DSL 全式の機械語生成は含まない。
通常の Java API・C ABI・wasm API へ compiler 起動を足さない。
build tool の crates.io 公開は行わず、当面は workspace / CI / release binary として配布する。
