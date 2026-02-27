# UBNF → ParseTree → AST → Evaluator → LSP/DAP 実装ガイド

対象: unlaxer-dsl を使って DSL を設計・実装したい人（言語処理初心者）
Branch: `feat-java21-p4-lsp-dap`
Last updated: 2026-02-28

---

## 0. このガイドで作るもの

```
UBNF文法ファイル
    ↓ [unlaxer-dsl コード生成]
Parser / AST / Mapper / Evaluator / LanguageServer / DebugAdapter
    ↓ [手書きコード]
P4AstEvaluatorCalculator  ← 既存Calculatorと同じインターフェース
    ↓ [VSCode拡張]
ユーザーがシンタックスハイライト・型チェック・ステップデバッグができる DSL 環境
```

TinyExpression P4 の例を通じて、unlaxer-dsl を使った DSL の設計から LSP/DAP 統合までの全工程を説明します。

---

## 1. 全体アーキテクチャ

### 1.1 レイヤ図

```
┌─────────────────────────────────────────────────────────┐
│  UBNF Grammar  (tinyexpression-p4.ubnf)                  │
│  → grammar rules + @mapping / @whitespace annotations    │
└──────────────────────┬──────────────────────────────────┘
                       │ unlaxer-dsl mvn generate-sources
┌──────────────────────▼──────────────────────────────────┐
│  Generated Code  (target/generated-sources/...)          │
│  ┌────────────────┐ ┌──────────┐ ┌────────────────────┐ │
│  │ Parsers.java   │ │ AST.java │ │ Mapper.java        │ │
│  │ (Tokenize)     │ │ (sealed  │ │ (Token→AST)        │ │
│  │                │ │  records)│ │                    │ │
│  └────────────────┘ └──────────┘ └────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Evaluator.java  (abstract class, switch pattern)   │  │
│  └────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────┐  │
│  │ LanguageServer.java / DebugAdapter.java (tooling)  │  │
│  └────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                       │ 手書き実装
┌──────────────────────▼──────────────────────────────────┐
│  Runtime Integration                                      │
│  P4AstEvaluatorCalculator  (implements Calculator)        │
│   → P4Mapper.parse() でパース試行                        │
│   → 成功なら _tinyP4ParserUsed=true でマーカー記録       │
│   → 失敗なら AstEvaluatorCalculator へ fallback          │
└─────────────────────────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│  LSP/DAP Tooling (tools/tinyexpression-p4-lsp-vscode/)   │
│  TinyExpressionP4LanguageServerExt                       │
│   → type-safe semantic tokens (instanceof, no regex)     │
│  TinyExpressionP4DebugAdapterExt                         │
│   → AST node path in DAP variables panel                 │
└─────────────────────────────────────────────────────────┘
```

### 1.2 ファイル配置

```
tinyexpression/                              ← メインプロジェクト
  docs/ubnf/tinyexpression-p4-draft.ubnf    ← UBNF文法（ドラフト）
  src/main/java/.../evaluator/p4/
    P4AstEvaluatorCalculator.java            ← P4バックエンド本体
    P4DslJavaCodeCalculator.java             ← P4 DSL バックエンド
  src/main/java/.../runtime/
    ExecutionBackend.java                    ← バックエンドenum
  src/main/java/.../loader/model/
    CalculatorCreatorRegistry.java           ← バックエンド登録

tools/tinyexpression-p4-lsp-vscode/
  grammar/tinyexpression-p4.ubnf            ← UBNF文法（ビルド用）
  pom.xml                                   ← generators設定
  target/generated-sources/tinyexpression-p4/
    runtime/  → Parsers, AST, Mapper, Evaluator
    tooling/  → LanguageServer, DebugAdapter, Launcher
  src/main/java/.../lsp/p4/
    TinyExpressionP4LanguageServerExt.java  ← LSP拡張
    ParseFailureDiagnostics.java            ← sealed interface
  src/main/java/.../dap/p4/
    TinyExpressionP4DebugAdapterExt.java    ← DAP拡張
```

---

## 2. ステップ1: UBNF 文法の書き方

### 2.1 基本構造

```ubnf
grammar TinyExpressionP4 {

  @package: org.unlaxer.tinyexpression.generated.p4
  @whitespace: javaStyle          // コメント・空白を自動スキップ
  @comment: { line: '//' }        // 行コメント

  // トークン定義 (lexer token)
  token NUMBER     = NumberParser    // 数値 (unlaxer built-in)
  token IDENTIFIER = IdentifierParser
  token STRING     = SingleQuotedParser  // 'hello'

  @root
  Formula ::= { VariableDeclaration } { Annotation } Expression ;
```

