# Feature Parity Diff: Hand-Written Path vs UBNF (P4) Path

Last updated: 2026-03-27

## Overview

This document is a comprehensive feature-level diff between the **hand-written parser path**
(Java classes under `org.unlaxer.tinyexpression.parser.*`) and the **UBNF grammar path**
(`tinyexpression-p4.ubnf`). It identifies parity, gaps, and partial coverage to guide
DGE (Dual Grammar Engine) convergence work.

Key:
- **PARITY** -- Both paths support the feature
- **HAND-ONLY** -- Feature exists only in hand-written parsers
- **UBNF-ONLY** -- Feature exists only in the UBNF grammar
- **PARTIAL** -- Feature exists in both but with differing scope or semantics

---

## 1. Top-Level Structure

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Root formula (code + imports + vars + annotations + expr + methods) | `TinyExpressionParser` | `Formula` rule | PARITY |
| Fenced Java code blocks (` ```scheme:ClassName `) | `CodesParser`, `CodeParser`, `CodeStartParser`, `CodeEndParser` | `CodeBlock`, `CodeStart`, `CodeBody`, `CodeEnd` | PARITY |
| Import declarations (`import ... as ...;`) | `ImportsParser`, `ImportParser` | `ImportDeclaration` | PARITY |
| Import with `#method` hash syntax | `JavaClassMethodParser` in `ImportParser` | `'#' IDENTIFIER @method` in `ImportDeclaration` | PARITY |
| Variable declarations (`var $x ...;`) | `VariableDeclarationsParser`, `VariableDeclarationParser` | `VariableDeclaration` (4 typed variants) | PARITY |
| Annotations (`@name(params)`) | `AnnotationsParser`, `AnnotationParser` | `Annotation` rule | PARITY |
| Method declarations (typed return, params, body) | `MethodsParser`, `MethodChoiceParser`, `NumberMethodParser`, `StringMethodParser`, `BooleanMethodParser`, `ObjectMethodParser` | `MethodDeclaration` (4 typed variants with `@scopeTree`, `@declares`) | PARITY |
| EOF sentinel | Implicit (chain ends) | `EOF = EndOfSourceParser` | UBNF-ONLY |

---

## 2. Numeric Expressions

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Addition / subtraction (`+`, `-`) | `NumberExpressionParser` (Expression = Term { AddOp Term }) | `NumberExpression` with `AddOp` | PARITY |
| Multiplication / division (`*`, `/`) | `NumberTermParser` (Term = Factor { MulOp Factor }) | `NumberTerm` with `MulOp` | PARITY |
| Number literals | `NumberParser` | `NUMBER = NumberParser` | PARITY |
| Number variables (`$var`) | `NumberVariableParser`, `NumberPrefixedVariableParser`, `NumberSuffixedVariableParser` | `VariableRef` (untyped, polymorphic) | PARTIAL |
| Parenthesized number expressions | `ParenthesesParser(NumberExpressionParser)` | `'(' NumberExpression ')'` in `NumberFactor` | PARITY |
| Left-associative precedence (add < mul) | Structural (Expression > Term > Factor) | `@leftAssoc @precedence(level=10/20)` | PARITY |

### Number Type System (Concrete Types)

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Int/Long/Short/Byte/Float/Double cast parsers | `IntCastParser`, `LongCastParser`, `ShortCastParser`, `ByteCastParser`, `FloatCastParser`, `DoubleCastParser` | -- | HAND-ONLY |
| BigDecimal / BigInteger cast parsers | `BigDecimalCastParser`, `BigIntegerCastParser` | -- | HAND-ONLY |
| Prefix number parsers (e.g., `int:42`) | `IntPrefixNumberParser`, `FloatPrefixNumberParser`, etc. | -- | HAND-ONLY |
| Suffix number parsers (e.g., `42f`, `42L`) | `FloatSuffixNumberParser`, `DoubleSuffixNumberParser`, `LongSuffixNumberParser` | -- | HAND-ONLY |
| Number class parsers | `NumberClassParser`, `NumberClassName`, `MultipleTypeNumberParser` | -- | HAND-ONLY |
| Concrete number type expressions | `numbertype.NumberExpressionParser`, `numbertype.NumberTermParser` | -- | HAND-ONLY |

---

## 3. Math Functions

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `sin(x)` | `SinParser` | `SinFunction` / `SinExpr` | PARITY |
| `cos(x)` | `CosParser` | `CosFunction` / `CosExpr` | PARITY |
| `tan(x)` | `TanParser` | `TanFunction` / `TanExpr` | PARITY |
| `sqrt(x)` | `SquareRootParser` | `SqrtFunction` / `SqrtExpr` | PARITY |
| `min(x, ...)` | `MinParser` | `MinFunction` / `MinExpr` | PARITY |
| `max(x, ...)` | `MaxParser` | `MaxFunction` / `MaxExpr` | PARITY |
| `random()` | `RandomParser` | `RandomFunction` / `RandomExpr` | PARITY |
| `abs(x)` | `AbsParser` | `AbsFunction` / `AbsExpr` | PARITY |
| `round(x)` | `RoundParser` | `RoundFunction` / `RoundExpr` | PARITY |
| `ceil(x)` | `CeilParser` | `CeilFunction` / `CeilExpr` | PARITY |
| `floor(x)` | `FloorParser` | `FloorFunction` / `FloorExpr` | PARITY |
| `pow(x, y)` | `PowParser` | `PowFunction` / `PowExpr` | PARITY |
| `log(x)` | `LogParser` | `LogFunction` / `LogExpr` | PARITY |
| `exp(x)` | `ExpParser` | `ExpFunction` / `ExpExpr` | PARITY |

---

## 4. String Expressions

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| String literals (`'...'`) | `StringLiteralParser` | `STRING = SingleQuotedParser` | PARITY |
| String concatenation (`+`) | `StringExpressionParser` / `StringTermParser` / `StringPlusParser` | -- (StringExpression is flat choice, no `+` concatenation rule) | HAND-ONLY |
| String variables (`$var`) | `StringVariableParser`, `StringPrefixedVariableParser`, `StringSuffixedVariableParser` | `VariableRef` (polymorphic) | PARTIAL |
| String if-expression | `StringIfExpressionParser` | -- (IfExpression returns generic Expression) | PARTIAL |
| String match-expression | `StringMatchExpressionParser`, `StringCaseExpressionParser`, `StringCaseFactorParser`, `StringDefaultCaseFactorParser` | `StringMatchExpression`, `StringCase`, `StringDefaultCase` | PARITY |

### String Functions (Function Form)

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `toUpperCase(str)` | `ToUpperCaseParser` | `ToUpperCaseFunction` / `ToUpperCaseExpr` | PARITY |
| `toLowerCase(str)` | `ToLowerCaseParser` | `ToLowerCaseFunction` / `ToLowerCaseExpr` | PARITY |
| `trim(str)` | `TrimParser` | `TrimFunction` / `TrimExpr` | PARITY |
| `length(str)` (function form) | `StringLengthParser` | `LengthFunction` / `LengthExpr` | PARITY |
| `toNum(str, default)` | `ToNumParser` | `ToNumFunction` / `ToNumExpr` | PARITY |
| `slice(...)` (Python-style slicing) | `SliceParser` | -- | HAND-ONLY |

### String Dot Methods

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `$var.toUpperCase()` | -- | `ToUpperCaseDotMethod` / `ToUpperCaseDotExpr` | UBNF-ONLY |
| `$var.toLowerCase()` | -- | `ToLowerCaseDotMethod` / `ToLowerCaseDotExpr` | UBNF-ONLY |
| `$var.trim()` | -- | `TrimDotMethod` / `TrimDotExpr` | UBNF-ONLY |
| `$var.length()` | -- | `LengthDotMethod` / `LengthDotExpr` | UBNF-ONLY |

### String Predicates (Boolean-returning)

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `str == str` (string equality) | `StringEqualsExpressionParser` | `StringComparisonExpression` with `==` | PARITY |
| `str != str` (string inequality) | `StringNotEqualsExpressionParser` | `StringComparisonExpression` with `!=` | PARITY |
| `str.in(str, str, ...)` | `StringInParser`, `InMethodParser` | -- | HAND-ONLY |
| `str.startsWith(str)` | `StringStartsWithParser`, `StartsWithMethodParser` | -- | HAND-ONLY |
| `str.endsWith(str)` | `StringEndsWithParser`, `EndsWithMethodParser` | -- | HAND-ONLY |
| `str.contains(str)` | `StringContainsParser`, `ContainsMethodParser` | -- | HAND-ONLY |

---

## 5. Boolean Expressions

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Boolean OR (`\|`) | `BooleanOrExpressionParser` | `BooleanExpression` (level=1) | PARITY |
| Boolean AND (`&`) | `BooleanAndExpressionParser` | `BooleanAndExpression` (level=2) | PARITY |
| Boolean XOR (`^`) | `BooleanXorExpressionParser` | `BooleanXorExpression` (level=3) | PARITY |
| 3-level precedence (OR < AND < XOR) | Structural (Expression > And > Xor > Factor) | `@precedence(level=1/2/3)` | PARITY |
| `true` / `false` literals | `TrueTokenParser`, `FalseTokenParser` | `'true'`, `'false'` in `BooleanFactor` | PARITY |
| `not(expr)` | `NotBooleanExpressionParser` | `NotExpression` / `NotExpr` | PARITY |
| Parenthesized boolean | `ParenthesesParser(BooleanExpressionParser)` | `'(' BooleanExpression ')'` in `BooleanFactor` | PARITY |
| Boolean variables | `BooleanVariableParser` | `VariableRef` (polymorphic) | PARTIAL |
| Boolean if-expression | `BooleanIfExpressionParser` | -- (IfExpression returns generic Expression) | PARTIAL |
| Boolean match-expression | `BooleanMatchExpressionParser`, `BooleanCaseExpressionParser`, etc. | `BooleanMatchExpression`, `BooleanCase`, `BooleanDefaultCase` | PARITY |

---

## 6. Comparison Operators

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `==` (number) | `NumberEqualEqualExpressionParser` | `ComparisonExpression` with `CompareOp` | PARITY |
| `!=` (number) | `NumberNotEqualExpressionParser` | `ComparisonExpression` with `CompareOp` | PARITY |
| `>` | `NumberGreaterExpressionParser` | `ComparisonExpression` with `CompareOp` | PARITY |
| `>=` | `NumberGreaterOrEqualExpressionParser` | `ComparisonExpression` with `CompareOp` | PARITY |
| `<` | `NumberLessExpressionParser` | `ComparisonExpression` with `CompareOp` | PARITY |
| `<=` | `NumberLessOrEqualExpressionParser` | `ComparisonExpression` with `CompareOp` | PARITY |

---

## 7. Control Flow

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `if (cond) { then } else { else }` (number) | `NumberIfExpressionParser` | `IfExpression` / `IfExpr` | PARITY |
| `if (cond) { then } else { else }` (string) | `StringIfExpressionParser` | `IfExpression` / `IfExpr` (generic) | PARTIAL |
| `if (cond) { then } else { else }` (boolean) | `BooleanIfExpressionParser` | `IfExpression` / `IfExpr` (generic) | PARTIAL |
| Ternary `(cond ? then : else)` (number only) | `TernaryOperatorParser` | `TernaryExpression` / `IfExpr` | PARTIAL |
| Ternary inside arguments | -- | `ArgumentTernary` / `IfExpr` | UBNF-ONLY |
| Number match expression | `NumberMatchExpressionParser`, `NumberCaseExpressionParser`, `NumberCaseFactorParser`, `NumberDefaultCaseFactorParser` | `NumberMatchExpression`, `NumberCase`, `NumberDefaultCase` | PARITY |
| String match expression | `StringMatchExpressionParser`, etc. | `StringMatchExpression`, etc. | PARITY |
| Boolean match expression | `BooleanMatchExpressionParser`, etc. | `BooleanMatchExpression`, etc. | PARITY |

---

## 8. Variable System

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `var $name description = '...' ;` declaration | `VariableDeclarationParser` (Number/String/Boolean subtypes) | `VariableDeclaration` (Number/String/Boolean/Object) | PARITY |
| Object variable declaration | `NakedVariableDeclarationParser` (implicit) | `ObjectVariableDeclaration` (explicit) | PARTIAL |
| Type hints (`as number`, `as string`, etc.) | `NumberTypeHintParser`, `StringTypeHintParser`, `BooleanTypeHintParser`, `ObjectTypeHintParser` | `NumberTypeHint`, `StringTypeHint`, `BooleanTypeHint`, `ObjectTypeHint` | PARITY |
| Type hint prefix / suffix forms | `TypeHintPrefixParser`, `TypeHintSuffixParser` (per type) | `TypeHint` (single generic rule) | PARTIAL |
| Setter (`set [if not exists] expr`) | `NumberSetterParser`, `StringSetterParser`, `BooleanSetterParser`, `ObjectSetterParser` | `NumberSetter`, `StringSetter`, `BooleanSetter`, `ObjectSetter` | PARITY |
| `set if not exists` guard | `IfNotExistsParser` | `'if' 'not' 'exists'` (inline) | PARITY |
| Description (`description = '...'`) | `DescriptionParser` | `Description` rule | PARITY |
| `$var` reference (typed: Number/String/Boolean) | `NumberVariableParser`, `StringVariableParser`, `BooleanVariableParser` | `VariableRef` (single polymorphic rule) | PARTIAL |
| `$var` reference (naked / exclusive) | `NakedVariableParser`, `ExclusiveNakedVariableParser` | `VariableRef` | PARTIAL |
| Prefixed variable (`number: $var`) | `NumberPrefixedVariableParser`, `StringPrefixedVariableParser`, `BooleanPrefixedVariableParser`, `ObjectPrefixedVariableParser` | `VariableRef` with `TypeHint` | PARTIAL |
| Suffixed variable (`$var as number`) | `NumberSuffixedVariableParser`, `StringSuffixedVariableParser`, `BooleanSuffixedVariableParser`, `ObjectSuffixedVariableParser` | `VariableRef` with `TypeHint` | PARTIAL |
| `$var` catalog / backref lookup | Implicit via variable type parsers | `@catalog(context='variable')`, `@backref(name=name)` | PARITY |

---

## 9. Method System

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Method declaration (number return) | `NumberMethodParser` | `NumberMethodDeclaration` with `@declares(symbol=methodName)` | PARITY |
| Method declaration (string return) | `StringMethodParser` | `StringMethodDeclaration` | PARITY |
| Method declaration (boolean return) | `BooleanMethodParser` | `BooleanMethodDeclaration` | PARITY |
| Method declaration (object return) | `ObjectMethodParser` | `ObjectMethodDeclaration` | PARITY |
| Method parameters with type hints | `MethodParametersParser`, `MethodParameterParser`, `MethodParametersElementParser` | `MethodParameters`, `MethodParameter` with `@declares(symbol=paramName)` | PARITY |
| Method invocation (`call [internal] name(args)`) | `MethodInvocationParser`, `MethodInvocationHeaderParser` | `MethodInvocation` with `MethodInvocationHeader` | PARITY |
| Method invocation backref resolution | Implicit | `@backref(name=name)` on `MethodInvocation` | PARITY |
| Return type parsing | `ReturningParser`, `ReturningNumberParser`, `ReturningStringParser`, `ReturningBooleanParser` | `ReturnType`, `NumberReturnType`, `StringReturnType`, `BooleanReturnType`, `ObjectReturnType` | PARITY |
| Scope tree for methods | Implicit | `@scopeTree(mode=lexical)` | UBNF-ONLY |

---

## 10. External Invocations (Side Effects / Java Interop)

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `[call] with side effect returning as <type> : JavaClass#method(args)` | `SideEffectExpressionParser`, `SideEffectName1Parser` ("call with side effect") | -- | HAND-ONLY |
| `[call] external returning as <type> : JavaClass#method(args)` | `SideEffectExpressionParser`, `SideEffectName2Parser` ("call external") | -- | HAND-ONLY |
| Number side effect expression | `NumberSideEffectExpressionParser` | -- | HAND-ONLY |
| String side effect expression | `StringSideEffectExpressionParser` | -- | HAND-ONLY |
| Boolean side effect expression | `BooleanSideEffectExpressionParser` | -- | HAND-ONLY |
| Side effect with string-to-boolean param | `SideEffectStringToBooleanExpressionParser`, `SideEffectStringToBooleanExpressionParameterParser` | -- | HAND-ONLY |
| Side effect with string expression param | `SideEffectStringExpressionParser`, `SideEffectStringExpressionParameterParser` | -- | HAND-ONLY |
| `external returning as boolean name(args)` | -- (uses side effect infra) | `ExternalBooleanInvocation` / `ExternalBooleanInvocationExpr` | UBNF-ONLY |
| `external returning as number name(args)` | -- (uses side effect infra) | `ExternalNumberInvocation` / `ExternalNumberInvocationExpr` | UBNF-ONLY |
| `external returning as string name(args)` | -- (uses side effect infra) | `ExternalStringInvocation` / `ExternalStringInvocationExpr` | UBNF-ONLY |
| `external returning as object name(args)` | -- (uses side effect infra) | `ExternalObjectInvocation` / `ExternalObjectInvocationExpr` | UBNF-ONLY |

> **Note:** The hand-written "side effect" system and the UBNF "external invocation" system serve
> overlapping purposes but have different syntax. The hand-written path uses Java class + hash method
> references (`JavaClass#method`) while the UBNF path uses simple identifier names resolved via
> import aliases.

---

## 11. Argument System

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Function arguments (comma-separated) | `ArgumentsParser`, `ArgumentChoiceParser`, `ArgumentSuccessorParser` | `Arguments`, `ArgumentExpression` | PARITY |
| Comma-separated string expressions | `CommaSeparatedStringExpressionParser` | -- (handled generically) | HAND-ONLY |
| Argument-position ternary | -- | `ArgumentTernary` / `IfExpr` | UBNF-ONLY |
| Argument wrapping as expression | -- | `ArgumentExpression` / `ArgumentExpressionExpr` | UBNF-ONLY |

---

## 12. Domain-Specific Functions

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `inTimeRange(from, to)` | `InTimeRangeParser`, `InTimeRangeNameParser` | -- | HAND-ONLY |
| `inDayTimeRange(day, hour, day, hour)` | `InDayTimeRangeParser`, `InDayTimeRangeNameParser` | -- | HAND-ONLY |
| `DayOfWeek` enum (MONDAY..SUNDAY) | `DayOfWeekEnumParser` | -- | HAND-ONLY |
| `isPresent($var)` | `IsPresentParser`, `IsPresentNameParser` | -- | HAND-ONLY |

---

## 13. Object Type System

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Object expression (union of number/string/boolean) | `ObjectExpressionParser` | `ObjectExpression` | PARITY |
| Object variable | `ObjectVariableParser` | `VariableRef` (polymorphic) | PARTIAL |
| Object setter | `ObjectSetterParser` | `ObjectSetter` | PARITY |
| Object type hint | `ObjectTypeHintParser`, `ObjectTypeHintPrefixParser`, `ObjectTypeHintSuffixParser` | `ObjectTypeHint` | PARITY |
| Object method parser | `ObjectMethodParser` | `ObjectMethodDeclaration` | PARITY |
| Object method parameter | `ObjectVariableMethodParameterParser` | `MethodParameter` (generic) | PARTIAL |

---

## 14. Java Interop

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Java class name parsing (`pkg.Class`) | `JavaClassNameParser` | `ClassName` | PARITY |
| Java class method parsing (`pkg.Class#method`) | `JavaClassMethodParser` | -- (handled in ImportDeclaration inline) | PARTIAL |
| Java class + hash parser | `JavaClassAndHashParser` | -- | HAND-ONLY |
| External Java class expression type | `JavaExpressionParser`, `ExternalJavaClassExpression` | -- | HAND-ONLY |

---

## 15. Strict Typed System

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Strict typed number expression | `StrictTypedNumberExpressionParser`, `StrictTypedNumberTermParser`, `StrictTypedNumberFactorParser` | -- | HAND-ONLY |
| Strict typed string expression | `StrictTypedStringExpressionParser`, `StrictTypedStringTermParser`, `StrictTypedStringFactorParser` | -- | HAND-ONLY |
| Strict typed boolean expression | `StrictTypedBooleanExpressionParser`, `StrictTypedBooleanFactorParser` | -- | HAND-ONLY |
| Strict typed match expressions | `StrictTypedNumberMatchExpressionParser`, `StrictTypedStringMatchExpressionParser`, `StrictTypedBooleanMatchExpressionParser` | -- | HAND-ONLY |

> **Note:** The "strict typed" parsers are internal variants used when parsing inside typed
> contexts (e.g., method bodies) where naked variables should not be accepted. UBNF handles this
> implicitly through its grammar structure.

---

## 16. Grammar Infrastructure / Annotations

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| `@whitespace: javaStyle` (interleave) | Implicit in `JavaStyleDelimited*` base classes | `@whitespace: javaStyle`, `@interleave(profile=javaStyle)` | PARITY |
| `@comment: { line: '//' }` | Implicit | `@comment: { line: '//' }` | PARITY |
| `@mapping` (AST node mapping) | -- (parsers ARE the AST nodes) | `@mapping(ExprName, params=[...])` | UBNF-ONLY |
| `@leftAssoc` | Structural | `@leftAssoc` | PARITY |
| `@precedence(level=N)` | Structural (nesting depth) | `@precedence(level=N)` | PARITY |
| `@declares(symbol=...)` | Implicit | `@declares(symbol=varName, description=desc)` | UBNF-ONLY |
| `@scopeTree(mode=lexical)` | Implicit | `@scopeTree(mode=lexical)` on root + methods | UBNF-ONLY |
| `@catalog(context='variable')` | Implicit | `@catalog(context='variable')` on `VariableRef` | UBNF-ONLY |
| `@backref(name=...)` | Implicit | `@backref(name=name)` on `VariableRef`, `MethodInvocation` | UBNF-ONLY |
| `@root` annotation | `RootParserIndicator` interface | `@root` on `Formula` | PARITY |
| `@package` | Java package declaration | `@package: org.unlaxer.tinyexpression.generated.p4` | PARITY |

---

## 17. Expression-Level Top Choice

| Feature | Hand-Written | UBNF (P4) | Status |
|---------|-------------|-----------|--------|
| Top expression = Number \| Boolean \| String | `ExpressionsParser` (3 choices) | `Expression` (5 choices: Number \| Boolean \| String \| Object \| MethodInvocation \| paren) | PARTIAL |
| Object in top expression | Not in `ExpressionsParser` | `ObjectExpression` in `Expression` | UBNF-ONLY |
| MethodInvocation in top expression | Not in `ExpressionsParser` | `MethodInvocation` in `Expression` | UBNF-ONLY |
| Parenthesized expression at top level | Not in `ExpressionsParser` | `'(' Expression ')'` in `Expression` | UBNF-ONLY |

---

## Summary Statistics

| Status | Count |
|--------|-------|
| PARITY | 68 |
| HAND-ONLY | 30 |
| UBNF-ONLY | 16 |
| PARTIAL | 14 |
| **Total features** | **128** |

### Top HAND-ONLY Gaps (highest priority for UBNF migration)

1. **Side effect expressions** (`call with side effect returning as ...`) -- 6 parsers, core business logic integration
2. **String predicates** (`.in()`, `.startsWith()`, `.endsWith()`, `.contains()`) -- 4 features, used in boolean expressions
3. **Domain functions** (`inTimeRange`, `inDayTimeRange`, `isPresent`, `DayOfWeek`) -- 4 features
4. **Number concrete types** (int/long/float/double/BigDecimal cast, prefix, suffix) -- 6+ parser families
5. **Strict typed parsers** -- 7 parsers (internal, may not need UBNF equivalents)
6. **String concatenation** (`+` operator for strings) -- 1 feature
7. **Slice** (Python-style string slicing) -- 1 feature

### Top UBNF-ONLY Gaps (features to consider backporting or accepting as P4-forward)

1. **String dot methods** (`.toUpperCase()`, `.toLowerCase()`, `.trim()`, `.length()`) -- 4 features, new syntax
2. **External invocations** (typed `external returning as <type> name(args)`) -- 4 features, simplified syntax
3. **Argument-position ternary** -- 1 feature
4. **Top-level expression expansion** (Object, MethodInvocation, parens) -- 3 features
5. **Grammar annotations** (`@mapping`, `@declares`, `@scopeTree`, `@catalog`, `@backref`) -- infrastructure, not user-facing

### PARTIAL Coverage Notes

- **Variable references**: Hand-written has per-type variable parsers (`NumberVariableParser`, etc.) with prefix/suffix variants; UBNF uses a single polymorphic `VariableRef` with optional `TypeHint`. Semantic equivalence depends on evaluator behavior.
- **If expressions**: Hand-written has per-type if-expression parsers (`NumberIfExpressionParser`, `StringIfExpressionParser`, `BooleanIfExpressionParser`); UBNF has a single generic `IfExpression` returning `Expression`. This means the UBNF path relies on the evaluator for type resolution.
- **Ternary**: Hand-written supports ternary only for numbers; UBNF supports generic `Expression` in then/else branches.
- **Object variable declaration**: Hand-written uses `NakedVariableDeclarationParser` (no type keyword); UBNF has explicit `ObjectVariableDeclaration` with `'object'` keyword.
