# TinyExpression UnlaxerDSL 実装ハンドブック

このドキュメントは、TinyExpression を `unlaxer-dsl` ベースへ移行・拡張する時の最短導線をまとめる。

## 1. 全体構造

主要レイヤ:

1. Grammar (`docs/ubnf/*.ubnf`)
2. Codegen (`unlaxer-dsl` の `AST/Parser/Mapper/Evaluator/LSP/DAP` generator)
3. Runtime (`tinyexpression` の `AstEvaluatorCalculator` / `JavaCodeCalculatorV3`)
4. Integration (`FormulaInfoParser` / `CalculatorCreatorRegistry` / backend switch)

実装上の基本方針:

1. `JAVA_CODE` と `AST_EVALUATOR` の dual backend を維持する
2. `AST_EVALUATOR` は `generated-ast -> token-ast -> javacode-fallback` の順で実行
3. 生成物は `runtime` と `tooling` に分離し、通常 compile には `runtime` のみを入れる

## 2. TinyExpression から UnlaxerDSL へ変換する方法

手順:

1. 既存 TinyExpression 構文を UBNF rule に写像する  
   例: `NumberExpression`, `NumberTerm`, `NumberFactor`
2. AST化したい rule に `@mapping(...)` を付与
3. 演算子優先順位が必要なら `@leftAssoc/@rightAssoc + @precedence` を付与
4. スコープ/参照制約が必要なら `@scopeTree`, `@backref` を付与
5. `scripts/generate_tinyexpression_p4_from_ubnf.sh` で再生成

## 3. 新しい型を定義する方法

チェックリスト:

1. UBNF 側に型ごとの declaration/expression rule を追加
2. TinyExpression parser 側に type hint / variable / method parameter parser を追加
3. `VariableTypeResolver` と builder/evaluator で型解決・実行を追加
4. `AstEvaluatorCalculator` の AST path で必要なら評価器を拡張

推奨実装順:

1. parse可能化
2. codegen可能化
3. runtime評価可能化
4. DAP/LSP表示確認

## 4. 新しい関数を定義する方法

手順:

1. parser rule 追加（例: `AbsParser` 相当）
2. `OperatorOperandTreeCreator` の再構築ロジック追加
3. JavaCode builder と AST evaluator 両方に評価ロジックを追加
4. UBNF にも rule 追加し、`@mapping` との整合を確認

## 5. ASTMapper で AST を作る方法

前提:

1. 対象 rule に `@mapping(ClassName, params=[...])`
2. capture 名 (`@left`, `@op`, `@right` など) を params と一致させる

出力:

1. `...AST.java`（sealed interface + record）
2. `...Mapper.java`（Token parse tree -> AST）

注意:

1. 末端ノードが非mappedの時は mapper 側 leaf fallback が必要
2. Token API 差分を吸収するため、生成 mapper は compatibility helper を使う

## 6. AST から実行可能状態へ変換する方法

TinyExpression では次の3段階で実行:

1. `GeneratedAstRuntimeProbe.tryMapAst(...)` で generated mapper 実行
2. `GeneratedP4NumberAstEvaluator` で generated AST を直接評価（対応範囲）
3. 数値 leaf が `$name` の場合は `CalculationContext` から数値を解決して評価
4. P4 draft では `StringExpr` / `BooleanExpr` / `ObjectExpr` / `VariableRefExpr` の mapping を追加済み（評価器側は段階実装中）
3. 非対応時に `AstNumberExpressionEvaluator`（token-ast）または `JavaCodeCalculatorV3` へfallback

要点:

1. fallback がある限り段階移行できる
2. 対応式を増やすと `javacode-fallback` 使用率が下がる

## 7. LSP / DAP と接続する方法

### LSP

1. `LSPGenerator` が `...LanguageServer` を生成
2. parse結果・診断公開は generated server 側
3. runtime接続を深める場合は AST情報を hover/semantic token に反映

### DAP

1. `DAPGenerator` が `...DebugAdapter` を生成
2. launch引数 `runtimeMode` (`token` / `ast`) を受ける
3. `ast` モードでは mapper AST の可視化情報（`astNodeCount`, `astCurrentNode`）を variables に出す
4. `ast` モードでは stackTrace / breakpoint line 判定に mapper の AST node source span を利用
5. step順序自体は引き続き互換優先（AST node列挙 + token fallback 併用）

## 8. 依存拡張が必要になった時

記録先:

1. `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md`

運用:

1. 必要になったら先にノートへ記録
2. `unlaxer-dsl` / `unlaxer-common` を編集
3. tinyexpression 側再生成・compileで整合確認
