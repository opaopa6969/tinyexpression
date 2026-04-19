# TinyExpression

[日本語](README.md) | English

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression)

A Java-embedded expression engine (UDF style) for runtime formula evaluation.

- Runtime formula evaluation
- Multi-formula execution with dependency ordering
- 6 execution backends (JavaCode / AST / P4 series)
- LSP / DAP support (VS Code extension)

**Docs**: [getting-started](docs/getting-started.md) | [language-guide](docs/language-guide.md) | [backends](docs/backends.md) | [architecture](docs/architecture.md)

**IDE**: [tinyexpression-group/tinyexpression-ide](https://github.com/tinyexpression-group/tinyexpression-ide) — VS Code extension (LSP + DAP)

---

## Table of Contents

- [Requirements](#requirements)
- [Maven Dependency](#maven-dependency)
- [Quick Start](#quick-start)
- [Multi-Formula Execution](#multi-formula-execution)
- [FormulaInfo Format](#formulainfo-format)
- [Java Code Blocks (Security Warning)](#java-code-blocks-security-warning)
- [Backend Configuration](#backend-configuration)
- [Language Quick Reference](#language-quick-reference)
- [LSP / DAP](#lsp--dap)
- [Development](#development)

---

## Requirements

- Java 21+
- Maven 3.8+

Note: tests/runtime use reflective access and require `add-opens` options (configured in [`pom.xml`](pom.xml)).

---

## Maven Dependency

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>tinyExpression</artifactId>
  <version>1.4.10</version>
</dependency>
```

---

## Quick Start

```java
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class QuickStart {
  public static void main(String[] args) {
    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set("gender", "male");

    String formula = "if($gender=='male'){500}else{1000}";
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        new Source(formula),
        "QuickStartCalculator",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    float v1 = ((Number) calculator.apply(context)).floatValue();
    context.set("gender", "female");
    float v2 = ((Number) calculator.apply(context)).floatValue();

    System.out.println(v1); // 500.0
    System.out.println(v2); // 1000.0
  }
}
```

---

## Multi-Formula Execution

`TinyExpressionsExecutor` (plural) executes multiple formulas in dependency order.

### Directory Layout

```text
<root>/
  <tenant-id>/formulaInfo.txt
```

### formulaInfo.txt Example

```text
tags:NORMAL
description:base score
siteId:69
calculatorName:baseScore
var:baseScore
resultType:float
formula:
if($age >= 20){100}else{0}
---END_OF_PART---

tags:NORMAL
description:bonus score
siteId:69
calculatorName:bonusScore
dependsOn:baseScore
var:finalScore
backend:AST_EVALUATOR
resultType:float
formula:
$baseScore + 10
---END_OF_PART---
```

### Executor Code

```java
FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
    "siteId",
    info -> info.calculatorName);
fields.setExecutionBackend(ExecutionBackend.JAVA_CODE);

FileBaseTinyExpressionInstancesCache cache = new FileBaseTinyExpressionInstancesCache(
    Path.of("src", "main", "resources", "formula-root"),
    fields);

CalculationContext ctx = CalculationContext.newConcurrentContext();
ctx.set("age", 30);

TinyExpressionsExecutor executor = new TinyExpressionsExecutor();
List<CalculationResult> results = executor.execute(
    TenantID.create(69),
    ctx,
    resultConsumer,
    cache,
    Comparator.comparingInt(Calculator::dependsOnByNestLevel),
    calculator -> true,
    Thread.currentThread().getContextClassLoader());
```

See [docs/getting-started.md](docs/getting-started.md) for a full walkthrough.

---

## FormulaInfo Format

Each block is `key:value` metadata + formula body, delimited by `---END_OF_PART---`.

| Key | Description |
|-----|-------------|
| `calculatorName` | formula identifier |
| `dependsOn` | comma-separated dependency names |
| `resultType` | return type (`string`, `boolean`, `float`, `double`, FQCN, etc.) |
| `numberType` | default number literal type inside the formula |
| `formula` | formula body |
| `executionBackend` / `backend` | backend override |
| `var` | write result to `CalculationContext` variable |
| `field` | write result to domain object field |
| `checkKind` | output key for score/risk maps |

---

## Java Code Blocks (Security Warning)

> **Warning**: Java code blocks execute arbitrary code on the JVM. Do **not** use this feature in environments where untrusted users can submit formulas.

You can embed a Java class directly inside a `formula` field:

~~~text
formula:
```java:sample.v1.CheckDigits
package sample.v1;
import org.unlaxer.tinyexpression.CalculationContext;
public class CheckDigits {
  public boolean check(CalculationContext context, String target) {
    return target.matches("\\d+");
  }
}
```
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
~~~

See [docs/language-guide.md](docs/language-guide.md) for details.

---

## Backend Configuration

Resolution order:

1. Global default: `FormulaInfoAdditionalFields.setExecutionBackend(...)` (default: `JAVA_CODE`)
2. Per-formula override: `executionBackend` / `backend` key
3. Implementation mapping: `CalculatorCreatorRegistry.forBackend(...)`

| Backend Name | Description |
|-------------|-------------|
| `JAVA_CODE` | Current production JavaCode (recommended) |
| `JAVA_CODE_LEGACY_ASTCREATOR` | Pre-refactor baseline (frozen) |
| `AST_EVALUATOR` | AST traversal evaluator |
| `DSL_JAVA_CODE` | DSL JavaCode seam (hybrid) |
| `P4_AST_EVALUATOR` | UBNF-generated parser + AST evaluation (PRIMARY) |
| `P4_DSL_JAVA_CODE` | UBNF-generated parser + DSL JavaCode |

DAP/runtime aliases: `token`, `ast`, `dsl-javacode`, `p4-ast`, `p4-dsl-javacode`

See [docs/backends.md](docs/backends.md) for details.

---

## Language Quick Reference

```text
# Variables
$age  $name  $isMember

# Arithmetic
1 + 2 * 3    (1 + 2) / 3

# Comparison / Logic
10 >= 3    10 == 3    10 != 3
true | false    true & false    not(false)

# Conditional
if($age >= 20){100}else{0}

# match
match{
  $code == 'JP' -> 1,
  default -> 0
}

# Strings
toUpperCase($name)    $msg.startsWith('hello')    $msg[0:3]

# Variable declaration
variable $gender as string set if not exists 'male' description='gender';

# External method
import sample.v1.Checker#check as check;
if(external returning as boolean check($input)){1}else{0}
```

Full specification: [docs/language-guide.md](docs/language-guide.md)

---

## LSP / DAP

The [tinyexpression-p4-lsp-vscode](tools/tinyexpression-p4-lsp-vscode/README.md) VS Code extension provides:

- Syntax highlighting and semantic tokens
- Diagnostics (TE001 parse errors)
- Completion and hover
- DAP debugging with 6-backend parity comparison

External repository: [tinyexpression-group/tinyexpression-ide](https://github.com/tinyexpression-group/tinyexpression-ide)

---

## Development

```bash
mvn -q test
```

Document index: [docs/INDEX.md](docs/INDEX.md)
