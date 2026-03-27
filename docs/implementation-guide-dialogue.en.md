[English](./implementation-guide-dialogue.en.md) | [日本語](./implementation-guide-dialogue.ja.md) | [Index](./INDEX.md)

---

# tinyexpression Implementation Guide — Learning the 5 Backends through Dialogue

> **Characters**
> - **Senior (S)**: A senior developer who knows the tinyexpression design well
> - **Newcomer (N)**: A developer encountering this library for the first time

---

## Episode 1 — What Exactly Is a "Backend"?

**N:** Senior, there are so many similar-looking classes like `JavaCodeCalculatorV3` and `AstEvaluatorCalculator`. What's the difference between them?

**S:** The goal is the same: evaluate an expression like `"$a + $b * 2"`. But *how* the evaluation happens differs. That's the backend. There are currently 5 families.

```
  Expression string
     |
  +--+--------------------------------------------+
  |                                                |
  v                                                v
[compile family]                             [AST family]
  |                                                |
  +--[1] compile-hand                 +------------+------------------+
  |      Hand-written codegen -> javac|            |                  |
  |                                   v            v                  v
  +--[2] compile-dsl          [3] ast-hand  [4] P4-reflection  [5] P4-typed
        P4 AST -> codegen -> javac  Annotation    reflection        sealed switch
```

**N:** So there are two compile-family backends.

**S:** Right. Both ultimately compile with javac and execute `.class` files. The difference is *how* the code is generated. Here's the summary of all 5:

| # | Name | Approach | Key Class |
|---|------|----------|-----------|
| 1 | **compile-hand** | Hand-written codegen logic -> javac | `JavaCodeCalculatorV3` |
| 2 | **compile-dsl** | P4 AST -> code string generation -> javac | `DslJavaCodeCalculator` |
| 3 | **ast-hand** | Annotation-driven AST with recursive evaluation | `AstNumberExpressionEvaluator` |
| 4 | **P4-reflection** | Evaluate P4 AST via reflection | `GeneratedP4NumberAstEvaluator` |
| 5 | **P4-typed** | Evaluate P4 AST via sealed switch | Subclass of `TinyExpressionP4Evaluator<T>` |

---

## Episode 2 — The compile-hand Family (JavaCodeCalculatorV3)

**N:** What kind of code does compile-hand generate?

**S:** For `"$a + $b * 2"`, Java code like this is generated:

```java
// <- This is the auto-generated code
public class Calc_abc123 implements TokenBaseCalculator {
    public Object evaluate(CalculationContext ctx, Token token) {
        float _a = ctx.getValue("a").orElse(0f);
        float _b = ctx.getValue("b").orElse(0f);
        float answer = _a + _b * 2.0f;
        return answer;
    }
}
```

**N:** And then javac compiles this...

**S:** The `.class` bytecode is loaded in-memory via a `ClassLoader`. That instance is reused.

**N:** So every time `calculate()` is called, it's just an ordinary Java method invocation?

**S:** Exactly. Once JIT kicks in, it runs at nearly native speed. **Ideal for high-frequency calls requiring sustained throughput.**

**N:** What are the downsides?

**S:** Initialization is heavy. Running javac means the first compilation in a process costs tens to over a hundred milliseconds. Recompilation is needed every time the expression changes.

---

## Episode 3 — The compile-dsl Family (DslJavaCodeCalculator)

**N:** How is compile-dsl different?

**S:** The code generation part differs. compile-hand uses "hand-written logic to transform parse tokens." compile-dsl uses "**a P4-generated mapper to convert the expression into an AST, then generates code from that AST**."

```
[compile-hand flow]
Expression string -> TinyExpressionParser (hand-written) -> Token tree -> Hand-written transform logic -> Java code string

[compile-dsl flow]
Expression string -> TinyExpressionP4Mapper (UBNF-generated) -> P4 AST -> DslGeneratedAstJavaEmitter -> Java code string
```

**N:** So it creates Java code from the P4 AST. How exactly?

**S:** `DslGeneratedAstJavaEmitter.renderExpression()` examines the P4 AST node types and assembles code strings. For example, for `BinaryExpr`:

```java
// DslGeneratedAstJavaEmitter — outline
private String renderNumberExpressionFromBinary(Object binaryExpr, ...) {
    Object left  = readComponent(binaryExpr, "left");   // <- still using reflection
    List   op    = readComponent(binaryExpr, "op");
    List   right = readComponent(binaryExpr, "right");

    String leftCode  = renderNumberExpressionFromBinary(left, ...);
    String operator  = op.get(0).toString();   // "+", "-", "*", "/"
    String rightCode = renderNumberExpressionFromBinary(right.get(0), ...);

    return "(" + leftCode + operator + rightCode + ")";
}
```

