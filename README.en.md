# TinyExpression

[日本語](README.md) | English

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression)

TinyExpression is a Java-embedded expression engine (UDF style) for:

- runtime formula evaluation
- multi-formula execution with dependency ordering
- optional Java code generation and AST-based execution

Roadmap: [docs/TINYEXPRESSION-DSL-ROADMAP.md](docs/TINYEXPRESSION-DSL-ROADMAP.md)

## Current Backend Lineup (2026-02-26)

TinyExpression currently supports four execution backends:

1. `JAVA_CODE` (current production JavaCode path)
2. `JAVA_CODE_LEGACY_ASTCREATOR` (pre-refactor baseline)
3. `AST_EVALUATOR` (AST traversal evaluator)
4. `DSL_JAVA_CODE` (UnlaxerDSL JavaCode seam)

Detailed contract: [docs/TINYEXPRESSION-BACKEND-CONTRACT.md](docs/TINYEXPRESSION-BACKEND-CONTRACT.md)

## Requirements

- Java 21+
- Maven 3.8+

Note: tests/runtime use reflective access and require add-opens options (already configured in [`pom.xml`](pom.xml) surefire/argLine context).

## Maven Dependency

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>tinyExpression</artifactId>
  <version>1.4.10</version>
</dependency>
```

## Quick Start (Single Formula)

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

## Multi Formula Execution (`TinyExpressionsExecutor`)

Class name is `TinyExpressionsExecutor` (plural).

`TinyExpressionsExecutor` itself does not choose backend. It executes cached calculators.  
Backend selection is done while parsing `FormulaInfo` (details in "Backend Configuration").

### 1. Directory Layout

`FileBaseTinyExpressionInstancesCache` expects:

```text
<root>/
  <tenant-id-1>/formulaInfo.txt
  <tenant-id-2>/formulaInfo.txt
```

### 2. `formulaInfo.txt` Minimal Example

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

### 3. Executor Usage Example

```java
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.instances.CalculationResult;
import org.unlaxer.tinyexpression.instances.FileBaseTinyExpressionInstancesCache;
import org.unlaxer.tinyexpression.instances.ResultConsumer;
import org.unlaxer.tinyexpression.instances.TenantID;
import org.unlaxer.tinyexpression.instances.TinyExpressionsExecutor;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class ExecutorExample {
  public static void main(String[] args) {
    FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
        "siteId",
        info -> info.calculatorName);

    // Global default backend for formulas that do not specify backend/executionBackend.
    fields.setExecutionBackend(ExecutionBackend.JAVA_CODE);

    FileBaseTinyExpressionInstancesCache cache = new FileBaseTinyExpressionInstancesCache(
        Path.of("src", "main", "resources", "formula-root"),
        fields);

    CalculationContext ctx = CalculationContext.newConcurrentContext();
    ctx.set("age", 30);

    ResultConsumer resultConsumer = new ResultConsumer() {
      @Override
      public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, Number result) {
        info.getValue("var").ifPresent(name -> c.set(name, result));
      }

      @Override
      public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, String result) {
        info.getValue("var").ifPresent(name -> c.set(name, result));
      }

      @Override
      public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, Boolean result) {
        info.getValue("var").ifPresent(name -> c.set(name, result));
      }

      @Override
      public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, Object result) {
        info.getValue("var").ifPresent(name -> c.setObject(name, result));
      }
    };

    TinyExpressionsExecutor executor = new TinyExpressionsExecutor();
    List<CalculationResult> results = executor.execute(
        TenantID.create(69),
        ctx,
        resultConsumer,
        cache,
        Comparator.comparingInt(Calculator::dependsOnByNestLevel),
        calculator -> true,
        Thread.currentThread().getContextClassLoader());

    System.out.println("executed calculators: " + results.size());
  }
}
```

## `FormulaInfo` Format

Each block is key-value metadata + formula body, delimited by `---END_OF_PART---`.

Common keys:

- `calculatorName`: formula identifier
- `dependsOn`: comma-separated calculator names
- `resultType`: return type (`string`, `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, fully qualified Java type)
- `numberType`: default number literal type
- `formula`: TinyExpression body
- `executionBackend` or `backend`: backend override
- `tags`, `description`: optional metadata
- custom keys (example: `var`, `field`, `checkKind`) are preserved in `FormulaInfo.extraValueByKey`

Practical semantics used in many production integrations:

- `var`: write result into `CalculationContext` variable (typically handled in custom `ResultConsumer`)
- `field`: write result into domain object field (also via `ResultConsumer`)
- `checkKind`: logical output key for score maps / risk maps
- `calculatorName`: stable ID used by `dependsOn`