**ポイント:**
- `@package`: 生成クラスのパッケージ名
- `@whitespace: javaStyle`: スペース・タブ・改行・コメントを文法全体で自動無視
- `token FOO = SomeParser`: unlaxer-common の組み込みパーサーを字句要素として使う
- `@root`: パース開始ルール

### 2.2 ルール定義

```ubnf
// 繰り返し: { rule }
// 省略可能: [ rule ]
// 選択: rule1 | rule2
// グループ: ( rule1 | rule2 )
// リテラル: 'keyword'

NumberExpression ::=
    NUMBER @left
    { ( '+' | '-' | '*' | '/' ) @op  NUMBER @right } ;
```

### 2.3 AST マッピング: `@mapping`

```ubnf
@mapping(BinaryExpr, params=[left, op, right])
BinaryExpr ::=
    NumberExpression @left
    ( '+' | '-' | '*' | '/' ) @op
    NumberExpression @right ;
```

`@mapping(クラス名, params=[フィールド名, ...])` を付けると:
- 生成 `TinyExpressionP4AST` に `BinaryExpr` record が追加される
- `@left`, `@right`, `@op` でキャプチャしたトークンがフィールドになる
- Mapper が Token → AST record への変換コードを自動生成する

### 2.4 完成した AST sealed interface (自動生成)

```java
// target/.../TinyExpressionP4AST.java (自動生成)
public sealed interface TinyExpressionP4AST
    permits TinyExpressionP4AST.BinaryExpr,
            TinyExpressionP4AST.VariableRefExpr,
            TinyExpressionP4AST.IfExpr,
            TinyExpressionP4AST.NumberMatchExpr,
            ... {

  record BinaryExpr(String left, String op, String right)
      implements TinyExpressionP4AST {}

  record IfExpr(TinyExpressionP4AST condition,
                TinyExpressionP4AST thenExpr,
                TinyExpressionP4AST elseExpr)
      implements TinyExpressionP4AST {}

  record VariableRefExpr(String name)
      implements TinyExpressionP4AST {}

  // ...全ルールに対してrecordが生成される
}
```

`sealed interface` + `record` の組み合わせで:
- `instanceof` チェックがコンパイラで exhaustive に保証される
- `switch (ast) { case BinaryExpr n -> ...; }` が全ケースを網羅しないとコンパイルエラー

---

## 3. ステップ2: コード生成

### 3.1 pom.xml 設定

```xml
<plugin>
  <groupId>org.unlaxer</groupId>
  <artifactId>unlaxer-dsl-maven-plugin</artifactId>
  <version>${unlaxer-dsl.version}</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
    </execution>
  </executions>
  <configuration>
    <grammarFile>grammar/tinyexpression-p4.ubnf</grammarFile>
    <!-- 生成するコードの種類 -->
    <generators>Parser,AST,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher</generators>
    <!-- LSPランチャーのmainClass -->
    <mainClass>org.unlaxer.tinyexpression.lsp.p4.TinyExpressionP4LspLauncherExt</mainClass>
    <finalName>tinyexpression-p4-lsp-server</finalName>
    <!-- unlaxer-common バージョン -->
    <unlaxerCommonVersion>2.4.0</unlaxerCommonVersion>
  </configuration>
</plugin>
```

### 3.2 生成コマンド

```bash
cd tools/tinyexpression-p4-lsp-vscode
mvn generate-sources    # Parsers, AST, Mapper, Evaluator, LSP, DAP を生成
mvn compile             # 生成コードをコンパイル
```

### 3.3 生成されるファイル一覧

| ファイル | 役割 | 配置 |
|---------|------|------|
| `TinyExpressionP4Parsers.java` | 文法ルールに対応するパーサー群 | runtime/ |
| `TinyExpressionP4AST.java` | sealed interface + records | runtime/ |
| `TinyExpressionP4Mapper.java` | Token → AST 変換エントリポイント | runtime/ |
| `TinyExpressionP4Evaluator.java` | abstract evaluator (switch pattern) | runtime/ |
| `TinyExpressionP4LanguageServer.java` | LSP サーバー基底クラス | tooling/ |
| `TinyExpressionP4DebugAdapter.java` | DAP アダプター基底クラス | tooling/ |
| `TinyExpressionP4LspLauncher.java` | LSP `main()` (標準入出力 JSON-RPC) | tooling/ |
| `TinyExpressionP4DapLauncher.java` | DAP `main()` (標準入出力 DAP) | tooling/ |

