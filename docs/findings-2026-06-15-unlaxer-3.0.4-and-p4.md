# 調査・修正レポート: unlaxer 3.0.4 移行と P4 パイプライン (2026-06-15)

## TL;DR

- `unlaxer-common` / `unlaxer-dsl` を **3.0.2 → 3.0.4** に更新（HEAD のソースは既に 3.0.4 の API
  (`WildCardStringTerminatorParser` の typo 修正版) に依存しており、公開 3.0.2 ではコンパイル不能だった。
  3.0.4 が初の整合ビルド）。
- **致命的バグを修正**: P4 生成パーサが起動時に必ず `IllegalStateException: transaction nest is illegal`
  を投げ、**P4 一次経路が 100% 機能せず全式が手書き legacy にフォールバック**していた。
  原因は ubnf のルール名衝突（下記 BUG-0）。修正により **テスト失敗 51 → 8 に激減**。
- **性能**: AST_EVALUATOR(P4 インタプリタ) が apply ごとに毎回フルパースしていたため巨大式が激遅だった。
  AST キャッシュを追加し、**12KB 式の繰り返し評価が 5717ms → 0.19ms（約3万倍）**。
- **boolean 入れ子の挙動**を実機で確定。手書き legacy はフラット左結合、P4 は文法どおり
  `OR < AND < XOR` の優先順位。両者は mixed 演算で**結果が食い違う**。
- 残るバグ（paren-boolean / nested-ternary / variadic-min / cross-check）は **unlaxer-dsl の
  ジェネレータ起因**が中心で、`KnownP4BugsTest` に @Ignore で再現コード付きで記録。

---

## 1. 依存更新 (3.0.2 → 3.0.4)

| artifact | before | after |
|---|---|---|
| unlaxer-common | 3.0.2 | 3.0.4 |
| unlaxer-dsl | 3.0.2 | 3.0.4 |

3.0.4 の差分（jar 比較）:
- 追加: `org.unlaxer.context.DiagnosticFormatter`, `WildCardStringTerminatorParser`（typo 修正）,
  unlaxer-dsl の `codegen.*`（`CodeGenerator`/`ParserGenerator`/`MapperGenerator`/`EvaluatorGenerator`/
  `ASTGenerator`/`GrammarValidator`/`DAPGenerator`/`LSPGenerator` ほか多数）, `tools/railroad`, `ir.*`, `init.*`。
- 削除: `StringBase`, `StringIndexAccessor*`, `StringSource2`, typo 版 `WildCardStringTerninatorParser`。

ビルドの `generate-sources` は既に新ジェネレータ `org.unlaxer.dsl.CodegenMain --generators Parser,AST,Mapper,Evaluator`
を使用しており、これは 3.0.4 でのみ存在する。

---

## 2. BUG-0（修正済・最重要）: P4 生成パーサの無限自己再帰

### 症状
`TinyExpressionP4Mapper.parse(...)` が任意の式で
`IllegalStateException: transaction nest is illegal. check source code.` を送出。
`AstEvaluatorCalculator` は P4 を一次経路と謳いつつ、毎回これを握りつぶして手書き legacy に
フォールバックしていた（ログに `no P4 AST mapping attempted` が多発）。

### 根本原因（ジェネレータの名前衝突）
ubnf:
```
token CODE_START = org.unlaxer.tinyexpression.parser.javalang.CodeStartParser
CodeStart ::= CODE_START ;
```
ルール `CodeStart` から生成される内部クラス `CodeStartParser` が、トークンの外部パーサクラス
`...javalang.CodeStartParser` と**同名**になり、生成コードの `Parser.get(CodeStartParser.class)` が
内部クラス自身に解決 → 無限自己再帰 → トランザクションリスナー(`@scopeTree(mode=lexical)` 由来の
`ScopeStore.enter/leave`)の begin/commit が不整合になり例外。

### 修正
ubnf のルール名をトークンパーサ名と衝突しないよう変更:
```
CodeBlock ::= CodeBlockStart CodeBlockBody CodeBlockEnd ;
CodeBlockStart ::= CODE_START ; CodeBlockBody ::= CODE_BODY ; CodeBlockEnd ::= CODE_END ;
```
生成器はトークンラッパーを `class CodeStartParser extends ...javalang.CodeStartParser` として正しく出力し、
自己再帰が解消。**全体テスト失敗 51 → 8**。

### 提案（unlaxer-dsl 側）
ルール名とトークンパーサクラスの単純名衝突を **generate 時にエラー検出**すべき（`GrammarValidator` に
`E-RULE-TOKEN-NAME-COLLISION` を追加）。現状は無言で壊れたコードを出力する。

---

## 3. ubnf 定義ミス（修正済）

