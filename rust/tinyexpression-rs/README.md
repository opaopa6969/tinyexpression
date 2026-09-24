# tinyexpression-rs native frontend and evaluator

`tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` から ubnfc の Rust backend で生成した JVM 不要・依存ゼロの parser、typed AST と、context-free scalar evaluator/CLI である。

```sh
printf '%s' '1 + 2' | cargo run --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs -- parse -

cargo run --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs -- parse formula.tiny

printf '%s' '(1 + 2) * 3' | cargo run --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs -- eval -
```

`parse` の成功時は `{"ok":true,"ast":...}`、`eval` の成功時は `number`・`boolean`・`string` の型付き値を出力する。number は Java float と照合できる raw bits も含む。構文エラーは `{"ok":false,"stage":"parse","diagnostic":...}`、未対応 AST は source span 付きの `unsupported_node` 評価エラーになる。ASTはcapture文字列とspanを所有するためparse treeを破棄できるが、入力全文は保持しない。公開offsetはUnicode code point単位である。

library API は `parse(&str)` に加えて `evaluate(&str) -> Result<Value, EvaluationError>` と `evaluate_ast(&Ast)` を公開する。`Value` は `Number(f32)`・`Boolean(bool)`・`String(String)` を持ち、`number()`・`boolean()`・`string()` で型安全に参照できる。number の `f32_bits()` では Java float とビット単位で比較できる。

| exit code | 意味 |
|---:|---|
| 0 | parse・mapping・evaluation成功 |
| 2 | CLI引数不正 |
| 3 | parse失敗（trailing inputを含む） |
| 4 | mapping失敗 |
| 5 | evaluation失敗（未対応node・context依存構文を含む） |
| 6 | I/O失敗 |

## 保証範囲

- parser は ubnfc の生成物を vendoring したもの（下記「Parser」）。通常依存はゼロ（`criterion` は `benchmarks` feature のときだけ）で、`unlaxer-runtime` は依存グラフから外れた。debug/test build でも 2 MiB thread stack 上の P4 parse+mapping を検証する。
- Java P4と同じUBNFをsource of truthとし、生成物は `rust/check-generated.sh` で drift 検査する。
- parserとevaluatorはJava・手書きparser・別評価器へfallbackしない。
- top-levelの裸のboolean比較は、通常の`Formula`解析が数値prefixを選んで失敗した場合に限り、同じ生成文法の`BooleanExpression` ruleで全文を再解析して`FormulaExpr`へ包む。これはJavaの`P4PreferredAstMapper`と同じroot disambiguationであり、別parserへのfallbackではない。
- evaluatorの対応範囲は、状態を必要としない f32 数値literalと四則演算、boolean literal・`not`・`|`・`&`・`^`・equality、文字列literal・連結・比較、数値比較、`if`・ternary、number/string/boolean `match`、括弧、空白・commentである。優先順位と左結合は生成P4 typed ASTそのものに従う。
- Java意味論と同じく `|`・`&`・`^` は右辺も評価する eager 演算であり、`if`・ternary・`match` は選択された値だけを評価する。f32比較は `Float.compare` のNaN・signed-zero順序に一致する。
- import、宣言、method、変数、組み込み/外部関数、slice、objectは未対応である。文脈依存のdocument要素は `context_required`、その他のtyped AST nodeは `unsupported_node` として明示的に失敗する。
- 型hint付きvariableはhintを保持し、number/string/boolean/objectの結果familyを選択する。directな`match`のcase/defaultで異なるhint familyを混在させた場合と、対応するmatch familyがないobject-result matchは型エラーになる。
- `tests/fixtures/numeric-f32.tsv` は独立した期待 f32 bits を保持し、Rust library/CLIとJava `P4TypedAstEvaluator` の `p4-typed` runtimeが共有する。
- `tests/fixtures/scalar-control.tsv` はboolean/string/比較/制御構文について、値・評価順序・Java/Rust parityを共有する。
- `tests/fixtures/root-expression.tsv` はroot dispatchの全文消費、Java/Rustそれぞれの厳密なsemantic root、必要な子nodeを共有検証する。
- `javacodeblock` の内容を実行しない。`rustcodeblock` は未実装で、将来も既定無効・明示許可付きとする。
- CI artifactはUbuntuのLinux x86_64用であり、完全static binaryや全OS対応を意味しない。

## Parser（ubnfc の vendoring、issue #178）

| パス | 中身 |
|---|---|
| `src/generated/ubnfc/` | `ubnfc ir` → `ubnfc-rust --no-emit-driver` の出力。`#![forbid(unsafe_code)]`、std のみ |
| `src/generated/ubnfc/scanners.rs`, `scanners_tests.rs` | ubnfc `scanners/rust/` の extern token scanner（STRING / CODE_START / CODE_END）とその単体テスト |
| `src/generated/compat.rs` | ubnfc AST → 公開 `Ast` の変換。`rust/scripts/generate-compat.py` が両 `ast.rs` から生成 |
| `src/generated/ast.rs`, `evaluator.rs` | 公開 typed AST と `Semantics` trait。旧 unlaxer 生成物を引き継ぎ、以後は手で保守（node 集合は文法由来なので、文法を変えたら `generate-compat.py` が不一致で止まる） |
| `rust/ubnfc-pin.txt` | ubnfc commit、文法・IR・scanner の SHA-256 |