---

## 4. ステップ3: ParseTree (Token) の理解

### 4.1 ParseTree とは

`TinyExpressionP4Parsers.getRootParser().parse(context)` を呼ぶと `Parsed` が返る。
`parsed.getRootToken()` から `Token` ツリーが取得できる。

```
式: "1 + 2"
Token ツリー:
  Formula
    NumberExpression
      NumberFactor
        NUMBER: "1"   ← 葉ノード (isTerminalSymbol=true)
      BinaryOp
        PlusParser: "+"  ← 葉ノード
      NumberFactor
        NUMBER: "2"  ← 葉ノード
```

### 4.2 Token API (unlaxer-common 2.4.0)

```java
Token token = parsed.getRootToken(true);  // true = コメント・空白を除去

// パーサーの型で判定
Parser parser = token.getParser();
if (parser instanceof NumberParser) { /* 数値トークン */ }
if (parser instanceof SingleQuotedParser) { /* 文字列トークン */ }

// ソース位置
int offset = token.source.offsetFromRoot().value();  // 開始オフセット
String text = token.source.toString();               // テキスト内容

// 子ノードの走査 (null安全)
List<Token> children = token.filteredChildren;  // null の場合あり → null check 必要
if (children != null) {
    for (Token child : children) { /* 再帰 */ }
}
```

### 4.3 葉ノードの列挙 (LSP セマンティックトークン用)

```java
// 葉ノード (終端記号) だけを深さ優先で収集するユーティリティ
private List<Token> collectLeaves(Token root) {
    List<Token> leaves = new ArrayList<>();
    collectLeavesRec(root, leaves);
    return leaves;
}

private void collectLeavesRec(Token token, List<Token> acc) {
    if (token == null) return;
    List<Token> children = token.filteredChildren;
    if (children == null || children.isEmpty()) {
        acc.add(token);  // 葉ノード
        return;
    }
    for (Token child : children) {
        collectLeavesRec(child, acc);
    }
}
```

---

## 5. ステップ4: AST と Mapper の使い方

### 5.1 Mapper エントリポイント

```java
// TinyExpressionP4Mapper.java (自動生成)
//
// 1行でパース→AST変換が完了する
TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse("1 + 2");

// 成功すると BinaryExpr, VariableRefExpr などの record が返る
// 失敗すると IllegalArgumentException がスローされる (parse-failed)
```

### 5.2 AST を switch で処理する

```java
// Java 21 sealed interface switch (exhaustive)
String describe(TinyExpressionP4AST node) {
    return switch (node) {
        case TinyExpressionP4AST.BinaryExpr n ->
            n.left() + " " + n.op() + " " + n.right();

        case TinyExpressionP4AST.VariableRefExpr n ->
            "var:" + n.name();

        case TinyExpressionP4AST.IfExpr n ->
            "if(" + describe(n.condition()) + ") ? "
            + describe(n.thenExpr()) + " : " + describe(n.elseExpr());

        case TinyExpressionP4AST.NumberMatchExpr n ->
            "match{...}";

        // ... permits で宣言した全ケースを書かないとコンパイルエラー
        // → 文法追加時に eval 漏れがコンパイル時に検出される
    };
}
```

### 5.3 AST マッピングのデバッグ

```java
TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse("match{1==1->99,default->0}");
System.out.println(ast.getClass().getSimpleName());
// → "NumberMatchExpr"

if (ast instanceof TinyExpressionP4AST.NumberMatchExpr m) {
    System.out.println(m.firstCase());   // NumberCaseExpr
    System.out.println(m.defaultCase()); // NumberDefaultCaseExpr
}
```

---

## 6. ステップ5: Evaluator の実装

### 6.1 生成された abstract Evaluator

