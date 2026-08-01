# Backend Coverage Matrix

## 6 Execution Backends

| Backend | 実装クラス | 主要用途 |
|---------|-----------|---------|
| compile-hand | JavaCodeCalculatorV3 | 本番 (10^9 tx/month) |
| compile-legacy-astcreator | LegacyAstCreatorJavaCodeCalculator | 凍結（リグレッション比較用）。JavaCodeCalculatorV3 を継承し tokenReduer() のみ override |
| P4-typed | P4TypedAstEvaluator | DAP, LSP (sealed switch) |
| P4-reflection | GeneratedP4ValueAstEvaluator | レガシー (reflection) |
| ast-hand | AstNumberExpressionEvaluator | リテラル演算のみ |
| compile-dsl | DslJavaCodeCalculator + P4TypedJavaCodeEmitter | P4 → Java code → javac |

## Feature Coverage

| 機能 | compile-hand | compile-legacy-astcreator | P4-typed | P4-reflection | ast-hand | compile-dsl |
|------|:-----------:|:------------------------:|:--------:|:-------------:|:--------:|:-----------:|
| 四則演算 (+,-,*,/) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 変数参照 ($var) | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| 比較演算 (==,<,>) | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| Boolean演算 (&,\|,^) | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| if/else | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| ternary (cond?a:b) | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| match/case | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| Math 14関数 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| min/max 可変引数 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Not演算子 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| toNum() | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| String methods (関数形式) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| String methods (ドット形式) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| String predicates | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| isPresent() | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| **MethodInvocation (call)** | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| **External呼び出し** | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| **Side effect** | ✅ | ✅ | ✅ | **❌** | ❌ | ✅ |
| 変数宣言 (var) | ✅ | ✅ | 部分的 | ✅ | ❌ | ✅ |
| String連結 (+) | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |
| String slice ($s[0:3]) | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| inTimeRange/inDayTimeRange | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |

> **注**: `compile-legacy-astcreator` は `JavaCodeCalculatorV3` を継承し `tokenReduer()` のみを override する（`LegacyAstCreatorJavaCodeCalculator:15-33`）。Java コード生成能力は `compile-hand` と等価のため、Feature Coverage は `compile-hand` と同一。SPEC.md 1.3 に準拠（ステータス: 凍結・リグレッション比較用）。

## Fallback Chain

```
AstEvaluatorCalculator.apply():

  1. P4TypedAstEvaluator (sealed switch)
     ↓ UnsupportedOperationException
  2. GeneratedP4ValueAstEvaluator (reflection)
     ↓ Optional.empty()
  3. AstNumberExpressionEvaluator (hand-written AST)
     ↓ Optional.empty()
  4. JavaCodeCalculatorV3 (compile-hand) ← 最終 fallback
```

## P4TypedAstEvaluator の実装済み (v1.4.10+)

以下のノードタイプは P4TypedAstEvaluator で native に処理される（fallback 不要）:

```java
MethodInvocationExpr          // call identity(1) 等のUDF呼び出し ✅
ExternalBooleanInvocationExpr // external returning as boolean ... ✅
ExternalNumberInvocationExpr  // external returning as number ... ✅
ExternalStringInvocationExpr  // external returning as string ... ✅
ExternalObjectInvocationExpr  // external returning as object ... ✅
StringConcatExpr              // $firstName + ' ' + $lastName ✅
InTimeRangeExpr               // inTimeRange(9, 17) ✅
InDayTimeRangeExpr            // inDayTimeRange(MONDAY, 9, FRIDAY, 17) ✅
```

## ゴール

全ての式パターンが P4-typed パスで完結し、fallback が不要になること。