Result: a string like `"((3.0f+4.0f)*2.0f)"` -- which gets wrapped in a class definition and passed to javac.

**N:** The use of reflection concerns me.

**S:** The reflection here is used only once at compile time (initialization). It doesn't affect runtime. The generated Java code is what actually runs, so sustained throughput is comparable to compile-hand.

**N:** So the only difference from compile-hand is "ease of writing the code generation part"?

**S:** Right. compile-hand requires writing all code generation logic by hand. compile-dsl has the P4 mapper provide the expression structure in a typed way, so **the generation logic is more resilient as grammar grows more complex**.

**N:** However, looking at the current `DslGeneratedAstJavaEmitter`, it seems to only support literals.

**S:** Correct, `isNumericExpressionCandidate()` always returns `false`. Arithmetic with variables currently falls back to compile-hand. Expanding compile-dsl's coverage is future work.

---

## Episode 4 — The ast-hand Family (Annotation-Driven AST Traversal)

**N:** How does `AstNumberExpressionEvaluator` work?

**S:** It's an approach where annotations are written directly on parser classes. Look at `PlusParser`.

```java
// src/main/java/org/unlaxer/tinyexpression/parser/PlusParser.java
@TinyAstNode(kind = TinyAstNodeKind.NUMBER_BINARY)   // <- This node is a binary operation
@TinyAstOperator(symbol = "+")                        // <- Operator symbol
@TinyAstField(name = "left",  childIndex = 1)         // <- Child token[1] is the left operand
@TinyAstField(name = "right", childIndex = 2)         // <- Child token[2] is the right operand
public class PlusParser extends SingleCharacterParser implements NumberExpression {
    ...
}
```

**N:** So annotations alone declare "this parser produces a node with this structure."

**S:** `NumberGeneratedAstAdapter` reads the annotations and builds a `NumberGeneratedAstNode` tree.

```
Parse result token                           NumberGeneratedAstNode tree
   PlusParser                                  (converted by reading annotations)
   +-- [1] NumberParser("3")
   +-- [2] MultipleParser              ->   BinaryAstNode("+")
            +-- [1] NumberParser("4")       +-- left:  LiteralAstNode("3")
            +-- [2] NumberParser("2")       +-- right: BinaryAstNode("*")
                                                        +-- LiteralAstNode("4")
                                                        +-- LiteralAstNode("2")
```

**S:** Then `AstNumberExpressionEvaluator.evaluate()` recursively traverses the tree. No reflection, direct calculation via switch statements.

**N:** That's simple. But I heard it's "limited to literals"?

**S:** Variables like `$a` don't have `@TinyAstNode` on their parser class, so `tryGenerate()` returns empty. In that case, `AstEvaluatorCalculator` falls back to compile-hand.

---

## Episode 5 — The P4-reflection Family (GeneratedP4NumberAstEvaluator)

**N:** What's the problem with the P4-reflection version?

**S:** You can see it inside `GeneratedP4NumberAstEvaluator.evalNode()`:

```java
// The problematic implementation
private Number evalNode(Object node, ...) throws Exception {
    Method leftMethod  = node.getClass().getMethod("left");  // <- reflection every time
    Method opMethod    = node.getClass().getMethod("op");    // <- reflection every time
    Method rightMethod = node.getClass().getMethod("right"); // <- reflection every time

    Object leftObj  = leftMethod.invoke(node);
    Object opObj    = opMethod.invoke(node);
    Object rightObj = rightMethod.invoke(node);
    ...
}
```

**N:** Unlike compile-dsl's reflection, this is called on every execution.

**S:** Exactly, that's the problem. compile-dsl's reflection is a one-time initialization cost, but this one calls `getMethod()` on every node evaluation. The deeper the tree, the more it compounds. **This is technical debt** -- internal code that remains.

---

## Episode 6 — The P4-typed Family and the Generation Gap Pattern

**S:** Take a look at the generated `TinyExpressionP4Evaluator<T>`:

