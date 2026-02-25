# TinyExpression 構造・UnlaxerDSL移行・拡張ガイド

## 1. このドキュメントの目的

このドキュメントは次を一気通貫で説明する。

1. TinyExpression の現行構造
2. TinyExpression を UnlaxerDSL (UBNF) ベースへ段階移行する方法
3. 新しい型・新しい関数の追加方法
4. 追加要素を ASTMapper で AST 化する方法
5. AST から実行可能状態へ到達する方法
6. LSP / DAP と接続する方法

前提リポジトリ:

- `tinyexpression`
- `unlaxer-dsl`
- (`unlaxer-common` は parser combinator 実装の基盤)

---

## 2. TinyExpression 現行アーキテクチャ

### 2.1 パイプライン全体

現行の実行までの流れは次。

1. `TinyExpressionParser` が DSL 文字列を `Token` 木へパース
2. `VariableTypeResolver` が naked 変数の型解決を実施
3. `OperatorOperandTreeCreator` が演算子木へ再構築
4. `TinyExpressionTokens` が実行時に使う断片を抽出
5. `GeneralJavaClassCreator` / `NumberExpressionBuilder` 等が Java コードを生成
6. `JavaCodeCalculatorV3` が動的コンパイルして `TokenBaseOperator` を生成
7. `Calculator.apply(CalculationContext)` で実行

主要ファイル:

- `src/main/java/org/unlaxer/tinyexpression/parser/TinyExpressionParser.java`
- `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/VariableTypeResolver.java`
- `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/OperatorOperandTreeCreator.java`
- `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/TinyExpressionTokens.java`
- `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/GeneralJavaClassCreator.java`
- `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/JavaCodeCalculatorV3.java`

### 2.2 型ファミリの実態

現時点で第一級として扱っている型ファミリは以下。

- number
- boolean
- string
- object (javaType)

`object` は `resultType=object` のコード生成経路と、`NakedVariableDeclarationParser` / `ObjectSetterParser` / `ObjectMethodParser` 系で段階的に有効化している。

### 2.3 AST 周辺の現状

全面置換前の橋渡しとして、annotation-driven AST の最小経路が入っている。

- annotation 定義: `src/main/java/org/unlaxer/tinyexpression/ast/annotation/*`
- number 向け generated AST adapter:
  - `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/ast/NumberGeneratedAstAdapter.java`
  - `src/main/java/org/unlaxer/tinyexpression/evaluator/javacode/ast/NumberGeneratedAstNode.java`

これにより `NumberExpressionBuilder` が

- 生成 AST を使えるときは生成 AST で build
- 使えないときは従来 token path で build

という二重経路を持つ。

---

## 3. UnlaxerDSL(UBNF) への変換戦略

### 3.1 方針

一括置換しない。以下の段階移行で進める。

1. 既存 parser/token 実装を維持したまま AST 境界を明示
2. UBNF で grammar を再記述
3. `unlaxer-dsl` 生成物 (`Parser/AST/Mapper/Evaluator`) を導入
4. 既存 evaluator と並行稼働
5. 振る舞い一致が取れた単位から切り替え

### 3.2 変換対象の分割順

推奨順序:

1. number expression (優先順位・二項演算)
2. boolean expression
3. string expression
4. variable declaration / setter
5. method declaration / invocation
6. annotations/scope/profile 系

理由: number は既に annotation AST ブリッジがあり、比較基準が明確。

### 3.3 UBNF からの生成

`unlaxer-dsl` の CLI は以下生成器を持つ。

- `AST`
- `Parser`
- `Mapper`
- `Evaluator`
- `LSP`
- `Launcher`
- `DAP`
- `DAPLauncher`

`CodegenMain` 使用例:

```bash
mvn -q -DskipTests compile
java --enable-preview -cp target/classes \
  org.unlaxer.dsl.CodegenMain \
  --grammar path/to/tinyexpression.ubnf \
  --output path/to/generated \
  --generators AST,Parser,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher
```

参照:

- `../unlaxer-dsl/src/main/java/org/unlaxer/dsl/CodegenMain.java`
- `../unlaxer-dsl/README.md`

### 3.5 P4時点のUBNF反映状況

現時点の草案 `docs/ubnf/tinyexpression-p4-draft.ubnf` には、以下の型別宣言バリアントを反映済み。

1. 変数宣言:
   - `NumberVariableDeclaration`
   - `StringVariableDeclaration`
   - `BooleanVariableDeclaration`
   - `ObjectVariableDeclaration`
2. setter:
   - `NumberSetter` / `StringSetter` / `BooleanSetter` / `ObjectSetter`
3. メソッド宣言:
   - `NumberMethodDeclaration`
   - `StringMethodDeclaration`
   - `BooleanMethodDeclaration`
   - `ObjectMethodDeclaration`

この草案は `unlaxer-dsl` で validate-only / parser-ir export / parser-ir validation 済み。

### 3.6 生成コード再現手順（ローカル）

次のスクリプトで、P4草案から generated code 一式を再生成できる。

