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

検証結果:

1. `scripts/generate_tinyexpression_p4_from_ubnf.sh` 実行後に
   `./mvnw -q -DskipTests compile` で runtime 自動取り込み状態のビルド成功を確認済み

残タスク:

1. generated runtime を使った実評価経路（mapper/evaluator）を AST backend 本体へ段階的に接続
2. DAP runtimeMode の `ast` 分岐で AST ノード単位 stepping へ切替
3. AST backend の対応演算子/式カテゴリを number-only から段階拡張
