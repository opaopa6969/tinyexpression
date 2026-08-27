# 式に運用情報を加えます

FormulaInfoは、式を単なる文字列ではなく、名前・結果型・依存関係・実行方法を持つ部品として扱います。

```tinyexpression
calculatorName:welcomeScore
var:score
dependsOn:
resultType:float
executionBackend:P4_AST_EVALUATOR
formula:
$base + 2
---END_OF_PART---
```

複数の式を同じファイルに置けます。`dependsOn` の参照先は先に実行され、その結果は同じCalculationContextから利用できます。
