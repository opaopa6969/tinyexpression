# TinyExpression Dependency Extension Notes

このドキュメントは、`tinyexpression` 実装中に `unlaxer-dsl` / `unlaxer-common` 側の拡張が必要と判断した事項を記録する。

運用ルール:

1. 拡張が必要かもしれない時点で、このファイルに記録する。
2. 実際に `unlaxer-dsl` / `unlaxer-common` を編集する前にユーザーへ許可を取る。
3. 拡張不要で解決した場合は「解消済み」と明記する。

---

## 2026-02-25: P4 UBNF associativity validation gap

### Context

- File: `docs/ubnf/tinyexpression-p4-draft.ubnf`
- Goal: `@leftAssoc` + `@precedence` を TinyExpression P4 draft で有効化する。

### Observed result

`unlaxer-dsl` validate-only で以下が継続発生:

- `E-ASSOC-MISSING-CAPTURE` (`@right` が見つからない)
- `E-ASSOC-NO-REPEAT`
- `E-MAPPING-MISSING-CAPTURE` (`right`)

再現最小 grammar:

- `docs/ubnf/tinyexpression-p4-assoc-repro.ubnf`

再現コマンド:

```bash
cd /mnt/c/var/unlaxer-temp/unlaxer-dsl
mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain \
  -Dexec.args="--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-assoc-repro.ubnf --validate-only --report-format json"
```

### Resolution status

- 解消済み（dependency 側拡張なし）。
- 演算子選択を repeat 内 group choice から rule reference へ変更することで validator-pass となった。
  - `NumberExpression ::= NumberTerm @left { AddOp @op NumberTerm @right } ;`
  - `NumberTerm ::= NumberFactor @left { MulOp @op NumberFactor @right } ;`

### Decision

1. 現時点では `unlaxer-dsl` / `unlaxer-common` の変更は不要。
2. この問題で dependency repo を編集する必要はない。

### Required action before editing dependency repos

- 今後別件で `unlaxer-dsl` への修正が必要になった時点で、ユーザー許可を取得してから着手する。

---

## 2026-02-25: unlaxer-dsl MapperGenerator implementation start

### Context

- Target repo: `unlaxer-dsl`
- Target file: `src/main/java/org/unlaxer/dsl/codegen/MapperGenerator.java`
- Goal: UBNF -> AST の自動マッピングを実運用可能にする第一段階。

### Implemented (in progress)

1. generated `parse(String)` の未実装例外を除去し、実パース導線を生成
2. mapping rule parser class による dispatch (`mapNode`) を生成
3. left/right assoc ルールの最小マッピング生成を実装
4. utility (`findFirstDescendant`, `firstTokenText`) を追加

### Compatibility impact

1. `CodegenSnapshotTest` の mapper golden は更新が必要
2. TinyExpression 側では dual-path 実行（javacode / ast-evaluator）接続実装がまだ必要

### Follow-up in tinyexpression

1. generated mapper を受ける AST evaluator 実行器を追加
2. 既存 Java codegen 実行器との mode 切替を導入
3. DAP を mode 別に接続する
4. generated mapper runtime probe を `AstEvaluatorCalculator` に統合し、
   AST path 接続進捗を context object で可視化する

---

## 2026-02-25: unlaxer-dsl DAP runtimeMode hook

### Context

- Target repo: `unlaxer-dsl`
- Target file: `src/main/java/org/unlaxer/dsl/codegen/DAPGenerator.java`
- Goal: DAP で `javacode` / `ast` の両経路接続を可能にする準備。

### Implemented

1. launch 引数 `runtimeMode` を追加（default: `token`）
2. generated adapter に `isAstRuntimeMode()` 分岐を追加
3. `collectStepPoints` を mode-dispatch 化
4. variables へ `runtimeMode` 表示を追加

### Current limitation

- `runtimeMode=ast` は現時点では token stepping fallback。
- mapper/evaluator runtime が接続された段階で AST ノードステップへ差し替える。

---

## 2026-02-25: generated runtime compile compatibility gap

### Context

- `scripts/generate_tinyexpression_p4_from_ubnf.sh` で生成した runtime コードを tinyexpression compile に取り込むと、
  現行依存 (`org.unlaxer:unlaxer-common:1.2.7`) と API 不整合でビルド失敗する。

### Observed compile errors

1. `org.unlaxer.StringSource#createRootSource(String)` が未提供
2. generated mapper が `Token.source` フィールドを直接参照するが現行 `Token` API に存在しない
3. generated parser が `org.unlaxer.dsl.ir` パッケージ型を参照するが tinyexpression 依存に未提供

