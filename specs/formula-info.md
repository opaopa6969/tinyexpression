# FormulaInfo 仕様

> ステータス: draft
> 最終更新: 2026-03-01

## スコープ

このドキュメントは FormulaInfo ファイル形式、キー一覧、区切りフォーマット、TinyExpressionsExecutor の実行モデル、および依存解決アルゴリズムを定義する。

このドキュメントが **扱わない** 範囲:
- バックエンドの選択契約（→ [backends.md](backends.md)）
- API の詳細（→ [api.md](api.md)）

## 関連ドキュメント

- [backends.md](backends.md) — バックエンド選択
- [api.md](api.md) — TinyExpressionsExecutor API

---

## ファイル形式

FormulaInfo は `formulaInfo.txt` ファイルに記述される。1つのファイルに複数の式定義を含められる。

### 配置構成

```
<root>/
  <tenant-id-1>/formulaInfo.txt
  <tenant-id-2>/formulaInfo.txt
```

`FileBaseTinyExpressionInstancesCache` がこのディレクトリ構成を期待する。

---

## ブロック構造

各式定義はメタデータキーと式本文から構成される。式定義間は `---END_OF_PART---` で区切る（MUST）。

```
key1:value1
key2:value2
formula:
式テキスト
---END_OF_PART---
```

### メタデータ行

```
key:value
```

- キーと値はコロン `:` で区切る
- 行コメントは `#` で始まる

### formula フィールド

`formula:` 行の次行から次の区切り行までが式本文。

---

## キー一覧

### 必須キー

| キー | 説明 |
|------|------|
| `calculatorName` | 式の一意識別子 |
| `formula` | 式本文 |

### バックエンド設定

| キー | 説明 |
|------|------|
| `executionBackend` | 実行バックエンド名（正式名またはエイリアス） |
| `backend` | `executionBackend` のエイリアス |

### 型設定

| キー | 説明 |
|------|------|
| `resultType` | 戻り値の型（`string`, `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, FQCN） |
| `numberType` | 数値演算のデフォルト型 |

### 依存関係

| キー | 説明 |
|------|------|
| `dependsOn` | 依存する式の `calculatorName`（カンマ区切りで複数指定可） |

### 変数バインディング

| キー | 説明 |
|------|------|
| `var` | 計算結果を格納する変数名 |

### メタ情報

| キー | 説明 |
|------|------|
| `tags` | タグ（カンマ区切り。例: `NORMAL`） |
| `description` | 式の説明 |
| `field` | フィールド名（実運用向け） |
| `checkKind` | チェック種別（実運用向け） |

### テナント/サイト

| キー | 説明 |
|------|------|
| `siteId` | サイト ID |

---

## 式本文の拡張

### Java コードブロック

式本文内に Java クラスを埋め込める:

~~~
formula:
```java:package.ClassName
// Java ソースコード
```
import package.ClassName#method as alias;
if(external returning as boolean alias($input)){1}else{0}
~~~

バッククォート3つ + `java:完全修飾クラス名` で Java ブロックを開始し、バッククォート3つで終了する。

---

## TinyExpressionsExecutor 実行モデル

### 実行フロー

1. `FormulaInfo` ファイルをパースし、式定義のリストを取得
2. 各式定義からバックエンド解決を行い `Calculator` を生成
3. 依存関係に基づいて実行順序を決定
4. 各 `Calculator` を順次実行
5. 結果を `ResultConsumer` に渡す

### 依存解決アルゴリズム

- `dependsOn` フィールドにより式間の依存関係が宣言される
- 依存関係はグラフとして解析される
- `Calculator.dependsOnByNestLevel()` でネストレベルが計算される
- 実行時にはネストレベル（依存の深さ）の昇順で並べ替えて実行される
- `ResultConsumer` を通じて、先行式の結果を `CalculationContext` に設定し、後続式で参照可能にする

### ResultConsumer

```java
public interface ResultConsumer {
    void accept(CalculationContext c, Calculator calc, FormulaInfo info, Number result);
    void accept(CalculationContext c, Calculator calc, FormulaInfo info, String result);
    void accept(CalculationContext c, Calculator calc, FormulaInfo info, Boolean result);
    void accept(CalculationContext c, Calculator calc, FormulaInfo info, Object result);
}
```

- 結果の型に応じた `accept` メソッドが呼び出される
- 典型的な実装: `info.getValue("var")` で変数名を取得し、結果を `CalculationContext` に設定

---

## 現在の制限事項

- 循環依存の検出は実装されているが、エラーメッセージは限定的
- `dependsOn` は `calculatorName` ベースの文字列マッチング

## 変更履歴

- 2026-03-01: 初版作成
