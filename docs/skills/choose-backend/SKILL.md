---
name: choose-backend
description: TinyExpression の評価バックエンドを選択する判断基準
volta:
  version: 1
  namespace: tinyexpr
  locality: repo
  applies_when: "tinyexpr__evaluate や tinyexpr__execute_batch で backend パラメータを決めるとき"
  requires:
    - tinyexpr__evaluate
  min_role: VIEWER
  export: true
---

# バックエンド選択の判断基準

TinyExpression は 6 つの評価バックエンドを持つ。デフォルトは `AST_EVALUATOR`。

## バックエンド一覧

| バックエンド | 安全 | 速度 | 特徴 | 利用可否 |
|-------------|------|------|------|----------|
| AST_EVALUATOR | ○ | 中 | AST 直接評価（推奨・既定） | 常に利用可能 |
| P4_AST_EVALUATOR | ○ | 中 | P4 パーサー AST 評価 | 常に利用可能 |
| JAVA_CODE | △ | 高（2回目以降） | Java コード生成+コンパイル | TINYEXPR_ALLOW_JAVA_CODE=true のみ |
| DSL_JAVA_CODE | △ | 高（2回目以降） | DSL 経由 Java コード生成 | 同上 |
| P4_DSL_JAVA_CODE | △ | 高（2回目以降） | P4 DSL 経由 Java コード生成 | 同上 |
| JAVA_CODE_LEGACY_ASTCREATOR | △ | 高（2回目以降） | レガシー AST 生成器 | 同上 |

## 選択基準

1. **基本評価**: `AST_EVALUATOR`（デフォルト・推奨）
2. **P4 文法の機能を使いたい**: `P4_AST_EVALUATOR`
3. **最高速度が必要（同一式の繰り返し評価）**: `JAVA_CODE`（ただし要 TINYEXPR_ALLOW_JAVA_CODE=true）
4. **バックエンド比較**: `parity_check` tool で全バックエンドを比較

## セキュリティ注意

- JAVA_CODE 系バックエンドは JVM 上でコードを生成・コンパイルする
- MCP サーバの環境変数 `TINYEXPR_ALLOW_JAVA_CODE=true` でのみ有効
- デフォルトは無効（安全）

## P4 文法のカバレッジ

P4 generated backend の未カバー構文・mapping・型・評価は明示的に失敗する。
手書き backend が必要な場合は `JAVA_CODE` / `JAVA_CODE_LEGACY_ASTCREATOR` を明示選択し、
generated backend からの暗黙 fallback としては使わない。