```java
// TinyExpressionP4Evaluator.java (自動生成)
public abstract class TinyExpressionP4Evaluator<T> {

    public T eval(TinyExpressionP4AST node) {
        // switch pattern matching で dispatch
        return switch (node) {
            case TinyExpressionP4AST.BinaryExpr n   -> evalBinaryExpr(n);
            case TinyExpressionP4AST.IfExpr n       -> evalIfExpr(n);
            case TinyExpressionP4AST.VariableRefExpr n -> evalVariableRefExpr(n);
            // ... 全ノード型を網羅
        };
    }

    protected abstract T evalBinaryExpr(TinyExpressionP4AST.BinaryExpr node);
    protected abstract T evalIfExpr(TinyExpressionP4AST.IfExpr node);
    protected abstract T evalVariableRefExpr(TinyExpressionP4AST.VariableRefExpr node);
    // ... 全ノード型に対応した abstract メソッド
}
```

### 6.2 具体的なEvaluator実装例

```java
public class NumberEvaluator extends TinyExpressionP4Evaluator<Double> {

    @Override
    protected Double evalBinaryExpr(TinyExpressionP4AST.BinaryExpr n) {
        double left  = Double.parseDouble(n.left());
        double right = Double.parseDouble(n.right());
        return switch (n.op()) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> left / right;
            default  -> throw new UnsupportedOperationException("op: " + n.op());
        };
    }

    @Override
    protected Double evalVariableRefExpr(TinyExpressionP4AST.VariableRefExpr n) {
        // $variable → コンテキストから値を取得
        return context.get(n.name());
    }

    @Override
    protected Double evalIfExpr(TinyExpressionP4AST.IfExpr n) {
        boolean condition = (Boolean) eval(n.condition());
        return condition ? (Double) eval(n.thenExpr()) : (Double) eval(n.elseExpr());
    }

    // ... 全ケースを実装 (missがあるとコンパイルエラー)
}
```

---

## 7. ステップ6: バックエンド統合 (P4AstEvaluatorCalculator)

### 7.1 戦略: try-parse → fallback

```java
public class P4AstEvaluatorCalculator implements Calculator {

    private final AstEvaluatorCalculator delegate;
    private final Map<String, Object> p4Markers = new LinkedHashMap<>();

    public P4AstEvaluatorCalculator(Source source, ...) {
        this.delegate = new AstEvaluatorCalculator(source, ...);
        tryP4Parse(source.source());
    }

    private void tryP4Parse(String formula) {
        try {
            TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse(formula);
            p4Markers.put("_tinyP4ParserUsed", true);
            p4Markers.put("_tinyP4AstNodeType", ast.getClass().getSimpleName());
        } catch (Exception e) {
            // P4 grammar が対応していない構文 → fallback
            p4Markers.put("_tinyP4ParserUsed", false);
            p4Markers.put("_tinyP4AstNodeType", "parse-failed");
        }
    }

    @Override
    public Object apply(CalculationContext ctx) {
        p4Markers.forEach(delegate::setObject);  // マーカーをデリゲートに渡す
        return delegate.apply(ctx);              // 実際の計算は既存エバリュエーターが担当
    }

    @Override
    public <X> X getObject(String key, Class<X> cls) {
        Object marker = p4Markers.get(key);
        if (marker != null && cls.isInstance(marker)) return cls.cast(marker);
        return delegate.getObject(key, cls);     // p4Markers を apply() 前に参照可能
    }
}
```

**設計のポイント:**
1. **コンストラクタでパース試行** → `apply()` 前から `_tinyP4ParserUsed` を参照できる
2. **fallback あり** → P4 grammar が対応していない構文でも既存評価器で動く
3. **delegate パターン** → 既存の `Calculator` インターフェースをそのまま使える

### 7.2 ExecutionBackend への登録

```java
// ExecutionBackend.java に追加
P4_AST_EVALUATOR("p4-ast",
    Set.of("p4-ast", "p4-ast-evaluator")),
P4_DSL_JAVA_CODE("p4-dsl-javacode",
    Set.of("p4-dsl-javacode", "p4-dsl-java-code"));

// CalculatorCreatorRegistry.java に追加
public static CalculatorCreator p4AstEvaluatorCreator() {
    return (source, className, types, cl) ->
        new P4AstEvaluatorCalculator(source, className, types, cl);
}
```

---

## 8. ステップ7: LSP サーバーの型安全拡張

### 8.1 生成された LanguageServer の限界

自動生成 `TinyExpressionP4LanguageServer` は:
- Semantic tokens legend が `["valid", "invalid"]` のみ
- `semanticTokensFull()` が空実装
- diagnostics が `"Parse error at offset X"` という文字列のみ
- hover が `"Valid"` か `"Parse error"` だけ

