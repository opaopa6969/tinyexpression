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

binary 名は `tinyexpression`（issue #181 で `tinyexpression-rs` から改名）。`check` は parse・mapping・型検査だけ行い `{"ok":true}` を返す。全コマンド・exit code は `tinyexpression --help`、配布形（C ABI・wasm32・versioning）は [`rust/README.md`](../README.md) を参照。CLI・C ABI・wasm が共有する JSON 契約は `tinyexpression_rs::api` にある。

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
| 7 | FormulaInfo の load 失敗（構文は通ったが Java loader も拒否する内容。構文エラーは 3） |

## 保証範囲

- parser は ubnfc の生成物を vendoring したもの（下記「Parser」）。通常依存はゼロ（`criterion` は `benchmarks` feature のときだけ）で、`unlaxer-runtime` は依存グラフから外れた。debug/test build でも 2 MiB thread stack 上の P4 parse+mapping を検証する。
- Java P4と同じUBNFをsource of truthとし、生成物は `rust/check-generated.sh` で drift 検査する。
- parserとevaluatorはJava・手書きparser・別評価器へfallbackしない。
- top-levelの裸のboolean比較は、通常の`Formula`解析が数値prefixを選んで失敗した場合に限り、同じ生成文法の`BooleanExpression` ruleで全文を再解析して`FormulaExpr`へ包む。これはJavaの`P4PreferredAstMapper`と同じroot disambiguationであり、別parserへのfallbackではない。
- context-free な `evaluate` / CLI `eval` の対応範囲は、状態を必要としない f32 数値literalと四則演算、boolean literal・`not`・`|`・`&`・`^`・equality、文字列literal・連結・比較、数値比較、`if`・ternary、number/string/boolean `match`、括弧、空白・commentである。優先順位と左結合は生成P4 typed ASTそのものに従う。
- Java意味論と同じく `|`・`&`・`^` は右辺も評価する eager 演算であり、`if`・ternary・`match` は選択された値だけを評価する。f32比較は `Float.compare` のNaN・signed-zero順序に一致する。
- `evaluate` では import、宣言、method、変数、組み込み/外部関数、slice、objectは扱わない（文脈依存のdocument要素は `context_required`、その他のtyped AST nodeは `unsupported_node` として明示的に失敗する）。これらを含む Java と同じ意味論は下の「文脈つき runtime」（`tinyexpression_rs::runtime`）が持つ。
- 型hint付きvariableはhintを保持し、number/string/boolean/objectの結果familyを選択する。directな`match`のcase/defaultで異なるhint familyを混在させた場合と、対応するmatch familyがないobject-result matchは型エラーになる。
- `tests/fixtures/numeric-f32.tsv` は独立した期待 f32 bits を保持し、Rust library/CLIとJava `P4TypedAstEvaluator` の `p4-typed` runtimeが共有する。
- `tests/fixtures/scalar-control.tsv` はboolean/string/比較/制御構文について、値・評価順序・Java/Rust parityを共有する。
- `tests/fixtures/root-expression.tsv` はroot dispatchの全文消費、Java/Rustそれぞれの厳密なsemantic root、必要な子nodeを共有検証する。
- `javacodeblock` の内容を実行しない。`rustcodeblock` は未実装で、将来も既定無効・明示許可付きとする。
- CI artifactはUbuntuのLinux x86_64用であり、完全static binaryや全OS対応を意味しない。

## 文脈つき runtime（issue #179）

`tinyexpression_rs::runtime` は Java の `P4_AST_EVALUATOR` 経路（`AstEvaluatorCalculator` → `P4TypedAstEvaluator`）と同じ意味論を持つ評価器である。通常依存ゼロのまま。`#![forbid(unsafe_code)]` は vendored parser だけでなく crate ルート（`lib.rs`）にも付け、runtime を含む全体に効かせた。

