[English](./parser-generator-comparison-and-eval-strategy.en.md) | [日本語](./parser-generator-comparison-and-eval-strategy.ja.md) | [Index](./INDEX.md)

---

# Parser Generator Comparison & @eval Strategy Design

## 1. unlaxer-parser vs Other Parser Generators

### Comparison Table

| Aspect | ANTLR | PEG.js / pest | Tree-sitter | **unlaxer (P4 UBNF)** |
|--------|-------|---------------|-------------|----------------------|
| **Grammar notation** | EBNF-like | PEG | JS-like DSL | UBNF (EBNF extension) |
| **Parsing method** | LL(*), ALL(*) | PEG (ordered choice) | GLR | PEG (ordered choice) |
| **Typed AST generation** | Visitor/Listener (generic) | None | S-expression | **sealed interface records** |
| **LSP generation** | Requires hand-writing | None | Built-in | **Auto-generated** |
| **DAP generation** | None | None | None | **Auto-generated** |
| **Evaluator generation** | None | None | None | **GGP skeleton generation** |
| **Java code emitter** | None | None | None | **Generatable** |
| **Mapper generation** | None (hand-written Visitor) | None | None | **Auto-generated** |
| **Ecosystem** | Huge | Medium | Large | Small |
| **Documentation** | Rich | Medium | Rich | Limited |
| **Language support** | Java, C#, Python, JS, etc. | JS | C, many bindings | **Java only** |

### Unique Advantages of unlaxer

1. **End-to-end from grammar to LSP/DAP** -- Building an LSP with ANTLR requires thousands of lines of hand-written code
2. **sealed interface + records AST** -- Full utilization of Java 21's type safety. ANTLR's ParseTree is untyped
3. **GGP (Generation Gap Pattern)** -- Separation of generated and hand-written code is built into the design
4. **`@mapping` annotation** -- AST structure is declaratively defined within the grammar. ANTLR requires writing Visitors in separate files
5. **`@eval` extension potential** -- Being able to write evaluation strategy in the grammar is unique

### Honest Shortcomings

1. **Small ecosystem** -- ANTLR has millions of users
2. **Limited documentation** -- specs/ has content, but tutorials and Q&A are lacking
3. **Java only** -- No expansion to other languages (JVM languages can use it)
4. **Weak error recovery** -- Being PEG-based, diagnostic messages on parse failure are insufficient
5. **Potential MapperGenerator bugs** -- Undiscovered bugs may exist with complex grammars

### Overall Assessment

unlaxer-parser is an "integrated development environment for DSL development." While ANTLR is a "parser generator,"
unlaxer generates everything from grammar definitions: parser, AST, mapper, evaluator, LSP, and DAP.
With the `@eval` extension, it could become a world where "writing grammar alone gives you a working language processor."

---

## 2. @eval Strategy Design

### Overview

The `@eval` annotation is added to UBNF grammar, and the EvaluatorGenerator auto-generates concrete implementations.
The strategy parameter allows selection of the generation method.

### Strategy List

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `default` | Standard implementation built into the generator | Most cases |
| `template("file.java.tmpl")` | Expanded from an external template file | BigDecimal special handling, logging, performance counters, etc. |
| `manual` | Left as abstract (human writes it) | Cases requiring fully custom implementation |

### UBNF Syntax Examples

```ubnf
// Strategy: default -- generator produces standard implementation
@mapping(BinaryExpr, params=[left, op, right])
@leftAssoc
@precedence(level=10)
@eval(kind=binary_arithmetic, strategy=default)
NumberExpression ::= NumberTerm @left { AddOp @op NumberTerm @right } ;

// Strategy: template -- generated from external template
@mapping(BinaryExpr, params=[left, op, right])
@leftAssoc
@precedence(level=20)
@eval(kind=binary_arithmetic, strategy=template("custom-term-eval.java.tmpl"))
NumberTerm ::= NumberFactor @left { MulOp @op NumberFactor @right } ;

// Strategy: manual -- left as abstract
@mapping(MethodInvocationExpr, params=[name])
@eval(kind=invocation, strategy=manual)
MethodInvocation ::= MethodInvocationHeader IDENTIFIER @name '(' [ Arguments ] ')' ;

// Default behavior example
@mapping(VariableRefExpr, params=[name])
@eval(kind=variable_ref, strip_prefix="$")
VariableRef ::= '$' IDENTIFIER @name ;

@mapping(IfExpr, params=[condition, thenExpr, elseExpr])
@eval(kind=conditional)
IfExpression ::= 'if' '(' BooleanExpression @condition ')' '{' Expression @thenExpr '}' 'else' '{' Expression @elseExpr '}' ;
```

### @eval kind List

| kind | Description | Generated Code |
|------|-------------|---------------|
| `binary_arithmetic` | Left-associative binary operation | leaf detection + recursive eval + applyBinary |
| `variable_ref` | Variable reference | strip prefix + context.getXxx() |
| `conditional` | if/else branching | condition eval + branch selection |
| `match_case` | Pattern matching | sequential case eval + default |
| `literal` | Literal value | parseNumber/Boolean/String |
| `comparison` | Comparison operation | left/right eval + compareTo |
| `invocation` | Method invocation | (typically manual) |
| `passthrough` | Return value as-is | eval(node.value()) |

### Template Variables

Variables available within template files (`.java.tmpl`):

```
{{left}}        -- Expression to get the eval result of the left operand
{{right}}       -- Expression to get the eval result of the right operand ({{right[i]}} for lists)
{{op}}          -- Operator string ({{op[i]}} for lists)
{{node}}        -- Reference to the current AST node
{{resultType}}  -- Result type (ExpressionType)
{{numberType}}  -- Number type (ExpressionType)
{{context}}     -- CalculationContext reference
{{eval(expr)}}  -- Call to recursively evaluate any AST expression
```

### Generated Class Structure

```
TinyExpressionP4Evaluator<T>           <- generated (abstract, GGP base)
  +-- P4DefaultAstEvaluator<Object>    <- generated (@eval default implementations)
  |     evalBinaryExpr()               <- generated from @eval(kind=binary_arithmetic)
  |     evalVariableRefExpr()          <- generated from @eval(kind=variable_ref)
  |     evalIfExpr()                   <- generated from @eval(kind=conditional)
  |     evalMethodInvocationExpr()     <- abstract (strategy=manual)
  |
  +-- P4DefaultJavaCodeEmitter<String> <- generated (Java code generation version)
  |     evalBinaryExpr()               <- Java code string version generated from @eval
  |     ...
  |
  +-- MyCustomEvaluator<Object>        <- hand-written (extends P4DefaultAstEvaluator)
        evalMethodInvocationExpr()     <- implementation of manual strategy
        evalBinaryExpr()               <- override if needed
```

---
[<- Previous: Implementation Guide](./implementation-guide-dialogue.en.md) | [Index](./INDEX.md)