→ 実用的な LSP には手書きの拡張クラスが必要

### 8.2 型安全な Semantic Token 分類

正規表現 **ではなく**、Token の `getParser()` が返す **Parser 型** で分類する:

```java
// TinyExpressionP4LanguageServerExt.java
private static final int KEYWORD = 0, VARIABLE = 1, NUMBER = 2,
                          STRING  = 3, OPERATOR = 4, FUNCTION = 5, COMMENT = 6;

private static final Set<String> KEYWORD_SET = Set.of(
    "if", "else", "match", "default", "var", "variable", "call",
    "as", "number", "string", "boolean", "object", "float",
    "set", "not", "exists", "description", "true", "false"
);

private int classifyLeafToken(Token token) {
    Parser parser = token.getParser();
    // instanceof で Parser 型を判定 (型安全 → 正規表現不要)
    if (parser instanceof CPPComment)          return COMMENT;
    if (parser instanceof SpaceParser)         return -1;  // skip
    if (parser instanceof NumberParser)        return NUMBER;
    if (parser instanceof SingleQuotedParser)  return STRING;

    String text = token.source.toString();
    if (KEYWORD_SET.contains(text))            return KEYWORD;
    if (OPERATOR_SET.contains(text))           return OPERATOR;
    if (text.startsWith("$"))                  return VARIABLE;
    if (parser instanceof IdentifierParser)    return VARIABLE;

    return -1;  // 分類不能 (括弧・区切り等)
}
```

**なぜ正規表現ではなく instanceof か:**
- コメント内の `if` キーワードを誤ってハイライトしない（位置情報は Token が持つ）
- Parser 定義が変われば自動的に追従する
- コンパイル時に型チェックが効く

### 8.3 ParseFailureDiagnostics の sealed interface

```java
// ParseFailureDiagnostics.java
public sealed interface ParseFailureDiagnostics
    permits ParseFailureDiagnostics.Absent,
            ParseFailureDiagnostics.Present {

    record Absent() implements ParseFailureDiagnostics {
        public boolean hasFailure()       { return false; }
        public int failureOffset()        { return 0; }
        public List<String> expectedHints() { return List.of(); }
    }

    record Present(int failureOffset, List<String> expectedHints)
        implements ParseFailureDiagnostics {
        public boolean hasFailure() { return true; }
    }

    static ParseFailureDiagnostics absent() { return new Absent(); }
    static ParseFailureDiagnostics present(int offset, List<String> hints) {
        return new Present(offset, hints);
    }
}
```

**使い方 (exhaustive switch):**
```java
String hoverText = switch (state.failures()) {
    case ParseFailureDiagnostics.Absent a ->
        "**" + astNodeType + "**";  // パース成功時はAST型表示
    case ParseFailureDiagnostics.Present p ->
        "Parse error at offset " + p.failureOffset();
};
// → ケース漏れがあるとコンパイルエラー
```

### 8.4 LSP Semantic Tokens エンコード形式

LSP の semantic tokens は **相対位置エンコード** を使う:

```java
// [deltaLine, deltaStartChar, length, tokenType, tokenModifiers]
List<Integer> data = new ArrayList<>();
int prevLine = 0, prevChar = 0;

for (SemanticToken tok : tokens) {
    int deltaLine = tok.line - prevLine;
    int deltaChar = deltaLine == 0
        ? tok.startChar - prevChar
        : tok.startChar;  // 行が変わったらchar位置はリセット

    data.addAll(List.of(deltaLine, deltaChar, tok.length, tok.tokenType, 0));
    prevLine = tok.line;
    prevChar = tok.startChar;
}
```

---

## 9. ステップ8: DAP アダプターの型安全拡張

### 9.1 生成 DebugAdapter の限界

- `runtimeMode`, `sourceContent` 等のフィールドが `private`
- P4 固有の runtime markers が表示されない
- AST のノードパスが変数パネルに出ない

### 9.2 launch/configurationDone のオーバーライド