`generate-sources` 実行時に `GrammarValidator` が警告（3.0.4 新機能）:
```
W-TOKEN-UNRESOLVED: token NUMBER references unresolved parser class: NumberParser ...
（IDENTIFIER / STRING / EOF も同様）
```
トークンが完全修飾名でなかった。完全修飾に修正（警告解消・可搬性向上）:
```
token NUMBER     = org.unlaxer.parser.elementary.NumberParser
token IDENTIFIER = org.unlaxer.parser.clang.IdentifierParser
token STRING     = org.unlaxer.parser.elementary.SingleQuotedParser
token EOF        = org.unlaxer.parser.elementary.EndOfSourceParser
```
（生成器は既知パッケージのフォールバックで救済していたため機能影響は無かったが、定義としては不正だった。）

---

## 4. 性能: AST キャッシュ（修正済）

### 計測（修正前）
| terms | bytes | インタプリタ create / apply | javacode create / apply |
|---|---|---|---|
| 1 | 1B | 235ms / 7.64ms | 428ms / ~0ms |
| 200 | 399B | 435ms / 141ms | 139ms / ~0ms |
| 2000 | 4KB | 2262ms / 1351ms | **CompileError** |
| 6000 | 12KB | 8549ms / **5717ms** | **CompileError** |

判明事項:
1. **javacode は ~4KB 以上でコンパイル不能**（JVM の1メソッド 64KB バイトコード上限）。
   → **数十KB の式は javacode 方式では扱えない**。インタプリタが唯一の現実解。
2. javacode の apply はコンパイルさえ通ればネイティブで ~0ms。
3. インタプリタは `AstEvaluatorCalculator.apply()` が**毎回フルパース＋マップ**していたため遅い。

### 修正
`AstEvaluatorCalculator` に、宣言を含まない式で typed 経路が成功した AST を
インスタンスにキャッシュするフィールド `cachedTypedAst` を追加。以降の apply は再パースを省略し
キャッシュ AST を現在の `CalculationContext` で評価（構造のみキャッシュ＝変数値は最新）。

### 計測（修正後）
| terms | bytes | インタプリタ apply |
|---|---|---|
| 1 | 1B | 0.02ms |
| 2000 | 4KB | 0.06ms |
| 6000 | 12KB | **0.19ms** |

→ 繰り返し評価が **約3万倍高速化**。全 1176 テストでリグレッション無し。

### 速度に関する結論（ご質問への回答）
- **compiler server 化より、P4 インタプリタの本流化＋AST キャッシュ＋(必要時)バイトコードキャッシュ**が
  コスパで上。compiler server は「多数の異なる式をコールド JVM で繰り返しコンパイル」が常態の場合の最終手段。
- 数十KB の式は javacode では 64KB 上限で**そもそも不可**。インタプリタ＋AST キャッシュなら apply は
  サブミリ秒。残コストは「一度きりのパース」(12KBで~8s)で、これはパーサ自体の最適化が次の課題。

### テスト方針（ご提案への回答）
「インタプリタと javacode が同一処理だと証明できればテストはインタプリタでよい」について:
- 単純式（算術・単一演算 boolean・if/match・文字列）は両者一致（パリティ成立）。
- **mixed boolean 演算は一致しない**（インタプリタ=文法優先順位 / javacode=手書きフラット左結合、§5）。
- よって「インタプリタ専用の高速テスト（`GrammarCoverageInterpreterTest`）＋ 一致範囲のみのパリティ証明」
  という構成を推奨。完全パリティは手書き legacy を廃する（=javacode 経路を P4 由来に統一する）まで成立しない。

---

## 5. boolean 入れ子の挙動（実機確定）

### 優先順位
- **P4 (文法どおり)**: `OR(緩) < AND < XOR(強)`。
  - `true | false & false` = **true**（`true | (false&false)`）
  - `true ^ true & false` = **false**（`(true^true) & false`）
  - `true & false ^ true` = **true**（`true & (false^true)`）
- **手書き legacy**: `& | ^ == !=` をすべて**同一優先順位・左結合のフラットチェーン**で評価
  （`AbstractBooleanExpressionParser`）。
  - `true | false & false` = **false**（`(true|false) & false`）

→ 両バックエンドは mixed 演算で**結果が食い違う**。`not(...)` は両者とも括弧必須で正しく動作。

### 注意（設計判断）
- 文法の `XOR > AND` は Java の `&(AND) > ^(XOR) > |(OR)` とも異なる。意図的かご確認を。
- `not` は文法上 `not(' BooleanExpression ')'`（括弧必須）。

---

## 6. 既知バグ（`KnownP4BugsTest` に @Ignore で再現コード）

