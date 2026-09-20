# tinyexpression-rs native frontend and evaluator

最新の `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` から生成した、JVM不要の parser・typed AST・mapper と、context-free scalar evaluator/CLI である。

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

- unlaxer-parser `eb8697b475383fa80e6731e28a0cc35152cf89e6` のruntimeとgeneratorを固定している。生成mapperはrule別のbounded-size関数へ分割され、debug/test buildでも2 MiB thread stack上のP4 parse+mappingを検証する。Rust frontendのprimary parse、result-family reparse、alternate-root reparseは、状態依存規則を除外するfailure-only memoizationを明示的に有効化する。
- Java P4と同じUBNFをsource of truthとし、生成5ファイルはCIでdrift検査する。
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