Embedded Java class block in `formula` is also supported:

~~~text
formula:
```java:sample.v1.CheckDigits
package sample.v1;
import org.unlaxer.tinyexpression.CalculationContext;
public class CheckDigits{
  public boolean check(CalculationContext context, String target){
    return target.matches("\\d+");
  }
}
```
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
~~~

## Backend Configuration

Backend choice is resolved in this order:

1. global default: `FormulaInfoAdditionalFields.executionBackend`  
   (default value is `JAVA_CODE`)
2. per-formula override by `executionBackend` or `backend` key
3. mapped to concrete calculator creator by `CalculatorCreatorRegistry.forBackend(...)`

Canonical backend names:

- `JAVA_CODE`
- `JAVA_CODE_LEGACY_ASTCREATOR`
- `AST_EVALUATOR`
- `DSL_JAVA_CODE`

DAP/runtime aliases (`runtimeMode`) include:

- `token` -> `JAVA_CODE`
- `legacy-astcreator` or `ootc` -> `JAVA_CODE_LEGACY_ASTCREATOR`
- `ast` -> `AST_EVALUATOR`
- `dsl-javacode` -> `DSL_JAVA_CODE`

Related code:

- [src/main/java/org/unlaxer/tinyexpression/loader/FormulaInfoAdditionalFields.java](src/main/java/org/unlaxer/tinyexpression/loader/FormulaInfoAdditionalFields.java)
- [src/main/java/org/unlaxer/tinyexpression/loader/FormulaInfoParser.java](src/main/java/org/unlaxer/tinyexpression/loader/FormulaInfoParser.java)
- [src/main/java/org/unlaxer/tinyexpression/loader/model/CalculatorCreatorRegistry.java](src/main/java/org/unlaxer/tinyexpression/loader/model/CalculatorCreatorRegistry.java)
- [src/main/java/org/unlaxer/tinyexpression/runtime/ExecutionBackend.java](src/main/java/org/unlaxer/tinyexpression/runtime/ExecutionBackend.java)

## TinyExpression Language Quick Reference

This section is a practical syntax guide (not a complete grammar).

### Values and Variables

```text
123
3.14
'text'
"text"
true
false
$age
$name
```

### Numeric / Boolean Operators

```text
1 + 2 * 3
(1 + 2) / 3
10 >= 3
10 == 3
10 != 3
true | false
true & false
true ^ false
not(false)
```

### Conditional and Match

```text
if($age >= 20){100}else{0}

match{
  $countryCode == 'JP' -> 1,
  default -> 0
}
```

### String Utilities (examples)

```text
toUpperCase($name)
toLowerCase($name)
$message.startsWith('hello')
$message.endsWith('world')
$message.contains('abc')
$message[0:3]
```

### Variable Declaration in Formula

```text
variable $gender as string set if not exists 'male' description='gender';
variable $age as number set 18 description='age';
variable $isMember as boolean description='member flag';
```

### External Java Method Call (in formula)

```text
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
```

At runtime, register the Java object in `CalculationContext`:

```java
context.set(new sample.v1.CheckDigits());
```

### User-defined Methods (advanced)

```text
float main(){
  match{
    $age < 18 -> 500,
    default -> call feeByGender($gender)
  }
}

float feeByGender($gender as string){
  match{
    $gender == 'female' -> 1000,
    default -> 1800
  }
}
```

### Comments and Whitespace

- FormulaInfo metadata supports `#` line comments.
- Formula expressions support whitespace and C-style comments such as `/* ... */`.

## How To Integrate TinyExpression Into Your System

### Pattern A: Single Formula Embedded in Service

Use this when formulas are static or deployed with code.

1. Build `CalculationContext` from request/domain model.
2. Compile formula with `JavaCodeCalculatorV3` (or your selected backend).
3. Execute `calculator.apply(context)` and map result.

Good for:

- small number of formulas
- low operational complexity

### Pattern B: Formula Repository + `TinyExpressionsExecutor`

Use this when formulas are tenant-specific or updated outside code release.

1. Store formulas as `formulaInfo.txt` blocks (or your own source mapped to `FormulaInfo`).
2. Load/cache by tenant via `TinyExpressionInstancesCache` implementation.
3. Execute by `TinyExpressionsExecutor` with:
   - `Comparator<Calculator>` for execution order
   - `Predicate<Calculator>` for filtering
   - `ResultConsumer` for output mapping (`var`, `field`, `checkKind` etc.)
4. Keep domain objects/services in `CalculationContext` for external calls.

Good for:

