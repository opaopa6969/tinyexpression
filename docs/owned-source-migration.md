# AST sourceの所有とslice添字の互換移行

追跡: tinyexpression #105、unlaxer-parser #163 / #165。
これはJava/Rustのalias型を揃えるための**利用側の先行移行**であり、Rust full-specや
tinyexpression-rs本体の完成を意味しない。文法・公開依存version・slice意味論は変更しない。

## 公開版と開発版

公開unlaxer 3.0.15の生成mapperには新しいsource-map snapshot APIがない。
同じversionの開発jarをローカルMaven repositoryへinstallできるため、開発機や
self-hosted runnerの既存cacheだけで公開版との互換性を判定してはいけない。

`P4SourceMapping`は公開methodだけをreflectionで調べる。新しい
`selectParsedTokenWithSourceMap(Token, String)`があれば、選択されたToken、AST、
immutable source snapshotを使う。**selectorが存在しない場合だけ**旧
`mapParsedToken`へ戻る。新API内部の例外や不正な戻り値を旧mappingで隠さない。
private fieldや`setAccessible`を使わず、AST評価のdispatchもreflectionへ変更しない。

旧経路のslice添字はStringなので従来どおり動く。snapshotがないNodeの添字は
明示エラーにする。旧mapperの並行安全性まで新APIと同じになったとは主張しない。

## 利用側

```java
var parsed = P4PreferredAstMapper.parseDetailed(formula, resultType);
var evaluator = new P4TypedAstEvaluator(types, context, parsed.sourceText());
Object value = evaluator.eval(parsed.ast());
```

calculatorは解析結果とresolverを評価時まで保持する。Formula/methodのscopeや
typed/default/template emitterにもresolverを渡す。既存constructorと
`ParsedAst(ast, selectionMode)`は残し、旧経路は`P4SourceText.lexicalOnly()`を使う。
ASTだけ返す既存入口はそのまま使えるが、Nodeからsourceを引く場合にはresolverも必要。

`P4SourceText.text(value)`はnull/Stringをそのまま返し、Nodeは所有snapshotの
Unicode codepoint半開区間から文字列を切り出す。Nodeを評価したり`toString()`を
sourceの代わりにしたりしない。未知のidentity、不正な範囲は明示エラーにする。

保持する文字列は元formulaではなく、実際にparserへ渡した`parserSource`。
コメントをUTF-16 char単位で空白化すると非BMP文字が2空白になるため、元formulaへ
同じcodepoint offsetを適用すると位置がずれる。Javaのsubstringには
`offsetByCodePoints`でUTF-16 indexへ変換して渡す。

## 既存仕様差と検証

- AST evaluator: 添字はstrip後`Integer.valueOf`。UTF-16のlength/charAtでsliceし、step 0は例外。
- default/typed Java emitter: 添字はstrip後のJava式として従来どおり出力し、CodePointのSlicerを利用。
- これらの既存差を今回のsource所有移行と一緒に統一しない。

`P4SourceMappingTest`、`P4OwnedSliceSourceTest`、`P4JavaCodeEmitterSourceTextTest`が、
旧String、新Node相当入力、欠損・符号・不正添字・Unicode・後続parse・未知Nodeを検証する。
現行生成SliceExprのfield自体はまだStringなので、Node添字のテストはadapterと
添字変換/生成経路へ直接Nodeを渡す。実際の生成fieldのNode化と共通Java/Rust比較は
unlaxer-parser #163の次段階で行う。

CIのmapper-compatibility matrixは、公開3.0.15と固定したsnapshot API導入commitを
別々の空Maven repositoryで構築する。期待するAPI能力もテストでassertし、意図しない
開発jar混入を検出する。通常CIの既知失敗baselineには新しい失敗を追加しない。
