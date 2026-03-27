[English](./implementation-guide-dialogue.en.md) | [日本語](./implementation-guide-dialogue.ja.md) | [Index](./INDEX.md)

---

# tinyexpression 実装ガイド — 会話で学ぶ5つのバックエンド

> **登場人物**
> - **先輩（S）**: tinyexpressionの設計をよく知るシニア開発者
> - **新人（N）**: このライブラリを初めて触る開発者

---

## 第1話 — そもそも「バックエンド」って何ですか？

**N:** 先輩、`JavaCodeCalculatorV3` とか `AstEvaluatorCalculator` とか、似たようなクラスがいっぱいありますね。これ何が違うんですか？

**S:** `"$a + $b * 2"` という式を評価する、という目標は同じ。ただ「どうやって評価するか」が違う。それがバックエンド。今は5系統ある。

```
  式文字列
     │
  ┌──┴────────────────────────────────────────────────┐
  │                                                    │
  ▼                                                    ▼
[compile系]                                      [AST系]
  │                                                    │
  ├─[1] compile-hand                      ┌────────────┼──────────────────┐
  │     手書きコード生成 → javac           │            │                  │
  │                                       ▼            ▼                  ▼
  └─[2] compile-dsl               [3] ast-hand  [4] P4-reflection  [5] P4-typed
        P4 AST → コード生成 → javac  アノテーション    reflection        sealed switch
```

**N:** compile系が2つあるんですね。

**S:** そう。どちらも最終的にjavacでコンパイルして `.class` を実行するのは同じ。「コードをどうやって生成するか」が違う。5つをまとめると:

| # | 名前 | 方式 | キークラス |
|---|------|------|-----------|
| 1 | **compile-hand** | 手書きコード生成ロジック → javac | `JavaCodeCalculatorV3` |
| 2 | **compile-dsl** | P4 AST → コード文字列生成 → javac | `DslJavaCodeCalculator` |
| 3 | **ast-hand** | アノテーション駆動ASTを再帰評価 | `AstNumberExpressionEvaluator` |
| 4 | **P4-reflection** | P4 ASTをreflectionで評価 | `GeneratedP4NumberAstEvaluator` |
| 5 | **P4-typed** | P4 ASTをsealed switch で評価 | `TinyExpressionP4Evaluator<T>` 継承 |

---

## 第2話 — compile-hand系 (JavaCodeCalculatorV3)

**N:** compile-hand系って、どういうコードが生成されるんですか？

**S:** `"$a + $b * 2"` なら、こういうJavaコードが生成される:

```java
// ← これが自動生成されるコード
public class Calc_abc123 implements TokenBaseCalculator {
    public Object evaluate(CalculationContext ctx, Token token) {
        float _a = ctx.getValue("a").orElse(0f);
        float _b = ctx.getValue("b").orElse(0f);
        float answer = _a + _b * 2.0f;
        return answer;
    }
}
```

**N:** で、これをjavacでコンパイルして…

**S:** `.class` バイトコードをメモリ上で `ClassLoader` にロード。そのインスタンスを使い回す。

**N:** つまり `calculate()` を呼ぶたびに発生するのは、普通のJavaメソッド呼び出しだけ？

**S:** そう。JITが効けばほぼネイティブコードと同じ速度になる。**定常スループットが必要な高頻度呼び出し**に最適。

**N:** デメリットは？

**S:** 初期化が重い。javacを動かすので、同一プロセス内での初回コンパイルは数十ms〜百数十msかかる。式が変わるたびに再コンパイルが必要。

---

## 第3話 — compile-dsl系 (DslJavaCodeCalculator)

**N:** compile-dsl系は何が違うんですか？

**S:** コードを生成する部分が違う。compile-hand は「パーストークンを手書きロジックで変換する」。compile-dsl は「**P4が生成したマッパーで式をASTにして、そのASTからコードを生成する**」。

```
【compile-hand の流れ】
式文字列 → TinyExpressionParser（手書きパーサー） → トークンツリー → 手書き変換ロジック → Javaコード文字列

【compile-dsl の流れ】
式文字列 → TinyExpressionP4Mapper（UBNF生成）→ P4 AST → DslGeneratedAstJavaEmitter → Javaコード文字列
```

