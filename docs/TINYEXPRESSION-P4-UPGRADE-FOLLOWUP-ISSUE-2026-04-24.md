# Issue Draft: unlaxer-dsl 3.0.2 / P4 更新後の残課題

Last updated: 2026-04-24

## 背景

`tinyexpression` は `unlaxer-common 3.0.2` / `unlaxer-dsl 3.0.2` へ更新済みで、
最新 UBNF 文法も P4 runtime / LSP / DAP に適用済み。
今回の更新で以下は完了した。

1. dependency 更新
   - `org.unlaxer:unlaxer-common:3.0.2`
   - `org.unlaxer:unlaxer-dsl:3.0.2`
2. UBNF 更新
   - fully-qualified token parser
   - CodeBlock
   - boolean equality
   - string dot method
   - slice variants
   - `isPresent(...)`
   - `inTimeRange(...)` / `inDayTimeRange(...)`
   - typed `if` / ternary
   - strict `match` typing
3. runtime / tooling 追従
   - `P4PreferredAstMapper` による preferred root 共通化
   - `P4TypedAstEvaluator` / Java emitter の source-aware 補正
   - `TE025` の導入
   - `_tinyP4ParserExact` / `_tinyP4ParserProbeMode` の公開
4. targeted verification
   - `P4TypedAstEvaluatorTest`
   - `AstEvaluatorParityCorpusTest`
   - `AstEvaluatorBackendParityTest`
   - `ThreeExecutionBackendParityTest`
   - `P4BackendParityTest`
   - `TinyExpressionP4LanguageServerExtTest`
   - `mvn -q -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true package`

## 残課題

### 1. release/install 導線が環境依存のまま

問題:

1. `mvn package` は通るが `mvn install` / `mvn deploy` は local repo / GPG / network 前提になる
2. 制約付き環境では `~/.m2/repository` と `~/.gnupg` に書けず再現性が落ちる

やること:

1. writable な `maven.repo.local` と `GNUPGHOME` を前提にした release 手順を文書化する
2. 必要なら CI 側へ `package -> verify -> deploy` の staging workflow を分離する
3. `ossrh` 経路を使い続けるか、Central publishing plugin へ寄せるかを決める

受け入れ条件:

1. `package` だけでなく `install` / `deploy` の再現手順が 1 本化されている
2. ローカル制約下でも `/tmp` 逃がし先で dry-run できる

資料:

- [pom.xml](/home/opa/work/tinyexpression/pom.xml)
- [tools/tinyexpression-p4-lsp-vscode/pom.xml](/home/opa/work/tinyexpression/tools/tinyexpression-p4-lsp-vscode/pom.xml)
- [docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md](/home/opa/work/tinyexpression/docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md)

### 2. preferred root / compat parse が local facade のまま

問題:

1. `P4PreferredAstMapper` は generated mapper の外側で preferred-root 選択と compat parse を吸収している
2. `ParseContext.close()` cleanup 由来の例外も local workaround で抑えている
3. 根本的には codegen-native な root selection と cleanup 契約が欲しい

やること:

1. `unlaxer-dsl` で最小再現を作る
2. `preferredAstSimpleName` の優先選択を generator 標準機能へ寄せる
3. local facade を段階的に薄くする

受け入れ条件:

1. runtime / LSP / DAP が facade なしでも同じ root を選べる
2. compat parse 用の reflection / private method 呼び出しを削減できる

資料:

- [P4PreferredAstMapper.java](/home/opa/work/tinyexpression/src/main/java/org/unlaxer/tinyexpression/p4/P4PreferredAstMapper.java)
- [GeneratedAstRuntimeProbe.java](/home/opa/work/tinyexpression/src/main/java/org/unlaxer/tinyexpression/evaluator/ast/GeneratedAstRuntimeProbe.java)
- [P4ParseProbe.java](/home/opa/work/tinyexpression/src/main/java/org/unlaxer/tinyexpression/evaluator/p4/P4ParseProbe.java)
- [docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md](/home/opa/work/tinyexpression/docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md)

### 3. 旧 LSP に対する編集支援 parity がまだ足りない

問題:

1. 変数カタログ補完が未移植
2. `import sample.v1.Class#method as alias;` のフル補完が未移植
3. クイックフィックス群が薄い
4. `$` trigger 補完が未移植
5. ホバー上の計算値表示が未移植

やること:

1. 旧 `calculator-lsp` の変数カタログと quick fix を棚卸しし、P4 LSP の責務へ分割する
2. `ScopeStore` と catalog resolver の境界を決める
3. まずセミコロン補完、括弧補完、`$` trigger を先に戻す

受け入れ条件:

1. 高頻度の編集支援が旧 LSP と同等以上になる
2. P4 parser ベースの診断と quick fix が矛盾しない

資料:

- [docs/TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md](/home/opa/work/tinyexpression/docs/TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md)
- [docs/TINYEXPRESSION-LSP-ADDITIONAL-FEATURES.md](/home/opa/work/tinyexpression/docs/TINYEXPRESSION-LSP-ADDITIONAL-FEATURES.md)
- [TinyExpressionP4LanguageServerExt.java](/home/opa/work/tinyexpression/tools/tinyexpression-p4-lsp-vscode/src/main/java/org/unlaxer/tinyexpression/lsp/p4/TinyExpressionP4LanguageServerExt.java)

### 4. full-suite / CI の新基準がまだ固定されていない

問題:

1. targeted suite は green だが、最新版 UBNF 適用後の smoke set が docs / CI に固定されていない
2. parity corpus は増えたが、どの組み合わせを gate にするかが曖昧

やること:

1. regenerate + compile + targeted smoke tests を 1 セットで固定する
2. P4 / LSP / DAP の最小 acceptance matrix を作る
3. 可能なら CI profile を追加する

受け入れ条件:

1. docs と CI の確認手順が一致する
2. regressions を再現しやすい smoke set が決まる

資料:

- [docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md](/home/opa/work/tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md)
- [three-backend-parity-corpus.txt](/home/opa/work/tinyexpression/src/test/resources/parity/three-backend-parity-corpus.txt)
- [P4BackendParityTest.java](/home/opa/work/tinyexpression/src/test/java/org/unlaxer/tinyexpression/p4/P4BackendParityTest.java)

## 推奨確認コマンド

```bash
scripts/generate_tinyexpression_p4_from_ubnf.sh
mvn -q -DskipTests compile
mvn -q -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true package
mvn -q -Dtest=org.unlaxer.tinyexpression.evaluator.ast.P4TypedAstEvaluatorTest test
mvn -q -Dtest=org.unlaxer.tinyexpression.p4.P4BackendParityTest test
cd tools/tinyexpression-p4-lsp-vscode && mvn -q -Dtest=org.unlaxer.tinyexpression.lsp.p4.TinyExpressionP4LanguageServerExtTest test
```

release/install 系を検証する場合の追加前提:

1. `-Dmaven.repo.local=/tmp/m2repo`
2. `GNUPGHOME=/tmp/gnupg`
3. deploy 可能な network と認証情報

## 関連ドキュメント

1. [docs/architecture-ja.md](/home/opa/work/tinyexpression/docs/architecture-ja.md)
2. [docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md](/home/opa/work/tinyexpression/docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md)
3. [docs/TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md](/home/opa/work/tinyexpression/docs/TINYEXPRESSION-P4-FEATURE-GAP-ANALYSIS.md)
4. [docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md](/home/opa/work/tinyexpression/docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md)