| ID | 症状 | 根本原因 | 直し場所 |
|---|---|---|---|
| P4-BUG-1 | `(a|b) & (c|d)` 等、括弧 boolean を演算子オペランドにすると `(` を value 捕捉し誤評価 | 生成マッパー: `'true'/'false'` が WordParser キャッチオールに compile され `(` トークンと衝突。`@mapping` 無しラッパールールは firstTokenText を返し再帰しない | unlaxer-dsl `MapperGenerator` |
| P4-BUG-2 | ネスト ternary `(true ? (false?1:2) : 3)` = 1（正解2） | 生成マッパーのネスト ternary 捕捉異常 | unlaxer-dsl `MapperGenerator` |
| P4-BUG-3 | `min/max` の3引数以上が誤値（min(3,5,1,9)=3, 正解1） | P4-typed は正解(1)だが、`AstEvaluatorCalculator` の数値 cross-check が**壊れた legacy variadic min/max(=3)を優先**して正解を捨てる。legacy 側 variadic も壊れている | tinyexpression cross-check + legacy |
| P4-BUG-4 | `if(1>0 | 0>1 & 1>2)` = 0（正解1） | P4-typed は正解(1)だが cross-check が legacy のフラット結果(0)で上書き | tinyexpression cross-check |

### cross-check に関する提案
`AstEvaluatorCalculator` は数値結果を legacy token-AST と照合し、不一致時に **legacy を信頼**する。
しかし legacy は (a) variadic min/max が壊れ、(b) boolean がフラット優先順位、で**しばしば legacy の方が誤り**。
手書き legacy を廃する方針なら、cross-check は撤去するか、少なくとも P4 を正とすべき。
ただし P4-BUG-1/2 のようにマッパー側のバグも残るため、**ジェネレータ修正と同時に**行うのが安全。

---

## 7. パーサの top-level 型ディスパッチ問題

裸の `1 > 0 & 2 > 1`（top-level）は P4・手書き両方でパース失敗（2文字で停止）。
`Expression ::= NumberExpression | BooleanExpression | ...` で NumberExpression が先に `1` だけ消費するため。
`(1>0)&(2>1)` や `if(1>0 & 2>1){...}` のように**括弧/if 文脈なら正常**。
実害は限定的だが、top-level の最長一致 or 型推定が望ましい（要設計判断）。

---

## 8. P4 機能ギャップ（既存テストの残失敗 4 件）

P4 復活後も以下は legacy にフォールバック（= P4 文法に機能が無い）。`AstEvaluatorGeneratedValuePathTest` /
`GeneratedAstRuntimeProbeTest` の残失敗の原因。**いずれも私の変更による新規ではなく既存ギャップ**
（修正前は P4 が全滅していたため別理由で失敗していた）:

1. **ブロックコメント `/* */`**: 文法は `@comment: { line: '//' }` のみ。
2. **`len()` と ダブルクォート文字列 `"..."`**: 文法は `length()` と単一引用符のみ。
3. **`.in(...)` メソッド**: 文法に無い（legacy のみ）。
4. **メソッド呼び出し引数のスコープ / 宣言 setter の P4 経路**: P4 typed 未対応で token-ast に落ちる。

→ 「手書き fallback を廃す」には P4 文法/ジェネレータにこれらを実装する必要がある。

### 補足: JAVA_CODE バックエンドは math 関数を未サポート
`abs/sqrt/round/ceil/floor/pow/min/max` を JAVA_CODE 経路に与えると
`IllegalArgumentException: Unsupported parser in factor: AbsParser` で失敗する。
**インタプリタの方が機能的に上位**であり、parity は共通サブセット（算術・単一演算 boolean・if・
比較 in if・match・文字列）でのみ証明可能（`InterpreterJavacodeParityTest`）。

---

## 9. 変更ファイル一覧

- `pom.xml`: 依存 3.0.4、surefire `forkCount` 並列化（`-Dte.forkCount`）、`skipRailroad` プロパティ。
- `tools/.../tinyexpression-p4.ubnf`: トークン完全修飾、CodeStart/End ルール改名（BUG-0 修正）。
- `src/.../evaluator/ast/AstEvaluatorCalculator.java`: `cachedTypedAst` による AST キャッシュ。
- テスト追加: `GrammarCoverageInterpreterTest`（網羅・green）, `KnownP4BugsTest`（@Ignore 再現）,
  `BackendSpeedBenchmarkTest`（@Ignore ベンチ）。

## 10. 推奨 issue 化リスト

1. (unlaxer-dsl) ルール名 vs トークンパーサ名の衝突を generate 時にエラー化（BUG-0 再発防止）。
2. (unlaxer-dsl MapperGenerator) `'literal' @value` の WordParser キャッチオール衝突（P4-BUG-1）。
3. (unlaxer-dsl MapperGenerator) `@mapping` 無しラッパールール/ネスト ternary/variadic `{,X@rest}` の捕捉（P4-BUG-1/2）。
4. (tinyexpression) cross-check の撤去 or P4 優先化＋legacy variadic min/max 修正（P4-BUG-3/4）。
5. (tinyexpression/P4) ブロックコメント・`len`・ダブルクォート・`.in`・宣言 setter の P4 対応（手書き廃止の前提）。
6. (P4 文法) top-level `Expression` の型ディスパッチ（裸の boolean 比較）。
