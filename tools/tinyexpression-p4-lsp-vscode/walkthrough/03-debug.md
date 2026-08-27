# 入力値とASTを同時に見ます

```json
"variables": {
  "base": 40,
  "member": true
}
```

`variables` はCalculationContextへ型付きで入ります。F10で生成ASTを進み、Variablesビューで入力値、選択した式の結果、依存するFormulaInfoの結果を確認できます。

FormulaInfoでは `calculatorName` でデバッグ対象を選びます。停止行は切り出した式ではなく、元のFormulaInfoファイル上に表示されます。