### Current decision

1. 生成物は `target/generated-sources/tinyexpression-p4/runtime|tooling` に分離出力
2. tinyexpression compile には runtime のみを自動取り込み
3. dependency 拡張（`unlaxer-common` / `unlaxer-dsl`）は継続管理しつつ、互換 shim を順次適用

### Required extension candidates

1. `unlaxer-common`:
   - `StringSource#createRootSource(String)` 互換 API
   - mapper generator と整合する token text/source access API
2. `unlaxer-dsl`:
   - parser generator が runtime で解決可能な package import を使うこと
   - mapper generator が `Token` 公開 API のみを使うこと

### Progress update

`unlaxer-dsl` 側で以下の compatibility shim 実装を反映済み:

1. generated `Mapper` / `LSP` / `DAP` の source 初期化を `createRootSourceCompat(...)` に変更
   - `StringSource.createRootSource(String)` がある環境と、ない環境の両方で reflection fallback する
2. generated `Mapper` の token text 抽出を `tokenTextCompat(...)` に変更
   - `getToken()` / `tokenString` / `source.sourceAsString()` を順に fallback
3. generated `Parsers` の synthetic scope event builder から `org.unlaxer.dsl.ir` 直接参照を除去
   - 互換 no-op 実装へ置換
4. generated `Mapper` の assoc mapping に leaf fallback ノード生成を追加
   - 非 mapped 末端（例: number literal）でも `BinaryExpr` 形に畳み込めるようにした
5. generated `Mapper` の non-assoc mapping で capture 抽出を実装
   - placeholder default 値のみだった constructor 引数を、capture-based extraction へ移行
6. generated `DAP` の `runtimeMode=ast` stepping を AST件数基準へ改善
   - step数は `astNodeTypes` 件数で管理
   - source座標は token 由来を維持（移行期互換）
7. generated `Mapper` に AST node source span API を追加
   - `sourceSpanOf(Object)` で AST node ごとの start/end offset を返す
   - mapper 生成時に constructor 返却ノードへ span を自動登録
8. generated `DAP` の `runtimeMode=ast` で source座標を AST span ベースへ改善
   - stackTrace の line/column を AST node span 由来で計算
   - breakpoint line hit 判定も AST step index + span line に同期
9. generated `Mapper` の root未mapping時ノード選択を改善
   - 「最初のmappedノード」固定を廃止
   - `depth asc` + `startOffset desc` ヒューリスティックで主式候補を選択
10. generated `Mapper` の choice capture 解決を複数候補対応へ拡張
   - 同一 capture 名を持つ選択肢を順に探索
   - target constructor 型との互換性ガードを追加（不一致候補は生成時にスキップ）
   - `String` target は token text 抽出へフォールバック

検証結果:

1. `scripts/generate_tinyexpression_p4_from_ubnf.sh` 実行後に
   `./mvnw -q -DskipTests compile` で runtime 自動取り込み状態のビルド成功を確認済み

残タスク:

1. generated runtime を使った実評価経路（mapper/evaluator）を AST backend 本体へ段階的に接続
2. AST backend の対応演算子/式カテゴリを number-only から段階拡張

---

## 2026-02-25: heterogenous capture inference + identifier capture extraction

### Context

- Target repo: `unlaxer-dsl`
- Target files:
  - `src/main/java/org/unlaxer/dsl/codegen/ASTGenerator.java`
  - `src/main/java/org/unlaxer/dsl/codegen/MapperGenerator.java`
- Goal:
  1. mixed choice capture（例: `ObjectExpression`）で generated AST field type が単一型に固定される問題を解消
  2. `VariableRef` の `IDENTIFIER` capture が `$` に崩れる問題を解消

### Observed issue

1. `ObjectExpr.value` が `BinaryExpr` に固定推論され、`VariableRefExpr` / `StringExpr` 経路が mapper で型ガード除外される
2. `VariableRefExpr.name` が `$` になるケースがあり、generated evaluator で context variable 解決に失敗する

### Implemented

1. capture 型推論を first-capture 固定から multi-capture 集約へ変更
   - 同一 capture 名で複数型が出る場合は `Object` に degrade
   - optional/repeat フラグは capture 群の OR 集約で推論
2. string target capture の式生成を拡張
   - `IdentifierParser` token capture は `identifierLikeText(...)` を使用
   - fallback は従来どおり `stripQuotes(firstTokenText(...))`
