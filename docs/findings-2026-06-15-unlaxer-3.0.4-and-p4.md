# P4 インタプリタ: 性能・網羅テスト・回帰知見 (2026-06-15、2026-08-27 更新)

この調査では P4 実装に対して、文法由来の網羅テスト、Java code バックエンドとの
パリティテスト、実利用式のパーステスト、手動性能ベンチマークを追加した。

## 現行設計への統合

当初は `AstEvaluatorCalculator` に型付き AST のインスタンスキャッシュを追加していた。
しかしその後、実行経路は UBNF 生成 AST と型付き評価器だけを使い、手書き評価器へ
fallback しない設計へ移行した。この統合では古い経路分岐を復活させず、キャッシュ実装も
取り込んでいない。性能改善を再導入する場合は、現行の単一実行経路上で parse/map と
実行時コンテキストのライフサイクルを分離して設計する。

`BackendSpeedBenchmarkTest` は assertion を持たない手動ベンチマークで、現在も
インタプリタの再 parse/map コストと Java コンパイルの初期コストを比較できる。

## 追加した回帰資産

- `GrammarCoverageInterpreterTest` — 算術、数学関数、boolean 優先順位、not、if、
  ternary、match、文字列、変数とコンテキスト更新を網羅する。
- `InterpreterJavacodeParityTest` — 両バックエンドの共通サブセットを比較する。
- `KnownP4BugsTest` — 過去の #21/#25 と unlaxer-parser#43 の再現を通常テストとして保持する。
- `UserFormulaParseTest` — import/external 呼び出しを含む実利用相当の式を検証する。
- `BackendSpeedBenchmarkTest` — `@Ignore` の手動性能ベンチマーク。

2026-08-27 の現行 `master` 取り込み後、通常テスト 29 件（上記 4 クラス）は
failure/error 0 で通過し、過去に ignore されていた 4 件も有効化して成功した。

## 過去に見つかった不具合

| issue | 症状 | 現在の扱い |
|---|---|---|
| tinyexpression#25 | top-level の `not(...)` が誤評価される | 修正済み。`standaloneNotReturnsFalse` で回帰検知 |
| tinyexpression#21 | legacy cross-check が variadic min/max や boolean 優先順位を上書きする | fallback/cross-check を除去済み。2 テストで回帰検知 |
| unlaxer-parser#43 | 数学関数を含む二項式の生成 AST 型が狭い | 修正済み。`functionTermArithmetic` で回帰検知 |

テストのプロセス共有状態については tinyexpression#27 で整理済みである。並列 fork を
変更する場合は、`JavaCodeBlockPolicy` などの静的状態がテストごとに復元されることを
引き続き確認する。