- multitenancy
- business-managed rule updates
- dependency-controlled formula pipelines

### Formula Name Strategy (`FormulaInfoAdditionalFields`)

`FormulaInfo` name resolution is pluggable.  
This is important when some formulas use `calculatorName`, others use `checkKind`.

```java
FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
    "siteId",
    formulaInfo -> {
      String checkKind = formulaInfo.extraValueByKey.get("checkKind");
      return formulaInfo.calculatorName != null ? formulaInfo.calculatorName : checkKind;
    });
```

This extracted name is used by cache/executor-level orchestration logic.

### Result Handling Strategy (`ResultConsumer`)

`TinyExpressionsExecutor` intentionally delegates result handling to `ResultConsumer`.
This design enables:

- writing to context variable (`var`)
- writing to domain object field (`field`)
- writing to custom sinks (logs, metrics, alerting, Slack, queue, DB)

Minimal pattern:

```java
public final class ResultConsumerExample implements ResultConsumer {
  private final CheckResult checkResult;

  public ResultConsumerExample(CheckResult checkResult) {
    this.checkResult = checkResult;
  }

  @Override
  public void accept(CalculationContext ctx, Calculator c, FormulaInfo info, Number result) {
    info.getValue("checkKind").ifPresent(name -> checkResult.suspiciousByKind.put(name, result.floatValue()));
    info.getValue("var").ifPresent(name -> ctx.set(name, result));
    info.getValue("field").ifPresent(name -> setField(checkResult, name, result));
  }

  @Override
  public void accept(CalculationContext ctx, Calculator c, FormulaInfo info, String result) {
    info.getValue("var").ifPresent(name -> ctx.set(name, result));
    info.getValue("field").ifPresent(name -> setField(checkResult, name, result));
  }

  @Override
  public void accept(CalculationContext ctx, Calculator c, FormulaInfo info, Boolean result) {
    info.getValue("var").ifPresent(name -> ctx.set(name, result));
    info.getValue("field").ifPresent(name -> setField(checkResult, name, result));
  }

  @Override
  public void accept(CalculationContext ctx, Calculator c, FormulaInfo info, Object result) {
    info.getValue("var").ifPresent(name -> ctx.setObject(name, result));
    info.getValue("field").ifPresent(name -> setField(checkResult, name, result));
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      target.getClass().getDeclaredField(fieldName).set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
```

### Number Type and Return Type

Two common knobs in `FormulaInfo`:

1. `resultType`: final return type of the formula  
   example: `float`, `double`, `boolean`, `String`
2. `numberType`: numeric literal/arithmetic default type inside the formula  
   example: `numberType:long`

This pair is useful when you need:

- large integer behavior in conditions/arithmetic
- boolean/string return with explicit numeric evaluation type

### Backend Rollout Strategy (Recommended)

1. Keep global default in `FormulaInfoAdditionalFields.setExecutionBackend(...)`.
2. Roll out per-formula using `backend`/`executionBackend`.
3. Compare outputs across backends in test/probe before production switch.

### Recommended Production Boundaries

1. Formula authoring/validation boundary:
   - lint and parser validation before persisting formulas
2. Runtime boundary:
   - deterministic context values only
   - explicit object registration for external functions
3. Observability boundary:
   - log calculator name, backend, tenant, result/error
   - track fallback usage when using AST path

## LSP / DAP / DSL Migration Docs

- backend contract: [docs/TINYEXPRESSION-BACKEND-CONTRACT.md](docs/TINYEXPRESSION-BACKEND-CONTRACT.md)
- UnlaxerDSL handbook: [docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md](docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md)
- migration guide: [docs/TINYEXPRESSION-UNLAXERDSL-MIGRATION-GUIDE.md](docs/TINYEXPRESSION-UNLAXERDSL-MIGRATION-GUIDE.md)
- DAP dual-evaluator plan: [docs/TINYEXPRESSION-DUAL-EVALUATOR-DAP-PLAN.md](docs/TINYEXPRESSION-DUAL-EVALUATOR-DAP-PLAN.md)
- final gap audit: [docs/TINYEXPRESSION-FINAL-GAP-AUDIT.md](docs/TINYEXPRESSION-FINAL-GAP-AUDIT.md)

## Development

```bash
mvn -q test
```

For roadmap context and work history:

- [docs/TINYEXPRESSION-DSL-ROADMAP.md](docs/TINYEXPRESSION-DSL-ROADMAP.md)
- [docs/TINYEXPRESSION-DSL-HANDOVER-2026-02-20.md](docs/TINYEXPRESSION-DSL-HANDOVER-2026-02-20.md)