```rust
use tinyexpression_rs::runtime::*;

let program = Program::new("if($country=='jp'){$price*2}else{0}", Options::new(ResultType::Float))?;
let mut context = Context::new();
context.set_float("price", 10.0);
context.set_string("country", "jp");
let mut external = NoExternals;
let mut random = XorShiftRandom::default();
let mut host = Host { external: &mut external, clock: &ContextClock, random: &mut random };
let tree = calculator_result(program.eval_tree(&mut context, &mut host));      // 木の解釈
let compiled = program.compile();                                             // 1 回だけ closure 化
let closure = calculator_result(compiled.eval(&mut context, &mut host));      // 以後はこちら
```

- `Program::new(source, Options)` が parse と**評価根の選択**を行う（Java の calculator 構築に当たる）。候補順・全文被覆・型ヒントによる族の選び直しは `P4PreferredAstMapper.parseByAstSimpleNamesDetailed`、match の直書き変数/メソッドの拒否は `P4StrictMatchTypingValidator` をそのまま移した。ここでの拒否は `ErrorKind::Parse`（Java の `ParseException`）。
- `eval_tree` は `P4TypedAstEvaluator` をメソッド単位で写した木の解釈、`compile()` → `Compiled::eval` は同じ AST を一度だけ closure の木へ下ろしたもの。演算子・数値リテラル（`numberType` での解釈）・slice 添字・import 解決・宣言型・メソッド名の解決をコンパイル時に済ませ、Java が評価時に投げる例外は評価時に同じ種別で返す。
- 戻り値は評価器レベル（`Value::Null` は Java の `null`）。`calculator_result` を通すと `AstEvaluatorCalculator.apply` と同じく `null` 結果と `IllegalArgumentException`/`UnsupportedOperationException` が `UnsupportedOperationException` になる。
- `Value` は Java のボックス型に揃えた: `Number`(=`Float`)・`Double`・`Int`・`Long`・`Short`・`Byte`・`Boolean`・`String`・`Null`・`Object`（ホストの不透明値）。数値は `f32_bits`/`to_bits` で Java とビット比較できる。

### ホスト trait

| trait | 役割 | 既定 |
|---|---|---|
| `ExternalHost` | `external ...` 呼び出し。import/alias 解決後のクラス名・メソッド名と評価済み引数を受け、`Variables`（呼び出し時点のスコープ）を読める。`class_exists` は `Class.forName` に相当し、偽なら引数評価の前に失敗する | `NoExternals`: クラスは存在するが実体が登録されていない扱い → Java と同じ `CalculationException` |
| `Clock` | `inTimeRange` / `inDayTimeRange` の現在時刻。`now_hour` はスコープ付き変数、`now_day_and_hour` は基底 context を読む（Java の `EmbeddedFunction.inTimeRange` と `AbstractCalculationContext.inDayTimeRange` の違いをそのまま保つ） | `ContextClock`: Java と同じく変数 `nowHour` / `nowDayOfWeek` を読む。無ければ範囲外 |
| `RandomSource` | `random()`（Java は `Math.random()`） | `XorShiftRandom`（seed 指定で決定的） |

`ExternalError` の 4 種はそれぞれ Java の例外に対応する: `ClassNotFound`/`MethodNotFound`/`Failed` → `UnsupportedOperationException`、`NotRegistered` → `CalculationException`。Java はリフレクションで「引数個数が合う最初の public メソッド」を選び、引数を `convertToParamType` で変換する。この選択と変換はホスト実装の責務である（`tests/java_differential.rs` の `TestHost` が `Fee`・`TestSideEffector`・FormulaInfo の `CheckDigits`/`CheckAlphabets` を Java と同じ変換で実装している）。

### 対応表（段階 1 棚卸しの各行）

