# tinyexpression-rs native frontend and evaluator

最新の `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` から生成した、JVM不要の parser・typed AST・mapper と、context-free f32 evaluator/CLI である。

```sh
printf '%s' '1 + 2' | cargo run --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs -- parse -

cargo run --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs -- parse formula.tiny

printf '%s' '(1 + 2) * 3' | cargo run --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs -- eval -
```

`parse` の成功時は `{"ok":true,"ast":...}`、`eval` の成功時は f32 の値と raw bits を含む `{"ok":true,"value":{"kind":"number",...}}` を出力する。構文エラーは `{"ok":false,"stage":"parse","diagnostic":...}`、未対応 AST は source span 付きの `unsupported_node` 評価エラーになる。ASTはcapture文字列とspanを所有するためparse treeを破棄できるが、入力全文は保持しない。公開offsetはUnicode code point単位である。

library API は `parse(&str)` に加えて `evaluate(&str) -> Result<Value, EvaluationError>` と `evaluate_ast(&Ast)` を公開する。`Value::Number(f32)` の `f32_bits()` で Java float とのビット単位の比較ができる。

| exit code | 意味 |
|---:|---|
| 0 | parse・mapping・evaluation成功 |
| 2 | CLI引数不正 |
| 3 | parse失敗（trailing inputを含む） |
| 4 | mapping失敗 |
| 5 | evaluation失敗（未対応node・context依存構文を含む） |
| 6 | I/O失敗 |

## 保証範囲

- unlaxer-parser `3c38c96a08aa452f5b50f8682fe04f6409f1008c` のruntimeとgeneratorを固定している。
- Java P4と同じUBNFをsource of truthとし、生成5ファイルはCIでdrift検査する。
- parserとevaluatorはJava・手書きparser・別評価器へfallbackしない。
- evaluatorの対応範囲は、状態を必要としない f32 数値literal（負数を含む）、四則演算、括弧、空白・commentである。生成 `FormulaExpr` → `ExpressionExpr` → `BinaryExpr` / `AstValue` のみを評価するため、優先順位と左結合はP4 typed ASTそのものに従う。
- import、宣言、method、変数、関数、boolean/string/objectなどは未対応である。文脈依存のdocument要素は `context_required`、その他のtyped AST nodeは `unsupported_node` として明示的に失敗する。
- `tests/fixtures/numeric-f32.tsv` は独立した期待 f32 bits を保持し、Rust library/CLIとJava `P4TypedAstEvaluator` の `p4-typed` runtimeが共有する。
- `javacodeblock` の内容を実行しない。`rustcodeblock` は未実装で、将来も既定無効・明示許可付きとする。
- CI artifactはUbuntuのLinux x86_64用であり、完全static binaryや全OS対応を意味しない。