3. generated mapper utility に identifier 抽出ヘルパーを追加
   - `identifierLikeText(Token)`
   - `extractIdentifierLike(String)`
4. generated mapper entry point を拡張
   - `parse(String, String preferredAstSimpleName)` を追加
   - preferred AST 名一致ノードを優先して root candidate を選択
5. assoc mapping 演算子抽出を repeat-token全体から op要素限定へ修正
   - `findFirstDescendant(repeatToken, <op parser>)` ベースで operator を抽出
   - `+2` のような結合トークン混入を防止

### Compatibility impact

1. generated AST field 型がより寛容（`Object`）になるため、mixed-choice grammar で compile/runtime 失敗を避けられる
2. 既存の単一型 capture grammar には影響を与えない（単一型時は従来型を維持）
3. mapper 出力差分が増えるため snapshot/golden の更新が必要

### Follow-up in tinyexpression

1. `ObjectExpression` mapping を有効化した generated runtime で `AST_EVALUATOR` object path を再接続
2. declaration setter 実行と合わせて object variable formula の `generated-ast` 経路を確認

---

## 2026-02-25: duplicate parser-class capture indexing in non-assoc mapping

### Context

- Target repo: `unlaxer-dsl`
- Target file: `src/main/java/org/unlaxer/dsl/codegen/MapperGenerator.java`
- Trigger: `ComparisonExpression` に `@mapping(ComparisonExpr, params=[left, op, right])` を追加後、
  generated mapper が `left/right` の両方に同じ最初の `NumberExpression` を割り当てるケースを確認。

### Observed issue

1. non-assoc mapping で `findFirstDescendant(...)` を param ごとに使うため、
   同一 parser class capture を複数 param (`left`,`right`) で使うと同じ token が再利用される。
2. index対応を入れても `findDescendants(...)` が self を含まないため、
   root parser class と同じ capture で 0番要素が解決できないケースが発生。

### Implemented

1. generated mapper utility に `findDescendantByIndex(Token, parserClass, index)` を追加。
2. non-assoc scalar/optional capture で parser class ごとの occurrence index を mapping param順で共有して割り当て。
3. `findDescendantByIndex(...)` は self token 一致時に `index=0` を self で返す挙動へ拡張。
4. token declaration が `IdentifierParser` の capture は `IdentifierParser.class` を直接探索するよう修正。
   - `MethodInvocationExpr.name` など identifier capture が rule root token へ吸い込まれる問題を解消。

### Effect

1. `left/right` のような同一 parser class 重複captureが正しく別tokenへマップされる。
2. 既存 single-capture mapping (`StringExpr.value` など) の後方互換性を維持。

### Follow-up in tinyexpression

1. `ComparisonExpr` direct-eval path の回帰確認（`match{1==1->...,default->...}`）。
2. generated-path runtime testで `javacode-fallback` 退行がないことを継続監視。

---

## 2026-02-26: generated DAP runtime-probe bridge variables

### Context

- Target repo: `unlaxer-dsl`
- Target file: `src/main/java/org/unlaxer/dsl/codegen/DAPGenerator.java`
- Goal:
  - generated DAP adapter 側から tinyexpression runtime の実際の backend 選択結果と実行マーカーを参照可能にする。

### Implemented

1. generated adapter に `runtimeProbeVariables` フィールドを追加。
2. parse 完了時に `collectRuntimeProbeVariables()` を呼ぶように変更。
3. generated adapter が reflection で optional bridge を呼び出すように変更。
   - class: `org.unlaxer.tinyexpression.dap.TinyExpressionDapRuntimeBridge`
   - method: `debugVariables(String formulaSource, String runtimeMode)`
4. DAP `variables` 応答へ `runtimeProbeVariables` を展開するように変更。
5. bridge 不在時・呼び出し失敗時は従来動作を維持（silent fallback）。

### Compatibility impact

1. tinyexpression bridge class がない環境でも generated DAP は動作継続。
2. bridge class がある環境では backend marker（`_tinyExecution*` など）が DAP variables に露出し、
   `JAVA_CODE` / `AST_EVALUATOR` / `DSL_JAVA_CODE` の識別が容易になる。

### Follow-up in tinyexpression

1. `TinyExpressionDapRuntimeBridge` 側で `runtimeMode` -> `ExecutionBackend` 変換を統一して維持する。
2. DAP stepping 自体の evaluator-level parity（value/step alignment）は別スライスで継続する。
