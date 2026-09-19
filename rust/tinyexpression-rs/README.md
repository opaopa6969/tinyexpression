# tinyexpression-rs parser frontend

最新の `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` から生成した、JVM不要の parser・typed AST・mapper と CLI である。

```sh
printf '%s' '1 + 2' | cargo run --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs -- parse -

cargo run --locked --manifest-path rust/Cargo.toml \
  -p tinyexpression-rs -- parse formula.tiny
```

成功時は `{"ok":true,"ast":...}`、構文エラー時は `{"ok":false,"stage":"parse","diagnostic":...}` を出力する。ASTはcapture文字列とspanを所有するためparse treeを破棄できるが、入力全文は保持しない。公開offsetはUnicode code point単位である。

| exit code | 意味 |
|---:|---|
| 0 | parse・mapping成功 |
| 2 | CLI引数不正 |
| 3 | parse失敗（trailing inputを含む） |
| 4 | mappingまたはI/O失敗 |

## 保証範囲

- unlaxer-parser `77b3ca1b59f6e50d31c455a8a8c9930bc8fce3d5` のruntimeとgeneratorを固定している。
- Java P4と同じUBNFをsource of truthとし、生成5ファイルはCIでdrift検査する。
- parserはJava・手書きparser・評価器へfallbackしない。
- 現時点では式を評価しない。`evaluator.rs` は網羅的なsemantics trait/APIだけを生成している。
- `javacodeblock` の内容を実行しない。`rustcodeblock` は未実装で、将来も既定無効・明示許可付きとする。
- CI artifactはUbuntuのLinux x86_64用であり、完全static binaryや全OS対応を意味しない。