| 棚卸しの行 | 状態 | 備考 |
|---|---|---|
| 数値リテラル・四則演算 | 実装 | `numberType` が float / double / int / long / short / byte のそれぞれで Java と同じ演算（整数は wrap、0 除算は `ArithmeticException`）。リテラルは `Float.parseFloat` 等の文法で解釈 |
| 〃 `BigDecimal` / `BigInteger` | **対象外** | 多倍長演算の依存が要り依存ゼロ方針と衝突する。`NumberType` に variant を持たない |
| 真偽値・`not`・`|` `&` `^`・equality | 実装 | eager 評価、`toBoolean` は `"true"` 文字列比較 |
| 文字列・連結 | 実装 | Java の `unquoteStringLiteral`（二重引用符は引用符付きで AST に来る） |
| 比較演算子 | 実装 | 同型は `Float.compare`/`Double.compare`/整数比較、混在は `Double.compare` |
| 変数参照・スコープ | 実装 | `Context` は Java の 4 map（number/string/boolean/object）。型指定なしは結果型の map → 全 map の順。宣言とメソッド引数は calculation-local な frame（`ScopedCalculationContext`）で、host の `Context` へ漏れない |
| `set` / `set if not exists` | 実装 | `isExists` は frame と基底の全 map |
| メソッド宣言・呼び出し | 実装 | 引数個数不一致・未定義は `UnsupportedOperationException`。引数は `coerceToType` |
| 〃 再帰の深さ | **差異** | Java は thread stack 枯渇で `StackOverflowError`。Rust は `Options::max_call_depth`（既定 256）で同じ種別を返す。無限再帰は一致、256 段を超える有限再帰は Rust だけ失敗する |
| `ExternalInvocation` | 実装（host 経由） | 上記 `ExternalHost` |
| `match` | 実装 | case が真でも値が `null` なら次の case へ進む Java の挙動も同じ |
| slice | 実装 | UTF-16 code unit 単位（負 index・逆順 step・範囲外 `StringIndexOutOfBoundsException`・step 0 は `IllegalArgumentException`） |
| 〃 孤立サロゲート | **差異** | Java の結果文字列は孤立サロゲートを持てるが Rust の `String` は持てないので U+FFFD に置き換える（golden も同じ置換で比較） |
| 文字列関数 | 実装 | `trim` は `<= ' '`、`strip` は `Character.isWhitespace`、長さは UTF-16 |
| 〃 `toUpperCase`/`toLowerCase` のロケール | **差異** | Java は既定ロケール依存（トルコ語ロケールの i/İ など）。Rust は非トルコ系ロケールの Unicode 大小変換に固定 |
| 文字列述語 | 実装 | パターンは先頭から評価し、一致した時点で残りを評価しない（Java の `anyMatch`） |
| 数学関数 | 実装 | `Math.min/max/round/pow` の NaN・符号付きゼロ・特例を再現、度/ラジアンは `Context::with_angle`。超越関数は Rust の libm で、corpus 上は Java と全件ビット一致 |
| `random()` | 実装（host 経由） | 値は比較不能なので差分テストは型だけ比べる |
| `isPresent` | 実装 | |
| `inTimeRange` / `inDayTimeRange` | 実装（host 経由） | 上記 `Clock`。不正な曜日名は `IllegalArgumentException` |
| `toNum` | 実装 | `Double.parseDouble` の文法（前後空白・`f`/`d` 接尾辞・`NaN`/`Infinity`） |
| 〃 16 進浮動小数・非 ASCII 数字 | **差異** | `0x1p3` 形式と `Integer.parseInt` が受ける非 ASCII 数字は受けない（`NumberFormatException` 扱い） |
| エラー種別 | 実装 | `ErrorKind` は Java の例外クラス（`java_name()`）。`calculator_result` で calculator 段の包み直しも再現 |
| null / undefined | 実装 | 未設定変数は算術で `numberType` の 0、文字列連結で `""`、`String.valueOf(null)` の `"null"` なども Java どおり |
| `FormulaInfo` の追加フィールド | **対象外（#180）** | loader は段階 4。差分テストは `resultType` / `numberType` だけを使う |
| 解析期限（`parse.timeout` / `probe.timeout`） | **対象外** | ubnfc parser は指数バックトラックを起こさないので期限を持たない |

