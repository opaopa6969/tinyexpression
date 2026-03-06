# 実行バックエンド仕様

> ステータス: draft
> 最終更新: 2026-03-01

## スコープ

このドキュメントは TinyExpression の6つの実行バックエンドの仕様を定義する。選択契約、ランタイムエイリアス、パリティ契約を含む。`docs/TINYEXPRESSION-BACKEND-CONTRACT.md` の形式化。

このドキュメントが **扱わない** 範囲:
- 各バックエンドの E2E パイプライン詳細（→ [pipeline.md](pipeline.md)）

## 関連ドキュメント

- [pipeline.md](pipeline.md) — 実行パイプライン
- [overview.md](overview.md) — バックエンド一覧
- [docs/TINYEXPRESSION-BACKEND-CONTRACT.md](../docs/TINYEXPRESSION-BACKEND-CONTRACT.md) — 原契約文書（参考）

---

## バックエンド一覧

### 1. JAVA_CODE

| 項目 | 値 |
|------|-----|
| クラス | `JavaCodeCalculatorV3` |
| 役割 | JavaCode パスのプロダクションベースライン |
| 変更ポリシー | JavaCode 側機能追加の主要ターゲット |

### 2. JAVA_CODE_LEGACY_ASTCREATOR

| 項目 | 値 |
|------|-----|
| クラス | `LegacyAstCreatorJavaCodeCalculator`, `LegacyOperatorOperandTreeCreator` |
| 役割 | リファクタ前 OOTC 比較ベースライン |
| 変更ポリシー | **凍結**。最小限の互換性パッチのみ許容 |

### 3. AST_EVALUATOR

| 項目 | 値 |
|------|-----|
| クラス | `AstEvaluatorCalculator` |
| ランタイムチェーン | `generated-ast → token-ast → javacode-fallback` |
| 役割 | DSL 置換実行ライン |
| 変更ポリシー | 生成 AST カバレッジ拡張の主要ターゲット |

### 4. DSL_JAVA_CODE

| 項目 | 値 |
|------|-----|
| クラス | `DslJavaCodeCalculator` |
| 役割 | DSL JavaCode シーム（ハイブリッド: 部分ネイティブエミッタ + レガシーブリッジフォールバック） |
| 変更ポリシー | ネイティブ DSL Java エミッタカバレッジ拡張のマイグレーションターゲット |

### 5. P4_AST_EVALUATOR

| 項目 | 値 |
|------|-----|
| クラス | `P4AstEvaluatorCalculator` |
| 役割 | UBNF 生成型安全パーサー（P4 文法）+ AST_EVALUATOR 実行チェーン |
| 変更ポリシー | P4 文法カバレッジ拡張; 型安全 LSP/DAP の参照 |

### 6. P4_DSL_JAVA_CODE

| 項目 | 値 |
|------|-----|
| クラス | `P4DslJavaCodeCalculator` |
| 役割 | UBNF 生成型安全パーサー（P4 文法）+ DSL_JAVA_CODE 実行チェーン |
| 変更ポリシー | P4 文法カバレッジ拡張; 完全生成 DSL エバリュエータへのブリッジ |

---

## 選択契約（Selection Contract）

### 解決順序

1. **グローバル既定値**: `FormulaInfoAdditionalFields.executionBackend`（初期値: `JAVA_CODE`）
2. **式ごとの上書き**: FormulaInfo の `executionBackend` または `backend` フィールド
3. **実装割り当て**: `CalculatorCreatorRegistry.forBackend(...)`

### ランタイムエイリアス

| エイリアス | バックエンド |
|-----------|------------|
| `token` | `JAVA_CODE` |
| `legacy-astcreator`, `ootc` | `JAVA_CODE_LEGACY_ASTCREATOR` |
| `ast` | `AST_EVALUATOR` |
| `dsl-javacode` | `DSL_JAVA_CODE` |
| `p4-ast`, `p4-ast-evaluator` | `P4_AST_EVALUATOR` |
| `p4-dsl-javacode`, `p4-dsl-java-code` | `P4_DSL_JAVA_CODE` |

### 関連クラス

| クラス | 役割 |
|--------|------|
| `ExecutionBackend` | バックエンド enum 定義 |
| `CalculatorCreatorRegistry` | バックエンド → Calculator 生成器のレジストリ |
| `FormulaInfoParser` | FormulaInfo のバックエンドフィールド解析 |

---

## パリティ契約（Parity Contract）

### サポートコーパス

1. 6つすべてのバックエンドは等価な値を返す（MUST）
2. `AST_EVALUATOR` は `javacode-fallback` を回避する（MUST）
3. `P4_AST_EVALUATOR` と `P4_DSL_JAVA_CODE` は既存の4バックエンドと等価な値を返す（MUST）
4. **既知の例外**: P4 文法でカバーされていない構文を使用するフォーミュラはフォールバックパスを使用する

### 抽出コーパス

- 4つのバックエンドは等価な値を返す（MUST）
- 実行/非フォールバック閾値を通過する（MUST）

### DAP プローブ

- `parity.*` 変数と `parity.equalAll` はすべての6バックエンドを反映する（MUST）

---

## ランタイムマーカー契約

### 共有マーカー

| マーカー | 説明 |
|---------|------|
| `_tinyExecutionBackend` | 使用されたバックエンド名 |
| `_tinyExecutionMode` | 実行モード |
| `_tinyExecutionImplementation` | 実装種別 |
| `_tinyExecutionBridgeImplementation` | ブリッジ実装種別 |
| `_tinyExecutionNonBridgeImplementation` | 非ブリッジ実装種別 |

### DSL バックエンド追加マーカー

| マーカー | 説明 |
|---------|------|
| `_tinyDslJavaEmitterMode` | DSL Java エミッタモード |
| `_tinyDslJavaNativeEmitterUsed` | ネイティブエミッタ使用フラグ |

### DSL 実装値

- ネイティブスライスヒット: `_tinyExecutionImplementation=dsl-javacode-native`
- フォールバック: `_tinyExecutionImplementation=legacy-javacode-bridge`

---

## 変更ガイドライン

1. 構文/ランタイム拡張時: まず `JAVA_CODE` と `AST_EVALUATOR` を更新
2. `JAVA_CODE_LEGACY_ASTCREATOR` の変更は最小限に留める
3. バックエンド名を再利用してはならない（MUST NOT）
4. バックエンド動作契約が変更される場合: この文書とパリティテストを同時に更新する（MUST）

---

## 現在の制限事項

- P4 バックエンドは全言語機能をカバーしていない
- `JAVA_CODE_LEGACY_ASTCREATOR` は凍結状態

## 変更履歴

- 2026-03-01: TINYEXPRESSION-BACKEND-CONTRACT.md を形式化