```java
// TinyExpressionP4DebugAdapterExt.java
private String capturedProgram;
private String capturedRuntimeMode;

@Override
public CompletableFuture<Void> launch(Map<String, Object> args) {
    // 生成クラスのsuper.launch()が呼ぶ前に自分でもキャプチャ
    this.capturedProgram     = (String) args.getOrDefault("program", "");
    this.capturedRuntimeMode = (String) args.getOrDefault("runtimeMode", "p4-ast");
    return super.launch(args);
}

@Override
public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
    return super.configurationDone(args)
        .thenRun(this::runP4Probe);  // super完了後にP4プローブを実行
}

private void runP4Probe() {
    try {
        TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse(capturedProgram);
        this.p4ParserUsed  = true;
        this.p4AstNodeType = ast.getClass().getSimpleName();
        this.p4AstNodePath = buildAstNodePath(ast, 3);  // depth=3
    } catch (Exception e) {
        this.p4ParserUsed  = false;
        this.p4AstNodeType = "parse-failed";
    }
}
```

### 9.3 variables レスポンスへの追加

```java
@Override
public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
    return super.variables(args).thenApply(response -> {
        List<Variable> vars = response.getVariables();
        // P4 固有の情報を追加
        if (p4ParserUsed != null) {
            vars.add(makeVar("_tinyP4ParserUsed",  String.valueOf(p4ParserUsed)));
            vars.add(makeVar("_tinyP4AstNodeType", p4AstNodeType));
            vars.add(makeVar("_tinyP4AstNodePath", p4AstNodePath));
        }
        return response;
    });
}
```

### 9.4 AST ノードパスの型安全な構築

```java
private String buildAstNodePath(TinyExpressionP4AST node, int depth) {
    if (depth == 0 || node == null) return "";
    // sealed interface switch で AST を再帰的に表現
    String name = switch (node) {
        case TinyExpressionP4AST.BinaryExpr n ->
            "BinaryExpr(" + n.op() + ")";
        case TinyExpressionP4AST.IfExpr n ->
            "IfExpr";
        case TinyExpressionP4AST.NumberMatchExpr n ->
            "NumberMatchExpr";
        case TinyExpressionP4AST.VariableRefExpr n ->
            "VariableRefExpr(" + n.name() + ")";
        // ... 全ケース網羅 (漏れるとコンパイルエラー)
    };
    // 子ノードを深さ優先で連結
    TinyExpressionP4AST child = firstChild(node);
    if (child == null) return name;
    return name + " → " + buildAstNodePath(child, depth - 1);
}
```

---

## 10. ステップ9: VSCode 拡張のパッケージング

### 10.1 package.json の最小構成

```json
{
  "name": "tinyexpression-p4-lsp",
  "contributes": {
    "languages": [{
      "id": "tinyexpressionP4",
      "extensions": [".tinyexp"],
      "configuration": "./language-configuration.json"
    }],
    "debuggers": [{
      "type": "tinyexpressionP4",
      "label": "TinyExpression P4 Debug"
    }],
    "configuration": {
      "title": "TinyExpression P4 LSP",
      "properties": {
        "tinyExpressionP4Lsp.server.javaPath": { "type": "string", "default": "java" },
        "tinyExpressionP4Lsp.server.jarPath":  { "type": "string", "default": "" },
        "tinyExpressionP4Lsp.server.jvmArgs":  { "type": "array",  "default": [] }
      }
    }
  }
}
```

### 10.2 extension.ts の LSP/DAP 起動

```typescript
// LSP サーバー: fat jar を -jar で直接起動
const serverOptions: ServerOptions = {
    command: javaPath,
    args: [...jvmArgs, "--enable-preview", "-jar", jarPath]
};

// DAP アダプター: fat jar の -cp で別の main class を起動
// (LSP Launcher と DAP Launcher は同一 JAR に同居)
const dapFactory = {
    createDebugAdapterDescriptor(_session) {
        return new vscode.DebugAdapterExecutable(javaPath, [
            ...jvmArgs, "--enable-preview",
            "-cp", jarPath,
            "org.unlaxer.tinyexpression.dap.p4.TinyExpressionP4DapLauncherExt"
        ]);
    }
};
```

**なぜ LSP は `-jar`、DAP は `-cp` か:**
- `-jar` はマニフェストの `Main-Class` を使う → LSP の main class を自動的に起動
- DAP は別の main class が必要 → `-cp` でクラスパス指定し main class を直接指定
- 両方が同じ fat JAR を共有するので jar ファイルは1つだけ

### 10.3 JAR ビルド (Maven Shade Plugin)

```bash
cd tools/tinyexpression-p4-lsp-vscode
mvn package -DskipTests    # → target/tinyexpression-p4-lsp-server.jar (fat jar)
# server-dist/tinyexpression-p4-lsp-server.jar にもコピーされる
```