### Java との差分テスト

`tests/java_differential.rs` が恒久ゲート。`tests/java-diff/golden/` に Java 側の結果を commit してあり、`cargo test` は JVM を要らない。

- コーパス（`tests/java-diff/build_corpus.py`）: Java `src/test/**` と Rust テストの文字列 literal、`tests/fixtures/*.tsv`・`*.tiny`、`benchmarks/fixtures/*.tiny`、parity コーパス、FormulaInfo fixture（`formulaInfo.fi`・`formulaInfo-test/*`、`resultType`/`numberType` つき）、`tests/java-diff/extra-formulas.tsv`（棚卸し行ごとの境界値）。結果型は既知ならそれ、不明なら float / boolean / string / object の 4 通り。`numeric-f32.tsv` と parity コーパスの数値式は double / int / long / short / byte の `numberType` でも回す。
- 変数を含む式は 7 つの context で回す: 空、全部 number、全部 string、全部 boolean、3 map 同時、object map（Integer/Long/String/Boolean）、Java テストコードの `set("x", …)` literal を集めたもの（`nowHour`/`nowDayOfWeek` も）。空以外では external のテスト用クラスを登録する。
- 比較: number は型と bit（float は `f32` bits、double は `f64` bits、整数は値）、boolean/string は値、エラーは Java の例外クラスと段（calculator 構築時か評価時か）。
- 同じ全行で木の解釈と closure の結果（エラーメッセージまで）が一致することも検査する。

結果（2026-09-24）: **953 式・12,609 行で Java と全件一致、説明のない差 0**。documented deviation は `random()` を含む 12 行（Java は `Math.random()` なので golden は型だけを持ち、型だけ比較）のみ。木の解釈と closure は 12,609 行すべてで一致。Java 側で全結果型が構築時に拒否し Rust の parser も拒否する 1,466 literal（式でない文字列）は golden に入れず件数だけ `rejected-count.txt` に残す。Rust だけ拒否・Java だけ受理の式は 0。

golden の作り直し（JDK 21・Maven・python3・cargo が要る。Java の解析期限は外して回す）:

```sh
bash rust/tinyexpression-rs/tests/java-diff/regenerate-java-golden.sh           # golden を書き換える
bash rust/tinyexpression-rs/tests/java-diff/regenerate-java-golden.sh --check   # 作り直して差分だけ見る
MVN_ARGS=-o bash rust/tinyexpression-rs/tests/java-diff/regenerate-java-golden.sh  # オフライン解決
```

`cargo test` は `[profile.test] opt-level = 1` で走らせる（`rust/Cargo.toml`）。vendored parser の native stack 番兵（呼び出し thread 上 256 KiB）は最適化後の frame を前提にしており、debug のままだと fraud-alert の深い式が `maximum parse depth exceeded` で落ちるため。debug assertion と overflow 検査は残る。

### 速度（2026-09-24、同一ホストで Rust と Java を交互に 3 ラウンド）

16 の P4 fixture（`benchmarks/fixtures/*.tiny` + `tests/fixtures/*.tiny`、ubnfc `examples/p4-java` の fixture と byte 一致）と fraud-alert 5 式（`tests/fixtures/fraud-alert.tsv`、`P4PackratFraudFormulaTest` と同じ）を、結果型 float・空の context で測った。WSL2 x86_64、計測中の load average は 8〜15（他作業と共有）。各セルは「3 ラウンドの中央値の中央値 / 全ラウンドの最小値」。

- Rust: `examples/eval-bench.rs`（parse+選択は `Program::new`、compile は `Program::compile`、木・closure は 1 評価あたり µs、2,000 評価の平均を 9 回）。
- Java 段別: ubnfc `examples/p4-java-facade` の `StageTimingRunner`（te-facade 報告 §5 と同じドライバ）を本ブランチの `target/classes` に対して実行。parse / Java 生成 / javac は ms、実行は bytecode の µs/回。
- Java P4AST: `P4_AST_EVALUATOR` の calculator 構築（parse + 根選択）と `apply`（Java の木の解釈）。