**N:** P4 ASTからJavaコードを作るんですね。具体的にはどうやって？

**S:** `DslGeneratedAstJavaEmitter.renderExpression()` が P4 AST のノード種別を見てコード文字列を組み立てる。例えば `BinaryExpr` なら:

```java
// DslGeneratedAstJavaEmitter — 概略
private String renderNumberExpressionFromBinary(Object binaryExpr, ...) {
    Object left  = readComponent(binaryExpr, "left");   // ← まだreflection
    List   op    = readComponent(binaryExpr, "op");
    List   right = readComponent(binaryExpr, "right");

    String leftCode  = renderNumberExpressionFromBinary(left, ...);
    String operator  = op.get(0).toString();   // "+", "-", "*", "/"
    String rightCode = renderNumberExpressionFromBinary(right.get(0), ...);

    return "(" + leftCode + operator + rightCode + ")";
}
```

結果: `"((3.0f+4.0f)*2.0f)"` のような文字列 → これをクラス定義で包んでjavacに渡す。

**N:** reflection を使っているのが気になりますね。

**S:** ここで使うreflectionはコンパイル時（初期化時）の1回だけ。実行時には影響しない。生成されたJavaコードが実行されるので、定常スループットは compile-hand と同じくらい速い。

**N:** じゃあ compile-hand との違いは「コードを作る部分の作りやすさ」だけですか？

**S:** そう。compile-hand はコード生成ロジックを全部手で書く必要がある。compile-dsl は P4 マッパーが式の構造を型付きで渡してくれるので、**文法が複雑になっても生成ロジックが壊れにくい**。

**N:** ただ現状の `DslGeneratedAstJavaEmitter` を見ると、リテラルしか対応していないみたいですね。

**S:** そう、`isNumericExpressionCandidate()` が常に `false` を返している。変数入りの四則演算は今のところ compile-hand に fallback する。compile-dsl の適用範囲を広げるのは今後の拡張余地。

---

## 第4話 — ast-hand系 (アノテーション駆動ASTトラバーサル)

**N:** `AstNumberExpressionEvaluator` はどういう仕組みですか？

**S:** パーサークラスに直接アノテーションを書く方式。`PlusParser` を見てみて。

```java
// src/main/java/org/unlaxer/tinyexpression/parser/PlusParser.java
@TinyAstNode(kind = TinyAstNodeKind.NUMBER_BINARY)   // ← このノードは2項演算
@TinyAstOperator(symbol = "+")                        // ← 演算子記号
@TinyAstField(name = "left",  childIndex = 1)         // ← 子トークン[1]が左辺
@TinyAstField(name = "right", childIndex = 2)         // ← 子トークン[2]が右辺
public class PlusParser extends SingleCharacterParser implements NumberExpression {
    ...
}
```

**N:** アノテーションだけで「このパーサーが作るノードはこういう構造」と宣言するんですね。

**S:** `NumberGeneratedAstAdapter` がアノテーションを読んで `NumberGeneratedAstNode` ツリーを作る。

```
パース結果トークン                         NumberGeneratedAstNode ツリー
   PlusParser                                  (アノテーションを読んで変換)
   ├── [1] NumberParser("3")
   └── [2] MultipleParser              →   BinaryAstNode("+")
            ├── [1] NumberParser("4")       ├── left:  LiteralAstNode("3")
            └── [2] NumberParser("2")       └── right: BinaryAstNode("*")
                                                        ├── LiteralAstNode("4")
                                                        └── LiteralAstNode("2")
```

**S:** あとは `AstNumberExpressionEvaluator.evaluate()` がツリーを再帰で降りる。reflectionなし、switch文で直接計算。

**N:** シンプルですね。でも「リテラル限定」って聞きましたが？

**S:** `$a` のような変数はパーサークラスに `@TinyAstNode` がないから `tryGenerate()` が empty を返す。その場合は `AstEvaluatorCalculator` が compile-hand に fallback する。

---

## 第5話 — P4-reflection系 (GeneratedP4NumberAstEvaluator)

