# TinyExpression UnlaxerDSL 実装ハンドブック

このドキュメントは、TinyExpression を `unlaxer-dsl` ベースへ移行・拡張する時の最短導線をまとめる。

確認済みベースライン（2026-04-24）:

1. `tinyexpression` `1.4.15`
2. `unlaxer-common` `3.0.14`
3. `unlaxer-dsl` `3.0.14`
4. build grammar: `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf`
5. snapshot grammar: `docs/ubnf/tinyexpression-p4-complete.ubnf`

## 1. 全体構造

主要レイヤ:

1. Grammar (`docs/ubnf/*.ubnf`, `tools/tinyexpression-p4-lsp-vscode/grammar/*.ubnf`)
2. Codegen (`unlaxer-dsl` の `AST/Parser/Mapper/Evaluator/LSP/DAP` generator)
3. Runtime (`tinyexpression` の
   `AstEvaluatorCalculator` /
   `JavaCodeCalculatorV3` /
   `org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyAstCreatorJavaCodeCalculator`)
4. Integration (`FormulaInfoParser` / `CalculatorCreatorRegistry` / backend switch)

実装上の基本方針:

1. backend を6系統で維持する:
   - `JAVA_CODE`
   - `JAVA_CODE_LEGACY_ASTCREATOR`
   - `AST_EVALUATOR`
   - `DSL_JAVA_CODE`
   - `P4_AST_EVALUATOR`
   - `P4_DSL_JAVA_CODE`
2. `AST_EVALUATOR` / `P4_AST_EVALUATOR` は生成 P4 AST を `P4TypedAstEvaluator` でのみ実行し、未対応時は明示的に失敗する
3. legacy backend は明示的な比較・回帰確認用途として扱い、generated backend の暗黙 fallback には使わない
4. 生成物は `runtime` と `tooling` に分離し、通常 compile には `runtime` のみを入れる
5. mapper root 選択は `P4PreferredAstMapper` に寄せ、runtime / LSP / DAP で同じ preferred-root ルールを使う

## 2. TinyExpression から UnlaxerDSL へ変換する方法

手順:

1. 既存 TinyExpression 構文を UBNF rule に写像する  
   例: `NumberExpression`, `NumberTerm`, `NumberFactor`
2. AST化したい rule に `@mapping(...)` を付与
3. 演算子優先順位が必要なら `@leftAssoc/@rightAssoc + @precedence` を付与
4. スコープ/参照制約が必要なら `@scopeTree`, `@backref` を付与
5. `scripts/generate_tinyexpression_p4_from_ubnf.sh` で再生成

現行 P4 baseline で反映済みの代表機能:

1. CodeBlock
2. boolean equality
3. string dot method (`.length`, `.startsWith`, `.endsWith`, `.contains`, `.trim`, `.toUpperCase`, `.toLowerCase`, `.in`)
4. slice (`[::-1]`, `[::2]`, `[1::2]` を含む)
5. `isPresent(...)`
6. `inTimeRange(...)` / `inDayTimeRange(...)`
7. typed `if` / ternary
8. strict `match` typing

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

1. 末端ノードも必要な値を AST に保持するよう mapping を定義する。非mapped leaf を別 evaluator で補う実行時 fallback は設けない
2. Token API 差分を吸収するため、生成 mapper は compatibility helper を使う

## 6. AST から実行可能状態へ変換する方法

TinyExpression の generated backend は次の固定経路で実行する:

1. `P4PreferredAstMapper` / `GeneratedAstRuntimeProbe` が generated mapper で preferred root を選ぶ
2. `AST_EVALUATOR` / `P4_AST_EVALUATOR` は `P4TypedAstEvaluator` で generated AST を評価する
3. `DSL_JAVA_CODE` / `P4_DSL_JAVA_CODE` は `P4TypedJavaCodeEmitter` で generated AST から Java を生成する
4. 変数は `CalculationContext` から型付きで解決する
5. parse・mapping・typing・評価・emission の未対応箇所は明示的に失敗する

`JAVA_CODE` と `JAVA_CODE_LEGACY_ASTCREATOR` は比較用に明示選択できるが、generated backend から暗黙に切り替わることはない。

## 7. LSP / DAP と接続する方法

### LSP

1. `LSPGenerator` が `...LanguageServer` を生成
2. parse結果・診断公開は generated server 側
3. runtime接続を深める場合は AST情報を hover/semantic token に反映
4. 現行 P4 LSP では `TE001` に加えて strict match typing の `TE025` を手書き拡張で追加済み
5. hover / diagnostics は preferred root を使って `match` / `if` / ternary の shallow root 誤判定を避ける

### DAP

1. `DAPGenerator` が `...DebugAdapter` を生成
2. launch引数 `runtimeMode`（`p4-ast` / `p4-dsl-javacode`）と構造表示用 `steppingMode`（`ast` / `token`）を別々に受ける
3. `ast` モードでは mapper AST の可視化情報（`astNodeCount`, `astCurrentNode`）を variables に出す
4. `ast` モードでは stackTrace / breakpoint line 判定に mapper の AST node source span を利用
5. `steppingMode=ast` の mapping 失敗は明示終了し、token step へ暗黙 fallback しない
6. 現行 P4 DAP / runtime bridge は `_tinyP4ParserUsed`, `_tinyP4ParserExact`, `_tinyP4ParserProbeMode`,
   `_tinyP4AstNodeType`, `_tinyP4AstNodePath`, `parity.*` を公開する

## 8. 依存拡張が必要になった時

記録先:

1. `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md`

運用:

1. 必要になったら先にノートへ記録
2. `unlaxer-dsl` / `unlaxer-common` を編集
3. tinyexpression 側再生成・compileで整合確認

## 9. 最低限の再生成・確認コマンド

```bash
scripts/generate_tinyexpression_p4_from_ubnf.sh
mvn -q -DskipTests compile
mvn -q -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true package
mvn -P p4-smoke test
(cd tools/tinyexpression-p4-lsp-vscode && mvn -P p4-smoke test)
```

`p4-smoke` profile が gate する acceptance matrix:

| layer | 対象テスト |
|---|---|
| P4 typed AST | `P4TypedAstEvaluatorTest` |
| AST parity | `AstEvaluatorParityCorpusTest` / `AstEvaluatorBackendParityTest` |
| 3-backend parity | `ThreeExecutionBackendParityTest` |
| P4 backend parity | `P4BackendParityTest` |
| P4 facade precedence | `P4PreferredAstMapperPrecedenceTest` |
| LSP slice | `TinyExpressionP4LanguageServerExtTest` (LSP module) |

CI (`.github/workflows/ci.yml`) も同じ profile を `smoke` ジョブで回し、
green のときだけ `build` ジョブが `mvn verify` でフル検証する。
docs と CI が同じ smoke set を gate する状態を維持すること。

補足:

1. `install` / `deploy` は writable な `maven.repo.local`、`GNUPGHOME`、ネットワークが必要
2. 制約付き環境では `-Dmaven.repo.local=/tmp/m2repo` のような逃がし先が必要
3. `package` / `install` / `deploy` の再現手順は [TINYEXPRESSION-RELEASE-RUNBOOK.md](./TINYEXPRESSION-RELEASE-RUNBOOK.md) に集約してある