| 入力 | 文字 | Rust parse+選択 ms | Rust compile ms | Rust 木 µs | Rust closure µs | Java P4AST 構築 ms | Java P4AST apply µs | Java parse ms | Java 生成 ms | Java javac ms | Java 実行 µs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| complex | 325 | 0.237 / 0.207 | 0.003 | 1.912 / 1.531 | 0.955 / 0.779 | 62.84 / 46.70 | 17.89 / 14.24 | 29.4 / 22.4 | 6.5 | 38.8 / 34.1 | 0.196 / 0.174 |
| complex-x4 | 1,293 | 0.843 / 0.762 | 0.011 | 6.591 / 6.207 | 3.545 / 3.372 | 285.34 / 152.39 | 134.54 / 54.96 | 104.7 / 78.6 | 11.6 | 61.4 / 39.6 | 3.577 / 0.881 |
| complex-x16 | 5,179 | 3.097 / 2.886 | 0.062 | 30.24 / 26.12 | 15.01 / 14.12 | 820.18 / 646.79 | 280.16 / 222.63 | 455.8 / 410.5 | 300.6 | 211.2 / 72.2 | 9.689 / 9.665 |
| complex-x64 | 20,923 | 15.09 / 12.15 | 0.331 | 122.2 / 109.1 | 61.13 / 57.58 | 7,685.7 / 3,726.6 | 1,576.2 / 1,040.0 | 2,777.8 / 1,833.6 | 0.0 | 84.7 / 64.3 | 27.35 / 21.99 |
| flat-arithmetic | 1,426 | 0.480 / 0.311 | 0.033 | 15.91 / 12.88 | 5.981 / 5.679 | 196.75 / 94.81 | 10.74 / 6.28 | 46.9 / 45.0 | 1.0 | 33.5 / 33.1 | 0.025 / 0.017 |
| large-match | 1,380 | 1.261 / 1.184 | 0.015 | 4.313 / 4.095 | 2.123 / 2.051 | 426.21 / 227.59 | 85.17 / 76.12 | 152.9 / 90.8 | 0.0 | 34.2 / 32.3 | 1.588 / 1.527 |
| valid-basic | 6 | 0.010 / 0.009 | 0.000 | 0.125 / 0.106 | 0.045 / 0.042 | 8.79 / 7.37 | 0.14 / 0.13 | 4.0 / 3.8 | 0.3 | 33.0 / 28.6 | 0.023 / 0.021 |
| valid-multiline | 40 | 0.016 / 0.015 | 0.000 | 0.311 / 0.284 | 0.144 / 0.137 | 9.06 / 8.60 | 1.61 / 1.57 | 4.3 / 4.1 | 0.1 | 31.1 / 30.0 | 0.062 / 0.054 |
| valid-unicode | 9 | 0.009 / 0.008 | 0.000 | 0.069 / 0.066 | 0.040 / 0.033 | 9.09 / 8.58 | 1.14 / 1.02 | 4.5 / 4.2 | 1.0 | javac で型エラー | — |
| fraud#1 | 404 | 0.172 / 0.148 | 0.002 | 1.796 / 1.470 | 0.967 / 0.869 | 492.07 / 254.62 | 64.60 / 35.05 | 246.4 / 123.2 | 0.0 | 38.7 / 29.6 | 0.299 / 0.293 |
| fraud#2 | 209 | 0.047 / 0.045 | 0.001 | 0.414 / 0.340 | 0.192 / 0.165 | 25.89 / 14.23 | 14.54 / 9.20 | 8.4 / 7.6 | 0.0 | 107.3 / 48.1 | 0.105 / 0.087 |
| fraud#3 | 68 | 0.037 / 0.022 | 0.000 | 0.292 / 0.190 | 0.083 / 0.083 | 7.97 / 3.80 | 6.71 / 3.99 | 3.9 / 3.7 | 0.6 | 51.0 / 43.6 | 0.086 / 0.050 |
| fraud#4 | 707 | 0.175 / 0.148 | 0.002 | 1.503 / 1.396 | 0.785 / 0.751 | 684.58 / 277.69 | 67.69 / 35.32 | 237.3 / 139.1 | 3.3 | 81.7 / 38.3 | 0.305 / 0.304 |
| fraud#5 | 459 | 0.133 / 0.119 | 0.002 | 0.099 / 0.098 | 0.041 / 0.030 | 4,506.7 / 3,257.4 | 3.88 / 3.63 | 1,886.4 / 1,833.6 | 0.0 | 50.0 / 45.8 | 0.058 / 0.055 |