**N:** P4-reflection版の問題は？

**S:** `GeneratedP4NumberAstEvaluator.evalNode()` の中を見ると分かる:

```java
// 問題のある実装
private Number evalNode(Object node, ...) throws Exception {
    Method leftMethod  = node.getClass().getMethod("left");  // ← 毎回反射
    Method opMethod    = node.getClass().getMethod("op");    // ← 毎回反射
    Method rightMethod = node.getClass().getMethod("right"); // ← 毎回反射

    Object leftObj  = leftMethod.invoke(node);
    Object opObj    = opMethod.invoke(node);
    Object rightObj = rightMethod.invoke(node);
    ...
}
```

**N:** compile-dsl のreflectionと違って、これは実行のたびに呼ばれますね。

**S:** そう、これが問題。compile-dsl のreflectionは初期化1回で済むが、こちらはノード評価のたびに `getMethod()` を呼ぶ。ツリーが深いほど乗算で効く。**技術的負債**として残っている内部コード。

---

## 第6話 — P4-typed系 と Generation Gap Pattern

**S:** 生成された `TinyExpressionP4Evaluator<T>` を見てほしい:

```java
// target/generated-sources/.../TinyExpressionP4Evaluator.java (生成コード、触らない)
public abstract class TinyExpressionP4Evaluator<T> {

    public T eval(TinyExpressionP4AST node) {
        return evalInternal(node);
    }

    private T evalInternal(TinyExpressionP4AST node) {
        return switch (node) {           // sealed interface → exhaustive switch
            case TinyExpressionP4AST.BinaryExpr      n -> evalBinaryExpr(n);
            case TinyExpressionP4AST.IfExpr          n -> evalIfExpr(n);
            case TinyExpressionP4AST.VariableRefExpr n -> evalVariableRefExpr(n);
            case TinyExpressionP4AST.BooleanExpr     n -> evalBooleanExpr(n);
            // ... 全ノード型を網羅（生成コードが保証）
        };
    }

    // ↓ 人間が実装するメソッド群（全てabstract）
    protected abstract T evalBinaryExpr(TinyExpressionP4AST.BinaryExpr node);
    protected abstract T evalIfExpr(TinyExpressionP4AST.IfExpr node);
    protected abstract T evalVariableRefExpr(TinyExpressionP4AST.VariableRefExpr node);
    // ...
}
```

**N:** これが Generation Gap Pattern ですね。生成コードが「振り分け」を担当して、人間は「中身」だけ書く。

**S:** そう。メリットは文法追加時にコンパイルエラーで漏れを静的に検出できること。

```
生成側（触らない）                      人間側
TinyExpressionP4Evaluator<T>    ←──    MyEvaluator extends TinyExpressionP4Evaluator<Float>
  evalInternal() が全ノード振り分け       evalBinaryExpr()   // 実装する
  evalBinaryExpr()   [abstract]           evalIfExpr()       // 実装する
  evalIfExpr()       [abstract]           evalVariableRefExpr() // 実装する
  ...                                     ...
```

**N:** `<T>` には何が入りますか？

**S:** 「この Evaluator が式全体から何を生み出すか」が `T`。ただし**全メソッドが同じ `T` を返す**という制約がある。

```java
TinyExpressionP4Evaluator<Float>    // 数値評価
TinyExpressionP4Evaluator<String>   // デバッグ用文字列化
TinyExpressionP4Evaluator<Object>   // 型混在（Boolean / Float / String）
```

**N:** 型混在の場合は `Object` にするしかないんですね。`evalBooleanExpr()` が `Float` を返すのはおかしいですし。

**S:** そこが `<T>` の限界。`if($a > 1){ "high" }else{ 0 }` のような式は型が混在するので `T = Object` で受けてキャストするしかない。より厳密にやるなら `sealed interface Result { record NumberResult(float value) ... }` のような独自のResult型を `T` に渡す手もある。

**N:** 現状、このクラスを継承した具体クラスはあるんですか？

**S:** ない。`extends TinyExpressionP4Evaluator` でgrepするとゼロ件。フレームワークは生成されているが**まだ誰も使っていない**。

