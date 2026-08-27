---
name: write-formula-info
description: FormulaInfo 記法で複数式の依存関係を定義する手順
volta:
  version: 1
  namespace: tinyexpr
  locality: repo
  applies_when: "tinyexpr__execute_batch で複数式を依存関係付きで実行するとき"
  requires:
    - tinyexpr__execute_batch
  min_role: VIEWER
  export: true
---

# FormulaInfo 記法の書き方

TinyExpression の `execute_batch` tool では JSON 配列で式を定義する。
各式は `name`（名前）、`formula`（式本体）、`dependsOn`（依存する式の名前配列）を持つ。

## 基本形

```json
{
  "formulas": [
    {"name": "base", "formula": "$x * 2"},
    {"name": "total", "formula": "$base + 100", "dependsOn": ["base"]}
  ],
  "variables": {"x": 5}
}
```

## フィールド

| フィールド | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| name | string | ○ | 式の名前（依存参照用・変数名として `$name` で参照可能） |
| formula | string | ○ | 式本体 |
| dependsOn | string[] | × | 依存する式の名前配列（先に評価される） |
| resultType | string | × | 結果型（float/string/boolean/object。省略時float） |
| backend | string | × | バックエンド（省略時AST_EVALUATOR） |

## 変数

`variables` で初期変数を渡す。各式の結果は自動的に変数名（`name`）で ctx に書き戻され、後続の式から `$name` で参照できる。

## 実行順序

`dependsOn` で指定した式が先に評価される。依存関係のない式は定義順に評価される。

## 例: 地域指標スコアリング

```json
{
  "formulas": [
    {"name": "density", "formula": "$buildings / $establishments * 1000"},
    {"name": "score", "formula": "if($density > 50){$density * 2}else{$density}", "dependsOn": ["density"]}
  ],
  "variables": {"buildings": 500, "establishments": 12000}
}
```
