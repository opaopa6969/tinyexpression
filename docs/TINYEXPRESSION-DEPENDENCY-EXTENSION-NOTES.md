# TinyExpression Dependency Extension Notes

このドキュメントは、`tinyexpression` 実装中に `unlaxer-dsl` / `unlaxer-common` 側の拡張が必要と判断した事項を記録する。

運用ルール:

1. 拡張が必要かもしれない時点で、このファイルに記録する。
2. 実際に `unlaxer-dsl` / `unlaxer-common` を編集する前にユーザーへ許可を取る。
3. 拡張不要で解決した場合は「解消済み」と明記する。

---

## 2026-02-25: P4 UBNF associativity validation gap

### Context

- File: `docs/ubnf/tinyexpression-p4-draft.ubnf`
- Goal: `@leftAssoc` + `@precedence` を TinyExpression P4 draft で有効化する。

### Observed result

`unlaxer-dsl` validate-only で以下が継続発生:

- `E-ASSOC-MISSING-CAPTURE` (`@right` が見つからない)
- `E-ASSOC-NO-REPEAT`
- `E-MAPPING-MISSING-CAPTURE` (`right`)

再現最小 grammar:

- `docs/ubnf/tinyexpression-p4-assoc-repro.ubnf`

再現コマンド:

```bash
cd /mnt/c/var/unlaxer-temp/unlaxer-dsl
mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain \
  -Dexec.args="--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-assoc-repro.ubnf --validate-only --report-format json"
```

### Current workaround

- P4 draft では `@interleave` / `@backref` / `@scopeTree` を優先し、
  `@leftAssoc` / `@precedence` は一旦外した状態を validator-pass の基準にする。

### Possible extension candidates

1. `unlaxer-dsl` validator 側で canonical left-assoc 判定の許容形を拡張する
2. capture 抽出ロジックが repeat 内 capture を見落とす条件を調査して修正する
3. 仕様上の canonical form を TinyExpression 側 grammar に合わせて再定義する

### Required action before editing dependency repos

- `unlaxer-dsl` への修正が必要になった時点で、ユーザー許可を取得してから着手する。