**N:** じゃあP4系の評価は全部 reflection 経由でやってるんですね、今は。

**S:** そういうこと。P4-typed の利用が次の実装課題。

---

## 第7話 — 実際に実装を追加する手順

**N:** 実際に何か新しい評価ロジックを追加するとしたら、どれを選べばいいですか？

**S:** こう考える。

```
新しい式のサポートを追加したい
          │
          ├─[既存文法で表現できる & 評価結果が欲しい]
          │   └─ P4-typed（TinyExpressionP4Evaluator<T> 継承）← おすすめ
          │
          ├─[既存文法で表現できる & 高速なJavaコードに落としたい]
          │   └─ compile-dsl（DslGeneratedAstJavaEmitter を拡張）
          │
          └─[文法自体を追加・変更する]
              ├─ パーサーに @TinyAstNode → ast-hand で評価できるように
              └─ UBNF定義を変更 → P4再生成 → P4-typed で対応
```

### P4-typed での実装手順

**ステップ1:** `TinyExpressionP4Evaluator<T>` を継承。

```java
// 自分のパッケージに作る。generated.p4 パッケージには置かない
public class NumberEvaluator extends TinyExpressionP4Evaluator<Object> {

    private final CalculationContext context;

    public NumberEvaluator(CalculationContext context) {
        this.context = context;
    }

    @Override
    protected Object evalBinaryExpr(TinyExpressionP4AST.BinaryExpr node) {
        // record のアクセサで直接取れる。reflection 不要
        Object left  = eval(node.left());
        String op    = node.op().isEmpty() ? "" : node.op().get(0).toString();
        if (node.right().isEmpty()) return left;     // 末端リテラル
        Object right = eval(node.right().get(0));
        float l = ((Number) left).floatValue();
        float r = ((Number) right).floatValue();
        return switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            case "*" -> l * r;
            case "/" -> l / r;
            default  -> throw new UnsupportedOperationException("unknown op: " + op);
        };
    }

    @Override
    protected Object evalVariableRefExpr(TinyExpressionP4AST.VariableRefExpr node) {
        return context.getValue(node.name()).orElse(0f);
    }

    // IDEで "implement abstract methods" → 残りをスタブ生成
    @Override protected Object evalIfExpr(TinyExpressionP4AST.IfExpr n)         { throw new UnsupportedOperationException(); }
    @Override protected Object evalBooleanExpr(TinyExpressionP4AST.BooleanExpr n) { throw new UnsupportedOperationException(); }
    // ...
}
```

**ステップ2:** 呼び出す。

```java
CalculationContext ctx = ...;
ctx.set("a", 3f);  ctx.set("b", 4f);

TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse("$a*$b+2");  // 1回だけ
Object result = new NumberEvaluator(ctx).eval(ast);                   // 14.0f
```

**N:** record のアクセサで直接フィールドが取れるから reflection ゼロですね。

**S:** そう。新しい演算子が UBNF に追加されてP4が再生成されたとき、`evalNewExpr()` という abstract メソッドが増えてコンパイルエラーになる。実装漏れがコンパイル時にわかる。

---

### compile-dsl での実装手順（高速パスを拡張する場合）

**N:** compile-dsl を拡張して変数入りの四則演算にも対応させるには？

**S:** `DslGeneratedAstJavaEmitter.renderNumberExpressionFromBinary()` の中で現状リテラルしか扱っていない部分を拡張する。`VariableRefExpr` ノードに遭遇したら変数名をコードに埋め込む。

```java
// DslGeneratedAstJavaEmitter の拡張イメージ（現状は未実装）
private String renderLeaf(Object node, ExpressionType numberType) {
    String name = node.getClass().getSimpleName();
    if ("VariableRefExpr".equals(name)) {
        String varName = (String) readComponent(node, "name");
        // → "((Number) calculateContext.getValue(\"a\").orElse(0f)).floatValue()"
        return "calculateContext.getValue(\"" + varName + "\").map(v -> v).orElse(0f)";
    }
    // ... リテラル処理
}
```