残り 7 fixture（comparison-heavy 系 4・complex-half・complex-tail・invalid-syntax）は結果型 float では Rust・Java とも構築時に拒否（te-facade 報告と同じ）。「生成」は `tryEmit` 全体から parse を引いた差で雑音に弱い。fraud-alert 5 式の `FormulaInfo` 文書を `FormulaInfoList.parse` で読み込み〜実行可能まで通した Java の時間は 3 ラウンドで 7,116 / 3,549 / 3,654 ms。

読み方:

- **最初の結果までの時間**（parse + compile + 1 評価）は Rust が桁で速い: complex 0.24 ms 対 Java bytecode 経路（parse+生成+javac）74.7 ms、fraud#5 0.14 ms 対 1,936 ms、complex-x64 15.5 ms 対 2,863 ms。Java の木の解釈の構築（P4AST 構築）と比べても約 200〜34,000 倍。
- **closure は木の解釈の 1.7〜3.5 倍速い**（complex 1.91 → 0.96 µs、flat-arithmetic 15.9 → 6.0 µs）。Java の木の解釈（P4AST apply）と比べると closure は 1.8〜95 倍速い。
- **1 評価あたりでは javac 済み bytecode（JIT 後）が closure より速い式がある**: complex 0.196 対 0.955 µs、flat-arithmetic 0.025 対 5.98 µs（literal だけの算術を JIT が畳む）。complex で損益分岐は約 10 万回評価。同じ式を何十万回も評価し続ける用途では Java bytecode 経路にまだ利がある。
- 計測値は共有ホストの負荷で揺れる（Java 側の中央値と最小値の開きが大きい）。桁の比較として読むこと。


## Parser（ubnfc の vendoring、issue #178）

| パス | 中身 |
|---|---|
| `src/generated/ubnfc/` | `ubnfc ir` → `ubnfc-rust --no-emit-driver` の出力。`#![forbid(unsafe_code)]`、std のみ |
| `src/generated/ubnfc/scanners.rs`, `scanners_tests.rs` | ubnfc `scanners/rust/` の extern token scanner（STRING / CODE_START / CODE_END）とその単体テスト |
| `src/generated/compat.rs` | ubnfc AST → 公開 `Ast` の変換。`rust/scripts/generate-compat.py` が両 `ast.rs` から生成 |
| `src/generated/ast.rs`, `evaluator.rs` | 公開 typed AST と `Semantics` trait。旧 unlaxer 生成物を引き継ぎ、以後は手で保守（node 集合は文法由来なので、文法を変えたら `generate-compat.py` が不一致で止まる） |
| `src/generated/ubnfc_formula_info/` | `grammar/formula-info.ubnf`（FormulaInfo 文書、issue #180）の `ubnfc ir` → `ubnfc-rust --no-emit-driver` の出力。extern token が無いので変更は (1) だけ。manifest は `rust/ubnfc-formula-info-vendored.sha256` |
| `rust/ubnfc-pin.txt` | ubnfc commit（2 文法共通）、文法・IR・scanner の SHA-256 |

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

## FormulaInfo loader（issue #180）