```java
// target/generated-sources/.../TinyExpressionP4Evaluator.java (generated code, don't touch)
public abstract class TinyExpressionP4Evaluator<T> {

    public T eval(TinyExpressionP4AST node) {
        return evalInternal(node);
    }

    private T evalInternal(TinyExpressionP4AST node) {
        return switch (node) {           // sealed interface -> exhaustive switch
            case TinyExpressionP4AST.BinaryExpr      n -> evalBinaryExpr(n);
            case TinyExpressionP4AST.IfExpr          n -> evalIfExpr(n);
            case TinyExpressionP4AST.VariableRefExpr n -> evalVariableRefExpr(n);
            case TinyExpressionP4AST.BooleanExpr     n -> evalBooleanExpr(n);
            // ... all node types covered (guaranteed by generated code)
        };
    }

    // Methods below are for humans to implement (all abstract)
    protected abstract T evalBinaryExpr(TinyExpressionP4AST.BinaryExpr node);
    protected abstract T evalIfExpr(TinyExpressionP4AST.IfExpr node);
    protected abstract T evalVariableRefExpr(TinyExpressionP4AST.VariableRefExpr node);
    // ...
}
```

**N:** This is the Generation Gap Pattern. The generated code handles "dispatch" and humans write the "implementation."

**S:** Right. The benefit is that when grammar is added, missing cases are caught statically as compile errors.

```
Generated side (don't touch)                Human side
TinyExpressionP4Evaluator<T>    <--    MyEvaluator extends TinyExpressionP4Evaluator<Float>
  evalInternal() dispatches all nodes       evalBinaryExpr()   // implement this
  evalBinaryExpr()   [abstract]             evalIfExpr()       // implement this
  evalIfExpr()       [abstract]             evalVariableRefExpr() // implement this
  ...                                       ...
```

**N:** What goes into `<T>`?

**S:** "What this Evaluator produces from the entire expression" is `T`. However, there's a constraint that **all methods return the same `T`**.

```java
TinyExpressionP4Evaluator<Float>    // numeric evaluation
TinyExpressionP4Evaluator<String>   // debug stringification
TinyExpressionP4Evaluator<Object>   // mixed types (Boolean / Float / String)
```

**N:** For mixed types, you have no choice but to use `Object`. It would be odd for `evalBooleanExpr()` to return `Float`.

**S:** That's the limitation of `<T>`. An expression like `if($a > 1){ "high" }else{ 0 }` has mixed types, so you'd have to use `T = Object` and cast. For stricter typing, you could pass a custom Result type like `sealed interface Result { record NumberResult(float value) ... }` as `T`.

**N:** Are there any concrete classes that currently extend this?

**S:** None. Grepping for `extends TinyExpressionP4Evaluator` yields zero hits. The framework is generated but **nobody is using it yet**.

**N:** So all P4-based evaluation is done via reflection for now.

**S:** Exactly. Using P4-typed is the next implementation task.

---

## Episode 7 — Steps to Add a New Implementation

**N:** If I were to actually add new evaluation logic, which backend should I choose?

**S:** Think of it this way.

```
Want to add support for a new expression
          |
          +--[Can be expressed with existing grammar & want evaluation results]
          |   +-- P4-typed (extend TinyExpressionP4Evaluator<T>) <- recommended
          |
          +--[Can be expressed with existing grammar & want fast Java code]
          |   +-- compile-dsl (extend DslGeneratedAstJavaEmitter)
          |
          +--[Modifying/adding the grammar itself]
              +-- Add @TinyAstNode to parser -> enable ast-hand evaluation
              +-- Modify UBNF definition -> regenerate P4 -> support via P4-typed
```

### Implementation Steps with P4-typed

**Step 1:** Extend `TinyExpressionP4Evaluator<T>`.

```java
// Create in your own package. Do NOT place in the generated.p4 package
public class NumberEvaluator extends TinyExpressionP4Evaluator<Object> {

    private final CalculationContext context;

    public NumberEvaluator(CalculationContext context) {
        this.context = context;
    }

    @Override
    protected Object evalBinaryExpr(TinyExpressionP4AST.BinaryExpr node) {
        // Accessed directly via record accessors. No reflection needed
        Object left  = eval(node.left());
        String op    = node.op().isEmpty() ? "" : node.op().get(0).toString();
        if (node.right().isEmpty()) return left;     // terminal literal
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

    // Use IDE's "implement abstract methods" -> generates remaining stubs
    @Override protected Object evalIfExpr(TinyExpressionP4AST.IfExpr n)         { throw new UnsupportedOperationException(); }
    @Override protected Object evalBooleanExpr(TinyExpressionP4AST.BooleanExpr n) { throw new UnsupportedOperationException(); }
    // ...
}
```

**Step 2:** Call it.

```java
CalculationContext ctx = ...;
ctx.set("a", 3f);  ctx.set("b", 4f);

TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse("$a*$b+2");  // once only
Object result = new NumberEvaluator(ctx).eval(ast);                   // 14.0f
```

**N:** Since record accessors provide direct field access, there's zero reflection.

**S:** Right. When a new operator is added to UBNF and P4 is regenerated, a new `evalNewExpr()` abstract method appears and causes a compile error. Missing implementations are caught at compile time.