**N:** これが完成すれば、変数入りの式でもP4 ASTをベースにしたJavaコードが生成できますね。

**S:** そう。最終的には compile-hand を不要にするのが目標ラインかもしれない。

---

## 第8話 — パフォーマンスの目安

```
【初期化コスト（1式あたり）】
compile-hand/dsl  ████████████████████  〜100ms (javac起動)
ast-hand          ███                   〜数ms   (パース1回)
P4-typed          ██                    〜数ms   (P4マップ1回)

【定常スループット（1呼び出しあたり、JIT後）】
compile-hand/dsl    ▌            〜0.001〜0.01µs  ← ほぼネイティブ
ast-hand (cached)   ██           〜0.1µs前後      ← ツリー再帰
P4-typed (cached)   ██           〜0.1〜0.5µs     ← sealed switch
ast-hand (full)     ████████     〜数µs           ← 毎回パース
P4-reflection       ████████████ 〜数µs           ← getMethod()税
```

**N:** compile-dsl は初期化コストが compile-hand と同じなんですね。

**S:** javac を走らせる部分は共通。compile-dsl はその前段の「コード文字列を作る方法」が違うだけ。

**N:** ast-hand(cached) と P4-typed が同程度というのは？

**S:** どちらも「ASTが既にある前提でツリーを再帰する」コスト。ast-hand は switch+instanceof、P4-typed は sealed switch。JIT後はほぼ同等になる。

---

## 第9話 — どのバックエンドを使うべきか

```
┌──────────────────────────────────────────────────────────────────┐
│ 同じ式を何百万回も繰り返す（式は固定）                           │
│   → compile-hand (JavaCodeCalculatorV3)                          │
├──────────────────────────────────────────────────────────────────┤
│ 式は動的だが高速なJavaコードにしたい（将来）                     │
│   → compile-dsl (DslJavaCodeCalculator) ← 現状リテラルのみ対応  │
├──────────────────────────────────────────────────────────────────┤
│ 式が動的、型安全に評価ロジックを書きたい                         │
│   → P4-typed (TinyExpressionP4Evaluator<T> 継承) ← おすすめ     │
├──────────────────────────────────────────────────────────────────┤
│ パーサー自体を追加・変更している最中                             │
│   → ast-hand (+ @TinyAstNode アノテーション)                     │
├──────────────────────────────────────────────────────────────────┤
│ とにかく動かしたい、細かいことは後で                             │
│   → AstEvaluatorCalculator (全経路試してcompileにfallback)       │
└──────────────────────────────────────────────────────────────────┘
```

**N:** P4-reflection は？

**S:** 意図して選ぶものではない。内部ブリッジコードとして残っているだけ。将来 P4-typed に置き換える対象。

---

## 付録 — クラス対応表・Generation Gap Pattern 適用状況

| バックエンド | エントリクラス | GGP? | 備考 |
|---|---|---|---|
| compile-hand | `JavaCodeCalculatorV3` | ❌ | コード生成ロジック手書き |
| compile-dsl | `DslJavaCodeCalculator` | 半分 | P4マッパーは生成、emitterは手書き |
| ast-hand | `AstEvaluatorCalculator` | ❌ | アノテーション駆動だが dispatch 手書き |
| P4-reflection | `GeneratedP4NumberAstEvaluator` | ❌ | 技術的負債、新規利用非推奨 |
| P4-typed | `TinyExpressionP4Evaluator<T>` 継承 | ✅ | 生成 dispatch + 人間が evalXxx() 実装 |

| 生成ファイル | GGP? | 役割 |
|---|---|---|
| `TinyExpressionP4Evaluator.java` | ✅ | dispatch を生成、人間がサブクラスで実装 |
| `TinyExpressionP4AST.java` | ✅ | sealed record 群、人間は操作するだけ |
| `TinyExpressionP4Mapper.java` | ✅ | 完全生成、触らない |
| `TinyExpressionP4Parsers.java` | ✅ | 完全生成、触らない |

---
[Index](./INDEX.md) | [Next: Parser Generator Comparison & @eval Strategy →](./parser-generator-comparison-and-eval-strategy.ja.md)
