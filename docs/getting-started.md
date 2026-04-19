# Getting Started with TinyExpression

[日本語版](getting-started-ja.md)

This guide walks you through integrating TinyExpression v1.4.10 into a Java project.

---

## Prerequisites

- Java 21+
- Maven 3.8+

---

## Maven Setup

Add the dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>tinyExpression</artifactId>
  <version>1.4.10</version>
</dependency>
```

If you run tests or use reflective access at runtime, add `add-opens` to the Surefire plugin:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <argLine>
      --add-opens java.base/java.lang=ALL-UNNAMED
      --add-opens java.base/java.util=ALL-UNNAMED
    </argLine>
  </configuration>
</plugin>
```

---

## Pattern A: Single Formula

Use this pattern when formulas are static or bundled with code.

### Step 1: Create a CalculationContext

```java
CalculationContext context = CalculationContext.newConcurrentContext();
context.set("age", 25);
context.set("gender", "male");
```

`newConcurrentContext()` returns a thread-safe context. You can safely share the same context across threads.

### Step 2: Compile the Formula

```java
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
    new Source("if($age >= 20){100}else{0}"),
    "AgeCheckCalc",                          // generated class name (must be valid Java identifier)
    new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
    Thread.currentThread().getContextClassLoader());
```

`JavaCodeCalculatorV3` compiles the formula to Java bytecode at construction time. Reuse the `calculator` instance across multiple `apply()` calls — it is stateless after construction.

### Step 3: Evaluate

```java
float result = ((Number) calculator.apply(context)).floatValue();
System.out.println(result); // 100.0
```

---

## Pattern B: Multi-Formula with TinyExpressionsExecutor

Use this pattern for tenant-specific rules, business-managed formulas, or dependency-controlled pipelines.

### Step 1: Create the Formula Directory

```
src/main/resources/formula-root/
  69/formulaInfo.txt
```

### Step 2: Write formulaInfo.txt

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
resultType:float
formula:
$baseScore + 10
---END_OF_PART---
```

The `dependsOn:baseScore` field ensures `baseScore` is evaluated before `bonusScore`.

### Step 3: Configure FormulaInfoAdditionalFields

```java
FormulaInfoAdditionalFields fields = new FormulaInfoAdditionalFields(
    "siteId",                          // partition key field name
    info -> info.calculatorName);      // name extractor: how to identify each formula

// Set global default backend (optional, default is JAVA_CODE)
fields.setExecutionBackend(ExecutionBackend.JAVA_CODE);
```

### Step 4: Build the Cache

```java
FileBaseTinyExpressionInstancesCache cache = new FileBaseTinyExpressionInstancesCache(
    Path.of("src", "main", "resources", "formula-root"),
    fields);
```

The cache loads and compiles formulas lazily per tenant.

### Step 5: Implement ResultConsumer

`ResultConsumer` receives each formula's result and decides what to do with it.

```java
ResultConsumer resultConsumer = new ResultConsumer() {
  @Override
  public void accept(CalculationContext c, Calculator calculator, FormulaInfo info, Number result) {
    // Write result to context variable named by "var" key
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
```

### Step 6: Execute

```java
CalculationContext ctx = CalculationContext.newConcurrentContext();
ctx.set("age", 30);

TinyExpressionsExecutor executor = new TinyExpressionsExecutor();
List<CalculationResult> results = executor.execute(
    TenantID.create(69),
    ctx,
    resultConsumer,
    cache,
    Comparator.comparingInt(Calculator::dependsOnByNestLevel), // dependency order
    calculator -> true,                                         // include all formulas
    Thread.currentThread().getContextClassLoader());

System.out.println("executed: " + results.size()); // 2
System.out.println("finalScore: " + ctx.get("finalScore")); // 110.0
```

---

## Choosing a Backend

The recommended production setup:

1. Set the global default to `JAVA_CODE` (already the default)
2. Override per-formula only when needed (e.g., `backend:P4_AST_EVALUATOR` for P4 grammar coverage)
3. Run parity tests before switching backends in production

See [docs/backends.md](backends.md) for a full backend comparison.

---

## External Java Methods

To call a Java method from a formula:

1. Implement the method in a Java class

```java
package myapp;
import org.unlaxer.tinyexpression.CalculationContext;

public class RiskChecker {
  public boolean isHighRisk(CalculationContext context, String region) {
    return "HIGH_RISK".equals(region);
  }
}
```

2. Register the object in the context before execution

```java
context.set(new myapp.RiskChecker());
```

3. Reference it in the formula

```text
import myapp.RiskChecker#isHighRisk as isHighRisk;
if(external returning as boolean isHighRisk($region)){100}else{0}
```

---

## Next Steps

- [Language Guide](language-guide.md) — complete language specification
- [Backends](backends.md) — 6 backend comparison and fallback chain
- [Architecture](architecture.md) — parser, AST, evaluator internals