---

### Implementation Steps with compile-dsl (Extending the Fast Path)

**N:** How would I extend compile-dsl to also support arithmetic with variables?

**S:** Extend the part of `DslGeneratedAstJavaEmitter.renderNumberExpressionFromBinary()` that currently only handles literals. When encountering a `VariableRefExpr` node, embed the variable name into the code.

```java
// DslGeneratedAstJavaEmitter extension sketch (currently unimplemented)
private String renderLeaf(Object node, ExpressionType numberType) {
    String name = node.getClass().getSimpleName();
    if ("VariableRefExpr".equals(name)) {
        String varName = (String) readComponent(node, "name");
        // -> "((Number) calculateContext.getValue(\"a\").orElse(0f)).floatValue()"
        return "calculateContext.getValue(\"" + varName + "\").map(v -> v).orElse(0f)";
    }
    // ... literal handling
}
```

**N:** Once this is complete, Java code based on P4 AST can be generated even for expressions with variables.

**S:** Right. Ultimately, the goal might be to make compile-hand unnecessary.

---

## Episode 8 — Performance Estimates

```
[Initialization Cost (per expression)]
compile-hand/dsl  ████████████████████  ~100ms (javac startup)
ast-hand          ███                   ~a few ms (parse once)
P4-typed          ██                    ~a few ms (P4 map once)

[Sustained Throughput (per invocation, after JIT)]
compile-hand/dsl    |            ~0.001-0.01us  <- nearly native
ast-hand (cached)   ██           ~0.1us         <- tree recursion
P4-typed (cached)   ██           ~0.1-0.5us     <- sealed switch
ast-hand (full)     ████████     ~several us    <- parse every time
P4-reflection       ████████████ ~several us    <- getMethod() tax
```

**N:** compile-dsl has the same initialization cost as compile-hand.

**S:** The javac portion is shared. compile-dsl only differs in *how* the code string is produced beforehand.

**N:** ast-hand (cached) and P4-typed are about the same?

**S:** Both involve the cost of "recursively traversing an AST that already exists." ast-hand uses switch+instanceof; P4-typed uses sealed switch. After JIT, they're roughly equivalent.

---

## Episode 9 — Which Backend Should You Use?

```
+------------------------------------------------------------------+
| Repeating the same expression millions of times (expression fixed)|
|   -> compile-hand (JavaCodeCalculatorV3)                          |
+------------------------------------------------------------------+
| Dynamic expressions but want fast Java code (future)              |
|   -> compile-dsl (DslJavaCodeCalculator) <- currently literals    |
+------------------------------------------------------------------+
| Dynamic expressions, want type-safe evaluation logic              |
|   -> P4-typed (extend TinyExpressionP4Evaluator<T>) <- recommended|
+------------------------------------------------------------------+
| Currently adding/modifying the parser itself                      |
|   -> ast-hand (+ @TinyAstNode annotations)                       |
+------------------------------------------------------------------+
| Just want it to work, details later                               |
|   -> AstEvaluatorCalculator (tries all paths, falls back to      |
|      compile)                                                     |
+------------------------------------------------------------------+
```

**N:** What about P4-reflection?

**S:** It's not something you'd intentionally choose. It remains only as internal bridge code. It's a candidate for replacement by P4-typed in the future.

---

## Appendix — Class Correspondence Table & Generation Gap Pattern Status

| Backend | Entry Class | GGP? | Notes |
|---|---|---|---|
| compile-hand | `JavaCodeCalculatorV3` | No | Code generation logic is hand-written |
| compile-dsl | `DslJavaCodeCalculator` | Partial | P4 mapper is generated, emitter is hand-written |
| ast-hand | `AstEvaluatorCalculator` | No | Annotation-driven but dispatch is hand-written |
| P4-reflection | `GeneratedP4NumberAstEvaluator` | No | Technical debt, not recommended for new use |
| P4-typed | Subclass of `TinyExpressionP4Evaluator<T>` | Yes | Generated dispatch + human implements evalXxx() |

| Generated File | GGP? | Role |
|---|---|---|
| `TinyExpressionP4Evaluator.java` | Yes | Generates dispatch, human implements via subclass |
| `TinyExpressionP4AST.java` | Yes | Sealed record group, human only operates on them |
| `TinyExpressionP4Mapper.java` | Yes | Fully generated, don't touch |
| `TinyExpressionP4Parsers.java` | Yes | Fully generated, don't touch |

---
[Index](./INDEX.md) | [Next: Parser Generator Comparison & @eval Strategy ->](./parser-generator-comparison-and-eval-strategy.en.md)