`grammar/formula-info.ubnf` は Java loader（`org.unlaxer.tinyexpression.loader.FormulaInfo*Parser`）が読むブロック形式を UBNF v2 で書いた文法で、ubnfc の Rust backend 生成物を `src/generated/ubnfc_formula_info/` に vendoring している。`tinyexpression_rs::formula_info` がその typed AST を薄く変換する。

```sh
cargo run --locked --manifest-path rust/Cargo.toml -p tinyexpression-rs -- load formulaInfo.txt
cargo run --locked --manifest-path rust/Cargo.toml -p tinyexpression-rs -- run --default-backend P4_AST_EVALUATOR formulaInfo.txt
```

- `parse_document`（構文層）: ブロック分割、`key:value` の切り出し、`---END_OF_PART---`。受理範囲は `FormulaInfoSourceDocument.parse`（Java の `FormulaInfoBlocksParser` を全文消費で走らせたもの）と一致する。
- `load`（変換層）: Java の `FormulaInfoParser.extractFormulaInfo` の後処理を同じ順序で行う。値の正規化（`stripTrailing`、`#` 行・空行の除去）、既知キーの写像、`executionBackend`/`backend` の解決、`hash` の MD5 更新（大文字 hex）、`Formula_<name>` のクラス名、formula 必須検査、`Program::new` による式の構築（Java の calculator 構築に当たる）、最後に `dependsOn` の配線。`LoadError::java_exception` は同じ文書で Java が投げる例外名を返す。
- `load`/`run` の CLI は Java loader テストと同じ設定（`siteId` を multi-tenancy 属性、`checkKind` があればそれ・無ければ `calculatorName` を名前）で読み、`run` は各式を空の context で 1 回評価する（external は未登録扱い）。
- 保持しないもの: `javaCode`・`byteCode`・`byteCode_<class>`・`hashByByteCode`。Java も load のたびに式から作り直し、保存値を実行しない。`byteCode` 系は Java と同じく hex として検査だけする。

### Java loader とのパリティ（`tests/formula_info.rs`）

`tests/formula-info/golden/java.jsonl` は Java loader が各 fixture（`src/test/resources/formulaInfo.fi`、`formulaInfo-test/*/formulaInfo.txt`、`formulaInfo-ubnf/*.fi` の受理・拒否ケース）から作るものを記録した golden で、`tests/formula-info/regenerate-formula-info-golden.sh`（JDK と Maven が要る）が作る。`cargo test` は JVM 不要でこれと突き合わせる: 構文の受理、各 entry の key・生の値・正規化後の値、読み込んだ全フィールド、load エラーの Java 例外名、各式の評価結果（P4_AST_EVALUATOR）。`rust/scripts/crosscheck-formula-info.sh` は ubnfc の Java backend も生成して、Rust/Java 生成物の canonical AST が全 fixture で一致することを確かめる（生成した Java は commit しない）。

| 項目 | 差 | 理由 |
|---|---|---|
| `resultType`/`numberType` の未知のクラス名 | Java は `Class.forName` で class path 上の任意のクラスを読む。Rust は loader の名前表と `java.lang.*`/`java.math.BigDecimal`/`java.math.BigInteger`/`java.sql.Timestamp` だけ | JVM が無い |
| BigDecimal / BigInteger / Timestamp | Rust は `LoadError::UnsupportedType` | runtime が扱わない（依存ゼロ方針、#179） |
| 式の構築 | Rust は常に P4 の意味論（`Program::new`）。Java はブロックの `executionBackend` の calculator で構築する | Rust の評価器は P4_AST_EVALUATOR だけ。golden は既定 backend を P4_AST_EVALUATOR にして採っている |

かつて差だった 3 件（issue #195 で解消）: 全文を消費できない文書、`key:` が入力末尾で値が 0 文字、未知の `dependsOn`。いずれも Java は以前は黙って通す／JDK の生の例外（`NoSuchElementException`・`NullPointerException`）を投げるだけだったが、いまは明示的な `FormulaInfoParseException` を投げ、`LoadError::java_exception()` もこれに合わせて `FormulaInfoParseException` を返す。

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