---

## 11. 開発上のトラブルシューティング

### 11.1 `mvn generate-sources` が失敗する

```
原因: unlaxer-dsl SNAPSHOT が古い
対処:
  cd ../unlaxer-dsl
  mvn -q -DskipTests install
  cd ../tinyexpression-p4-lsp-vscode
  mvn generate-sources
```

### 11.2 生成クラスのプライベートフィールドにアクセスできない

```
問題: TinyExpressionP4LanguageServer.client が private
対処: override connect(LanguageClient client) でキャプチャ

@Override
public void connect(LanguageClient client) {
    super.connect(client);
    this.extClient = client;  // 自分のフィールドに保存
}
```

### 11.3 `getObject()` が apply() 前に null を返す

```
問題: P4AstEvaluatorCalculator の p4Markers が apply() まで delegate に転送されない
対処: getObject() を override して p4Markers を直接参照する

@Override
public <X> X getObject(String key, Class<X> cls) {
    Object v = p4Markers.get(key);
    if (v != null && cls.isInstance(v)) return cls.cast(v);
    return delegate.getObject(key, cls);
}
```

### 11.4 テストのコンパイルエラー (`int` → `CodePointIndex`)

```
原因: unlaxer-common 2.4.0 で int オフセットが CodePointIndex に変更
対処: TokenTest.java の position 変数を CodePointIndex でラップ

// 変更前
int position = formula.indexOf("x");
// 変更後
CodePointIndex position = new CodePointIndex(formula.indexOf("x"));
```

### 11.5 DSL バックエンドが `(a+b)*(c+d)` を誤計算

```
既知問題: DSL_JAVA_CODE / P4_DSL_JAVA_CODE は nested parenthesis の乗算に不具合あり
          (10-2)*(7-3) → 3.0 (正しくは 32.0)
対処: P4_AST_EVALUATOR を使う (AstEvaluatorCalculator ベースなので正確)
```

---

## 12. 全体の流れのまとめ

```
①  tinyexpression-p4.ubnf を書く
    → grammar rules + @mapping(AstNode, params=[...]) を定義

②  mvn generate-sources
    → Parsers / AST / Mapper / Evaluator / LSP / DAP が生成される

③  P4AstEvaluatorCalculator を実装
    → TinyExpressionP4Mapper.parse() で試行
    → 成功: _tinyP4ParserUsed=true + AstEvaluatorCalculator に委譲
    → 失敗: _tinyP4ParserUsed=false + AstEvaluatorCalculator に fallback

④  TinyExpressionP4LanguageServerExt を実装
    → instanceof による型安全 semantic token 分類
    → ParseFailureDiagnostics sealed interface で型安全 diagnostic

⑤  TinyExpressionP4DebugAdapterExt を実装
    → launch() で formula と runtimeMode をキャプチャ
    → variables() で P4 markers と AST ノードパスを追加

⑥  mvn package → fat jar 生成
    → extension.ts で LSP (-jar) / DAP (-cp + mainClass) 起動
```

---

## 参考: 関連ファイル一覧

| ファイル | 内容 |
|---------|------|
| `docs/ubnf/tinyexpression-p4-draft.ubnf` | P4 UBNF 文法 (ドラフト) |
| `tools/.../grammar/tinyexpression-p4.ubnf` | P4 UBNF 文法 (ビルド用) |
| `tools/.../pom.xml` | generators 設定 |
| `src/main/java/.../evaluator/p4/P4AstEvaluatorCalculator.java` | P4 バックエンド |
| `src/main/java/.../runtime/ExecutionBackend.java` | バックエンド enum |
| `src/main/java/.../loader/model/CalculatorCreatorRegistry.java` | バックエンド登録 |
| `tools/.../lsp/p4/TinyExpressionP4LanguageServerExt.java` | LSP 拡張 |
| `tools/.../lsp/p4/ParseFailureDiagnostics.java` | sealed diagnostic |
| `tools/.../dap/p4/TinyExpressionP4DebugAdapterExt.java` | DAP 拡張 |
| `tools/.../src/extension.ts` | VSCode 拡張エントリポイント |
| `src/test/java/.../p4/P4BackendParityTest.java` | パリティテスト |
| `docs/TINYEXPRESSION-P4-LSP-DAP-TASKS.md` | 実装タスクトラッカー |