1. `scripts/generate_tinyexpression_p4_from_ubnf.sh`
2. 出力先:
   - runtime: `target/generated-sources/tinyexpression-p4/runtime`
   - tooling: `target/generated-sources/tinyexpression-p4/tooling`
3. 使用 generator:
   - runtime: `AST,Parser,Mapper,Evaluator`
   - tooling: `LSP,Launcher,DAP,DAPLauncher`
4. 現時点では `pom.xml` への自動 source 取り込みは無効化している。
   - 理由: generated runtime が要求する `unlaxer-common` / `unlaxer-dsl` API と
     現在の tinyexpression 依存バージョンに差分があるため。
   - 依存拡張ポイントは `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md` を参照。

### 3.4 Parser IR を中間契約として使う

移行時は Parser IR を出力・検証して差分管理すると事故が減る。

```bash
java --enable-preview -cp target/classes \
  org.unlaxer.dsl.CodegenMain \
  --grammar path/to/tinyexpression.ubnf \
  --export-parser-ir /tmp/tinyexpression-parser-ir.json

java --enable-preview -cp target/classes \
  org.unlaxer.dsl.CodegenMain \
  --validate-parser-ir /tmp/tinyexpression-parser-ir.json
```

---

## 4. 新しい型を定義する方法 (TinyExpression 現行系)

ここでは `object` 追加で実際に行った手順をテンプレート化する。

### 4.1 必須変更点チェックリスト

1. 型ヒント parser 追加
2. 変数 parser (prefixed/suffixed/root/method-parameter) 追加
3. 型ヒント選択 parser (`TypeHintPrefixParser` / `TypeHintSuffixParser`) へ登録
4. method parameter choice へ登録
5. expression choice に必要なら追加
6. `VariableTypeResolver` / builder / codegen 側で参照可能にする
7. `OperatorOperandTreeCreator` の再構築ルールを必要に応じて追加
8. roadmap テスト + parser テストを追加

### 4.2 今回の object 実装で追加された主要クラス

- `src/main/java/org/unlaxer/tinyexpression/parser/ObjectTypeHintParser.java`
- `src/main/java/org/unlaxer/tinyexpression/parser/ObjectTypeHintPrefixParser.java`
- `src/main/java/org/unlaxer/tinyexpression/parser/ObjectTypeHintSuffixParser.java`
- `src/main/java/org/unlaxer/tinyexpression/parser/ObjectVariableParser.java`
- `src/main/java/org/unlaxer/tinyexpression/parser/ObjectVariableMethodParameterParser.java`
- `src/main/java/org/unlaxer/tinyexpression/parser/ObjectMethodParser.java`
- `src/main/java/org/unlaxer/tinyexpression/parser/ObjectExpressionParser.java`
- `src/main/java/org/unlaxer/tinyexpression/parser/ObjectSetterParser.java`

### 4.3 検証観点

- parser 受理: 宣言/参照/メソッド引数/戻り値
- codegen 受理: `resultType=object` で Java へ落とせる
- runtime 受理: `CalculationContext` から object を get/set できる
- 既存型 regressions: number/boolean/string が壊れていない

---

## 5. 新しい関数を定義する方法 (TinyExpression 現行系)

### 5.1 number 関数を追加する場合

例: `abs(x)` を追加するケース。

1. parser 追加 (`AbsParser`)
2. `AbstractNumberFactorParser#getLazyParsers` に登録
3. `OperatorOperandTreeCreator#factor` に再構築分岐を追加
4. `NumberExpressionBuilder` に codegen 分岐を追加
5. parser test + evaluator/roadmap test を追加

### 5.2 string/boolean 関数の追加

同様に、対応する factor parser / builder / tree creator の 3 点を揃える。

- string: `StringFactorParser` 系 + `StringClauseBuilder`
- boolean: `BooleanFactorParser` 系 + `BooleanBuilder`

### 5.3 method invocation ベース関数

`call xxx(...)` で実装する場合:

- `MethodInvocationParser`
- `ArgumentsParser` / `ParametersBuilder`
- `MethodInvocationBuilder`
- `GeneralJavaClassCreator#createMethods`

の連携を確認する。

---

## 6. 新しい型/関数を ASTMapper で AST 化する方法

AST 化は 2 通りある。

### 6.1 現行 tinyexpression 内の annotation AST ブリッジを伸ばす

手順:

1. 対象 parser へ `@TinyAstNode` / `@TinyAstField` / `@TinyAstOperator` を付与
2. 生成 AST ノード(record/sealed) を追加
3. adapter (`*GeneratedAstAdapter`) へ変換規則を追加
4. builder 側で `tryGenerate(...)` 成功時の build 経路を追加

実例:

- parser annotations: `PlusParser`, `MinusParser`, `MultipleParser`, `DivisionParser`, `NumberParser`
- adapter: `NumberGeneratedAstAdapter`
- builder接続: `NumberExpressionBuilder`

### 6.2 unlaxer-dsl の MapperGenerator を使う

手順:

