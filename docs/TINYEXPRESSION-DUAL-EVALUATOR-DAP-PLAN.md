# TinyExpression Dual Evaluator + Dual DAP Plan

## Goal

最終目標は次の 3 経路を同時に維持すること。

1. 既存: TinyExpression の Java code generation 実行経路
2. 新規: UnlaxerDSL で生成した AST を走査する Evaluator 実行経路
3. 新規: UnlaxerDSL 側の Java code generation 実行経路（現状は bridge 実装）

さらに DAP は既存 TinyExpression を温存したまま次の実行経路に attach できるようにする。

1. Java code generation 経路の DAP
2. AST Evaluator 経路の DAP
3. DSL JavaCode 経路の DAP（bridge -> native へ段階移行）

## Why dual-path

1. 既存本番互換を維持しながら段階移行できる
2. 同一入力で 3 経路の差分比較ができる
3. DAP 機能を段階的に強化しやすい

## Current status (2026-02-25)

1. UBNF 草案と parser-ir の検証は進行中
2. `unlaxer-dsl` 側は `MapperGenerator` の実装を開始（parse/dispatch/assoc mapping）
3. TinyExpression 側は java code generation 経路が主経路のまま

## Required implementation slices

1. Mapper 実装を安定化（UBNF -> AST）
2. AST Evaluator 実行クラスを tinyexpression 側へ接続
3. Java code generation 実行器と AST Evaluator 実行器を同一 API で選択可能化
4. DAP launcher を runtime mode 引数で分岐可能にする
5. `ExecutionBackend` を 3 経路（`JAVA_CODE` / `AST_EVALUATOR` / `DSL_JAVA_CODE`）で運用する

## DAP dual connection model

1. `mode=javacode`: 既存 JavaCodeCalculator 系のステップ/変数表示
2. `mode=ast`: generated Evaluator 系の AST ノードステップ/評価値表示
3. `mode=dsl-javacode`: DSL JavaCode backend のステップ/変数表示（初期は legacy bridge）

### Current implementation note

`unlaxer-dsl` の `DAPGenerator` には `runtimeMode` launch 引数を追加済み。

1. `runtimeMode=token` (default)
2. `runtimeMode=ast` / `ast_evaluator`

現時点の `ast` は token stepping fallback 実装で、
後続で mapper/evaluator runtime 接続時に AST ノードステップへ差し替える。

また、tinyexpression 側の `AstEvaluatorCalculator` は generated mapper runtime が存在する場合に
`parse -> mapper.parse()` を probe し、次を context object に記録する。

1. `_astEvaluatorMapperAvailable` (boolean)
2. `_astEvaluatorMappedAst` (mapped AST object, present when mapping succeeds)

## Verification policy

1. 同じ式を 3 経路で実行し、結果一致を検証する
2. 生成 Java ソース差分はスナップショット化して比較する
3. DAP は stopOnEntry / next / continue / variables / stackTrace を各経路で確認する

## Dependency-note rule

`unlaxer-dsl` / `unlaxer-common` へ変更を加えた場合は、
`docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md` に

1. 変更対象
2. 目的
3. 互換性影響
4. TinyExpression 側で必要な追従

を記録する。