再生成と検査:

```sh
bash rust/scripts/regenerate-ubnfc.sh --write   # ../ubnfc か $UBNFC_DIR の checkout から
bash rust/check-generated.sh                     # = regenerate-ubnfc.sh --check + generate-compat.py --check
```

生成器の出力に対して行う機械的な変更は 3 つだけで、`--check` も同じ手順を踏む:
(1) 生成器の `Cargo.toml` を捨てる、(2) `parse_entry_with_options` から scanner 付きの
`parse_entry_with_scanner` を ubnfc `examples/p4-rust/build.rs` と同じ文字列置換で派生させ `parser.rs` 末尾に追記し `mod.rs` から re-export する
（result-family の再解析で入口 rule を選ぶため。scanner 無しの入口では STRING が全部落ちる）、
(3) `scanners.rs` 先頭の `//!` を `//` にする（`include!` で取り込むため）。
ubnfc checkout は共有作業ツリーなので、HEAD が pin と違っても警告だけ出し、固定の実体は byte 比較と各 SHA-256 で担保する。
旧 `rust/unlaxer-revision.txt` と unlaxer generator による検査は役目を終えたので削除した。

## AST shape（2.0 で形を変えない）

ubnfc の typed AST と公開 `Ast` は同じ IR から作られており、node 名 86 種・field 名は完全に一致する。
違いは 1 点だけ: 演算子連鎖の最内オペランドを、ubnfc は `BinaryExpr{left:null, op:["1"], right:[]}` という包みで持ち、
公開 `Ast` は `"1"`（`AstValue::Text`）を直接置く（ubnfc 側 16 fixture の golden 比較で 1,325 箇所、差分クラスはこれ 1 種類のみ）。
**決定: 変換層（`compat.rs`）で公開 JSON 形を維持する。** 形を 2.0 として変える案は、既存テストの AST 期待値と
evaluator（`AstValue::Text` を数値 literal として読む）を書き換えることになり「テストを弱めずに緑」を満たせないため採らない。
ubnfc の AST で span を持たない裸の text capture（`true` など）は、所有 node の span を代わりに使う（JSON には出ない。評価エラーの span にのみ効く）。

## 旧 parser との互換性（2026-09-24 実測）

旧 parser（unlaxer-runtime）と新 parser の release CLI に同じ入力を `parse` / `eval` で流し、exit code と出力 JSON を比べた。
入力は P4 fixture 16、`benchmarks/fixtures` 12、`tests/fixtures/*.tiny` 4、`tests/fixtures/*.tsv` の式 113、`tests/*.rs` の文字列 literal 156、Java `src/test/**` の文字列 literal 2,047（計 2,348 入力 × 2 コマンド）。

- 受理/拒否・exit code・受理時の AST JSON・評価値: **全件一致**。
- 差分は診断の `expected` / `farthestExpected` 配列だけ（2,898 件）: 終端の名前付け（`number` ↔ `NUMBER`、`'```'` など）と列挙順が backend ごとに違う。`kind` と `offset` は一致する。公開契約は「kind・offset・非空の expected」で、テストもそれを検査している。
- それ以外の差は 1 入力のみ: 閉じていない block comment `/*` で `farthestOffset` が旧 0 / 新 2（どちらも `syntax` @0 で拒否）。

## parse 時間（旧 unlaxer-runtime vs 新 ubnfc、2026-09-24）

`examples/parse-bench.rs`（公開 `parse` の 1 回＝1 sample、warm-up 1 回）を旧 build と同一ホストで fixture ごとに交互実行、5 ラウンド × 20 sample。
WSL2 x86_64、計測中の load average 9〜14（他作業と共有）なので中央値は揺れが大きく、比較には最小値を主に使う。単位 ms。

| fixture | 旧 中央値 | 旧 最小 | 新 中央値 | 新 最小 | 倍率（中央値） | 倍率（最小） |
|---|---:|---:|---:|---:|---:|---:|
| complex | 3.013 | 1.664 | 0.144 | 0.087 | 20.9x | 19.0x |
| complex-x4 | 12.651 | 6.758 | 0.658 | 0.359 | 19.2x | 18.8x |
| complex-x16 | 177.774 | 28.054 | 4.264 | 1.409 | 41.7x | 19.9x |
| complex-x64 | 418.232 | 163.983 | 95.560 | 10.075 | 4.4x | 16.3x |

```sh
cargo build --release --locked --manifest-path rust/Cargo.toml --example parse-bench
rust/target/release/examples/parse-bench --rounds 20 benchmarks/fixtures/complex*.tiny
```
