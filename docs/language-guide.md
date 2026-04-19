# TinyExpression Language Guide

[日本語版](language-guide-ja.md)

Complete language specification for TinyExpression v1.4.10.

---

## Table of Contents

- [Literals](#literals)
- [Variables](#variables)
- [Operators](#operators)
- [Conditional Expressions](#conditional-expressions)
- [String Functions](#string-functions)
- [Variable Declarations](#variable-declarations)
- [User-Defined Methods](#user-defined-methods)
- [External Java Methods](#external-java-methods)
- [Java Code Blocks](#java-code-blocks)
- [Comments](#comments)
- [Type Hints](#type-hints)

---

## Literals

### Numeric Literals

```text
123        integer
3.14       decimal
-42        negative integer
1.5e3      scientific notation (= 1500.0)
```

- Signed prefix (`+`, `-`) supported
- Scientific notation (`e` / `E`) supported
- Default type is controlled by `numberType` in `FormulaInfo` (default: `float`)

### String Literals

```text
'hello'    single-quoted
"hello"    double-quoted
```

- Either quote style accepted; they are interchangeable
- Basic Java escape sequences apply (`\n`, `\t`, `\\`, etc.)

### Boolean Literals

```text
true
false
```

---

## Variables

Variables are prefixed with `$`.

```text
$age
$name
$isMember
```

- Values are resolved from `CalculationContext`
- A variable reference to a name not set in context returns `null`
- Variable names are case-sensitive

---

## Operators

### Arithmetic Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `+` | Addition | `1 + 2` |
| `-` | Subtraction | `5 - 3` |
| `*` | Multiplication | `2 * 3` |
| `/` | Division | `10 / 3` |

### Comparison Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `==` | Equal | `$age == 20` |
| `!=` | Not equal | `$age != 20` |
| `>` | Greater than | `$age > 18` |
| `>=` | Greater or equal | `$age >= 18` |
| `<` | Less than | `$age < 65` |
| `<=` | Less or equal | `$age <= 65` |

String equality (`==`, `!=`) uses `String.equals()` internally.

### Boolean Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `&` | Logical AND | `$a & $b` |
| `\|` | Logical OR | `$a \| $b` |
| `^` | Logical XOR | `$a ^ $b` |
| `not()` | Logical NOT | `not($flag)` |

### Operator Precedence (high to low)

1. `()` — parentheses
2. `not()` — negation
3. `*`, `/` — multiplicative
4. `+`, `-` — additive
5. `>`, `>=`, `<`, `<=` — relational
6. `==`, `!=` — equality
7. `^` — XOR
8. `&` — AND
9. `|` — OR

---

## Conditional Expressions

### if / else

```text
if(condition){thenValue}else{elseValue}
```

- `condition` must be a boolean expression
- Both `then` and `else` branches are required
- Returns the value of the matching branch

Example:

```text
if($age >= 20){100}else{0}
```

### Ternary Expression

```text
condition ? thenValue : elseValue
```

Equivalent to `if`/`else`. Supported in P4 backends.

### match

```text
match{
  condition1 -> value1,
  condition2 -> value2,
  default -> defaultValue
}
```

- Evaluated top to bottom; first matching condition wins
- `default` branch is strongly recommended (SHOULD)
- Cases separated by commas

Example:

```text
match{
  $countryCode == 'JP' -> 1,
  $countryCode == 'US' -> 2,
  default -> 0
}
```

---

## String Functions

### Built-in Functions

| Function | Description | Example |
|----------|-------------|---------|
| `toUpperCase(s)` | Uppercase | `toUpperCase($name)` |
| `toLowerCase(s)` | Lowercase | `toLowerCase($name)` |
| `trim(s)` | Trim whitespace | `trim($input)` |
| `length(s)` | String length | `length($name)` |
| `toNum(s)` | Parse to number | `toNum($numStr)` |

### Method-Style Operations

| Method | Description | Example |
|--------|-------------|---------|
| `.startsWith(str)` | Prefix match | `$msg.startsWith('hello')` |
| `.endsWith(str)` | Suffix match | `$msg.endsWith('world')` |
| `.contains(str)` | Substring match | `$msg.contains('abc')` |
| `.isPresent()` | Non-null / non-empty check | `$name.isPresent()` |

### String Slice

```text
$message[0:3]    characters at index 0, 1, 2 (end exclusive)
```

### String Concatenation

```text
$firstName + ' ' + $lastName
```

The `+` operator performs string concatenation when operands are strings.

---

## Variable Declarations

Variable declarations define defaults and type hints for formula inputs.

```text
variable $name as type set [if not exists] defaultValue description='description';
var $name as type set defaultValue description='description';
```

| Part | Description |
|------|-------------|
| `variable` / `var` | Declaration keyword |
| `$name` | Variable name (must start with `$`) |
| `as type` | Type hint: `number`, `string`, `boolean`, `object`, `float` |
| `set` | Assign default value |
| `if not exists` | Only set if the variable has no current value in context |
| `description='...'` | Human-readable description (used by LSP hover) |

Examples:

```text
variable $gender as string set if not exists 'male' description='gender';
variable $age as number set 18 description='age in years';
variable $isMember as boolean description='membership flag';
```

---

## User-Defined Methods

Formulas can define multiple named methods.

```text
returnType methodName($param as type, ...){
  body
}
```

- The entry point must be named `main`
- Method calls use `call methodName(args)`
- Supported return types: `float`, `string`, `boolean`, `object`

Example:

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

---

## External Java Methods

Call Java methods from the host application inside a formula.

### Import Declaration

```text
import package.ClassName#methodName as alias;
```

### Call Syntax

```text
external returning as returnType alias(arg1, arg2, ...)
```

### Example

Formula:

```text
import sample.v1.CheckDigits#check as checkDigits;
if(external returning as boolean checkDigits($input)){1}else{0}
```

Java setup:

```java
// Register the object in CalculationContext before executing the formula
context.set(new sample.v1.CheckDigits());
```

Java class:

```java
package sample.v1;
import org.unlaxer.tinyexpression.CalculationContext;

public class CheckDigits {
  public boolean check(CalculationContext context, String target) {
    return target.matches("\\d+");
  }
}
```

---

## Java Code Blocks

> **Security Warning**: Java code blocks compile and execute arbitrary Java code on the JVM at formula evaluation time. Only enable this feature when formula authors are fully trusted. Do not expose this capability to untrusted users.
>
> **Risk**: Any Java code, including file system access, network calls, or `System.exit()`, can be embedded. Sandbox control is the responsibility of the host application.

A Java class can be embedded directly inside a `formula` field using triple-backtick syntax:

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

The class is compiled in-memory and loaded via `MemoryClassLoader`. The compiled bytecode is scoped to the formula's class loader and is not persisted to disk.

See [decisions/ADR-003-java-codeblock-safety.md](decisions/ADR-003-java-codeblock-safety.md) for the security decision record.

---

## Comments

| Context | Syntax | Scope |
|---------|--------|-------|
| FormulaInfo metadata | `# comment` | Lines starting with `#` in the key-value block |
| Formula body | `/* comment */` | Block comment anywhere in the formula |

---

## Type Hints

### ExpressionTypes

| Type Name | Java Type | Notes |
|-----------|-----------|-------|
| `byte` | `Byte` | |
| `short` | `Short` | |
| `int` | `Integer` | |
| `long` | `Long` | |
| `float` | `Float` | Recommended default |
| `double` | `Double` | |
| `number` | `Float` | Alias for `float` |
| `string` | `String` | |
| `boolean` | `Boolean` | |
| `object` | `Object` | |
| `bigDecimal` | `BigDecimal` | Limited expression support |
| `bigInteger` | `BigInteger` | Limited expression support |
| `timestamp` | `Timestamp` | Special purpose |

### Type Promotion in Arithmetic

When two operands have different numeric types, the wider type wins:

```
double > float > long > int
```

Example: `1 + 2.0` → result type is `float` (or `double` if operand is `double`).

---

## Built-in Math Functions

| Function | Description | Example |
|----------|-------------|---------|
| `min(a, b, ...)` | Minimum (2+ arguments) | `min($a, $b, 0)` |
| `max(a, b, ...)` | Maximum (2+ arguments) | `max($a, $b, 100)` |
| `abs(n)` | Absolute value | `abs($score)` |
| `floor(n)` | Floor | `floor($value)` |
| `ceil(n)` | Ceiling | `ceil($value)` |

## Built-in Date/Time Functions

| Function | Description |
|----------|-------------|
| `inTimeRange($ts, start, end)` | True if timestamp is in range |
| `inDayTimeRange($ts, start, end)` | True if time-of-day is in range |