1. UBNF で rule に `@mapping(...)` を記述
2. `Mapper` generator でマッパースケルトン生成
3. 生成 mapper の TODO 部分に意味変換実装
4. evaluator か tinyexpression 側 adapter に接続

契約違反は `GrammarValidator` で早期に検出される。

---

## 7. AST から実行可能状態にする方法

### 7.1 既存 TinyExpression の Java codegen 実行

既存は「AST/Token -> Java source -> JIT compile -> evaluate」方式。

1. `GeneralJavaClassCreator#createJavaClass` で Java ソース生成
2. `JavaCodeCalculatorV3` でコンパイル
3. 生成クラスの `evaluate(CalculationContext, Token)` を実行

長所:

- 既存実装が豊富
- runtime 最適化を JVM に委譲できる

注意点:

- codegen 経路を増やすと型分岐が肥大化しやすい
- parser 仕様と codegen 仕様のズレをテストで縛る必要がある

### 7.2 UBNF 生成 Evaluator へ寄せる

将来的には `EvaluatorGenerator` 生成物を主経路にし、必要箇所のみ tinyexpression runtime API へ adapter を書く。

推奨順:

1. number のみ evaluator 生成経路で動かす
2. boolean/string を追加
3. side effect / scope / annotations を取り込む

### 7.3 Dual backend 実行（併存運用）

移行期間は実行バックエンドを併存させる。

1. `JAVA_CODE`: 既存 `JavaCodeCalculatorV3` 経路
2. `AST_EVALUATOR`: 生成AST/evaluator を使う経路（段階実装）

現在は以下を実装済み。

1. `ExecutionBackend` enum 追加:
   - `src/main/java/org/unlaxer/tinyexpression/runtime/ExecutionBackend.java`
2. backend 別 `CalculatorCreator` を選択する registry 追加:
   - `src/main/java/org/unlaxer/tinyexpression/loader/model/CalculatorCreatorRegistry.java`
3. `FormulaInfoAdditionalFields` に backend 指定を追加し、
   `FormulaInfoParser` で backend 経路を選択するよう変更。
4. `AST_EVALUATOR` 用の専用 calculator エントリを追加:
   - `src/main/java/org/unlaxer/tinyexpression/evaluator/ast/AstEvaluatorCalculator.java`
   - `src/main/java/org/unlaxer/tinyexpression/evaluator/ast/GeneratedAstRuntimeProbe.java`
   - generated runtime (`TinyExpressionP4Parsers`, `TinyExpressionP4Mapper`) の存在を probe しつつ、
     実行結果互換のため現時点は JavaCode delegate で動作。

注: `AST_EVALUATOR` の実体は現時点では compatibility fallback として JavaCode 経路を利用し、
後続で generated mapper/evaluator 実装へ差し替える。

---

## 8. LSP / DAP とつなげる方法

### 8.1 最短経路 (unlaxer-dsl 生成物を使用)

1. UBNF を確定
2. `LSP`, `Launcher`, `DAP`, `DAPLauncher` を生成
3. 生成サーバを起動し、VSCode 拡張または client から接続

`CodegenMain` での生成対象は既に CLI 仕様に含まれる。

### 8.2 TinyExpression 固有機能を反映する

LSP 側で追加すべき情報:

1. 変数宣言/参照の型解決 (`VariableTypeResolver` 相当)
2. `@annotation` の semantic diagnostics
3. method signature と call-site 検証

DAP 側で追加すべき情報:

1. expression 評価ステップの位置情報
2. `CalculationContext` 内変数の watch 表示
3. side effect 呼び出し境界での停止点

### 8.3 Parser IR ベースでの統合

Parser IR を LSP/DAP の共通入力として使うと、

- 仕様変更時の影響範囲が可視化される
- parser 実装とツール実装の乖離を抑えられる

---

## 9. 推奨運用ルール

1. 追加機能ごとに「parser -> AST(mapper) -> execution」の最小E2Eテストを必ず作る
2. 失敗系テストは 1 本以上入れ、診断メッセージを固定する
3. 生成物移行時は `validate-only` + parser-ir export を CI で回す
4. roadmap 進捗は `docs/TINYEXPRESSION-DSL-ROADMAP.md` と handover に同時反映する

## 11. 依存拡張が必要になったときの扱い

`unlaxer-dsl` / `unlaxer-common` の拡張が必要だと判断した場合は、次を必須ルールとする。

1. 先に tinyexpression 側 docs に拡張要求メモを追加する
2. 追加先は `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md`
3. 「現状回避策」「最小再現」「期待する拡張API/仕様」を明記する
4. 依存リポジトリへ実変更を入れる前にユーザー承認を取る

---

## 10. 直近の推奨次アクション

1. tinyexpression の UBNF 草案 (`tinyexpression.ubnf`) を作成
2. number grammar だけで `Parser/AST/Mapper/Evaluator` を生成し既存結果と比較
3. object 系 (`ObjectMethodParser`, `ObjectVariable*`) を grammar 化して差分テストを追加
4. LSP/DAP は number subset で先に通し、段階的に型ファミリを拡張
