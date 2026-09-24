# ubnfc / legacy パリティの入力（issue #183）

`UbnfcParityTest` と `UbnfcDifferentialFuzzTest` が読む。

- `te-formulas.json` — tinyexpression（`c70416e1`）の `src/test/**`・`src/jmh/**`・
  `rust/tinyexpression-rs/tests/fixtures/**`・`benchmarks/fixtures/**` から、
  `P4PreferredAstMapper` の 4 入口に実際に渡される式を集めたもの。324 件（うちパース失敗を
  期待するもの 17 件）。重複は `(source, preferredType, preferredAstSimpleName)` で除いた。
  採取は ubnfc の facade 作業（`examples/p4-java-facade/src/test/resources/parity/`、
  commit は `src/main/java/org/unlaxer/tinyexpression/p4/ubnfc/UBNFC_PIN`）で行い、byte を変えずに複写した。
- `fixtures/*.tiny` — ubnfc `examples/p4-java/src/test/resources/fixtures/` の 16 件
  （`invalid-*`・`complex-half`・`complex-tail` は失敗を期待する）。

各要素:

| 欄 | 意味 |
|---|---|
| `source` | 式そのもの |
| `origin` | 採取元の `path:line` |
| `preferredType` | `ExpressionTypes` の定数名。無指定は `null` |
| `preferredAstSimpleName` | `parseByAstSimpleName*` に渡す名前。無指定は `null` |
| `negative` | テストがパース失敗を期待しているか |

含めなかったもの（採取時の判断）: `P4PreferredAstMapperPrecedenceTest` の式（候補名 API だけを呼ぶ）、
`P4RustSharedFixtureAcceptanceTest` の `.tiny`（facade を通らない）、`formulaInfo-test/**`
（backend が実行時に決まる。`P4EngineModeMatrixTest` が FormulaInfo 経路として別に通す）。
