# TinyExpression エディタ内言語ガイド

このガイドはVSIXに同梱されています。まず式を小さく書き、FormulaInfoで名前・結果型・依存関係を加え、最後にDAPで実行を観察する順で試せます。

## 1. 式の基本

数値、文字列、真偽値を値として使えます。

```tinyexpression
1 + 2 * 3
'Hello ' + $name
$age >= 18
```

変数は `$` から始まり、実行時には `CalculationContext` から読み取られます。変数名は大文字小文字を区別します。

```tinyexpression
if($member & $age >= 18){100}else{0}
```

複数の条件には `match` も使えます。

```tinyexpression
match{
  $score >= 80 -> 'A',
  $score >= 60 -> 'B',
  default -> 'C'
}
```

## 2. FormulaInfo

FormulaInfoは式だけでなく、名前、結果型、依存関係、実行バックエンドを一緒に保存する文書形式です。

```tinyexpression
tags:NORMAL
description:基本点にボーナスを足します
calculatorName:totalScore
var:score
dependsOn:
resultType:float
executionBackend:P4_AST_EVALUATOR
formula:
var $base as float set if not exists 40;
$base + 2
---END_OF_PART---
```

複数ブロックは `---END_OF_PART---` で区切ります。`dependsOn` に別の `calculatorName` を指定すると、依存先が先に実行され、同じCalculationContextへ結果が書き戻されます。

主な項目:

| 項目 | 意味 |
|---|---|
| `calculatorName` | 式を識別する名前 |
| `resultType` | `float`、`boolean`、`string` などの結果型 |
| `var` | 結果を書き戻すCalculationContext変数 |
| `dependsOn` | 先に実行するFormulaInfo名。複数はカンマ区切り |
| `executionBackend` | この式を実行するバックエンド |
| `formula` | 式本体の開始 |

候補が分からないときは、項目名または値の位置で補完を開いてください。誤ったバックエンド名や存在しない依存先は診断として表示されます。

## 3. DAPで実行する

`.vscode/launch.json` の `variables` は、文字列へ変換されず、そのJSON型のままCalculationContextへ渡されます。

```json
{
  "type": "tinyexpressionP4",
  "request": "launch",
  "name": "Debug FormulaInfo",
  "program": "${file}",
  "runtimeMode": "metadata",
  "calculatorName": "totalScore",
  "steppingMode": "ast",
  "stopOnEntry": true,
  "variables": {
    "base": 40,
    "member": true,
    "name": "alice"
  }
}
```

- `runtimeMode: metadata` はFormulaInfoの `executionBackend` に従います。
- `calculatorName` を省略すると最初のブロックが対象です。
- F10で生成ASTの構造を進み、Variablesで入力、結果、依存式の値を確認できます。
- ブレークポイントと停止位置はFormulaInfo内の元の行へ対応付けられます。

## 4. Javaコードブロック

FormulaInfo内にフェンス付きJavaコードを書けます。Javaとして色分けされますが、Java Language Serverによる完全な型検査や定義ジャンプはまだ提供していません。

````text
```java:sample.CheckValue
package sample;

public class CheckValue {
    public boolean check(String value) {
        return value != null && !value.isBlank();
    }
}
```
````

Javaコードは任意コードを実行できるため、コンパイル・実行は既定で無効です。信頼できる隔離環境でのみ `allowJavaCodeBlocks: true` を明示してください。公開エディタではこのフラグを有効にしないでください。

## 5. エラーの読み方

診断には `TE001` などの安定したエラーコード、問題の位置、修正候補が含まれます。エラーコードは人が検索できる識別子であると同時に、LLMや他のツールが修正方針を選ぶための機械可読データでもあります。

最初に見る場所:

1. Problemsパネルのエラーコードと説明
2. 波線部分のホバー
3. 電球アイコンのQuick Fix
4. **TinyExpression P4: Show Server Output** の起動・解析ログ

リポジトリ版の完全な仕様は `docs/language-guide-ja.md`、UBNFからLSP/DAPまでの実装解説は `docs/TINYEXPRESSION-P4-PIPELINE-GUIDE.md` にあります。
