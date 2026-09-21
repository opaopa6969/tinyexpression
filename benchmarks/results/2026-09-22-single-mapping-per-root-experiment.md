# 候補型ごとの再 mapping を root 1 回の mapping に置き換える（2026-09-22、tinyexpression #167、Java）

## 結論

`P4PreferredAstMapper.mapCandidates` は候補型名（約 45 個）ごとに `P4SourceMapping.select` を呼び、生成 mapper が毎回 token 木全体を
再 mapping していた。unlaxer-parser #267（PR #268）で生成 mapper に `mapParsedTree(Token)` / `mapSubtreeTree(Token)` が追加され、
1 回の mapping の結果（preorder の候補列・AST・凍結した source span 層）を `MappedTree.select(String)` で何度でも選択できるようになった。
本変更は tinyexpression 側でこれをリフレクションで使い、root 1 つにつき 1 回だけ mapping して候補ごとに `select` する。
候補の順序・`coversWholeSource` の判定・返す `ParsedAst`（AST / source text / 選択理由）・例外の扱いは不変。
Maven Central 公開版 3.0.15（新 API 無し）では従来どおり候補ごとに `select` する経路に fallback する。実装は codex に委譲、レビュー済み。

**採用。** public facade 3-run 中央値で complex **-12.9%**、comparison-heavy **-19.7%**。x64 では complex -33%、comparison-heavy -60% で、
facade の倍率は complex 85.6 → 66.1 倍（入力 63 倍）、comparison-heavy 125.6 → 62.8 倍（入力 64 倍）とほぼ線形になった。

## 測定条件

- unlaxer-parser master `cb128f7`（#267 込み。isolated Maven repo、両側同一）。baseline = tinyexpression master `4bcd3657`、candidate = `2e2da9a2`
- JMH `publicFacade`（既定、3 run）。倍率と失敗入力は 1 fork × 3 warmup × 5 iteration（`facadeAny` / `publicFacade`）
- Java 21.0.9（Oracle）、Linux 6.18 (WSL2)、他のベンチマークは同時に走らせていない

## Timing（Java、ms/op）

public facade 3-run 中央値:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 35.346 / 34.873 / 33.777 | **34.873** | 31.066 / 30.393 / 30.130 | **30.393** | **-12.85%** |
| Java | comparison-heavy | 21.830 / 23.422 / 23.143 | **23.143** | 17.843 / 18.577 / 19.540 | **18.577** | **-19.73%** |

倍率（`publicFacade`、1 fork × 5 iteration。x1 は上の 3-run 中央値）:

| fixture | 入力倍率 | baseline ms | candidate ms | 変化 | baseline 倍率 | candidate 倍率 |
|---|---:|---:|---:|---:|---:|---:|
| complex | 1 | 34.9 | 30.4 | -12.9% | 1 | 1 |
| complex-x4 | 4 | 138.6 | 121.4 | -12.4% | 4.0 | 4.0 |
| complex-x16 | 16 | 613.8 | 529.6 | -13.7% | 17.6 | 17.4 |
| complex-x64 | 63 | 2986.2 | 2009.7 | -32.7% | 85.6 | 66.1 |
| comparison-heavy | 1 | 23.1 | 18.6 | -19.7% | 1 | 1 |
| comparison-heavy-x4 | 4 | 98.8 | 77.9 | -21.2% | 4.3 | 4.2 |
| comparison-heavy-x16 | 16 | 441.7 | 295.7 | -33.1% | 19.1 | 15.9 |
| comparison-heavy-x64 | 64 | 2907.6 | 1166.0 | -59.9% | 125.6 | 62.8 |

失敗入力を含む `facadeAny`（1 fork × 5 iteration）:

| 経路 | Fixture | baseline ms | candidate ms | 変化 |
|---|---|---:|---:|---:|
| facadeAny | complex | 42.0 | 34.7 | -17.3% |
| facadeAny | complex-half（失敗） | 30.0 | 30.3 | +1.0% |
| facadeAny | complex-tail（失敗） | 82.3 | 67.6 | -17.9% |
| facadeAny | complex-x64 | 3057.2 | 2073.9 | -32.2% |
| publicFacade | complex | 39.5 | 30.1 | -23.9% |
| publicFacade | complex-x64 | 2986.2 | 2009.7 | -32.7% |

x64 で facade と parser 単体の差（#167 の背景で約 960 ms）は、mapping が 1 回になったことで候補数に比例しなくなった。
comparison-heavy 系は候補列が長く、x64 で候補ごとの再 mapping が支配的だったため効果が大きい。

## 正確性

- `P4SourceMappingTest` 19 件（新規 6 件: 新経路と候補ごとの `select` の結果が token identity・AST equals・span で一致、mapping 回数が 1 回、
  新 API を持たない mapper での fallback、「メソッドはあるが呼び出し失敗」時の例外伝播）
- 全テスト 769 件中、失敗 1・エラー 3 は base `4bcd3657` でも同じ 4 件（`P4PackratFraudFormulaTest`、`AstEvaluatorCalculatorTest#testFunctions` 等。
  unlaxer 開発版で深いネストの if 式の parse が 4〜6 秒かかる既存問題。unlaxer-parser #269 に切り出した）
- 公開版互換: main コードは新 API を文字列名でしか参照しない（`rg` と `javap` でバイトコード上の型・メソッド参照が無いことを確認）。CI の
  "Mapper compatibility (published)" ジョブが公開版 3.0.15 での fallback 経路を検証する
- unlaxer-parser 側（PR #268）: unlaxer-common 683 / unlaxer-dsl 999 件、p4-smoke 85 件、選択結果の同一性を 4 fixture × 2 条件で確認

## 注意

- `MappedTree` を保持している間は全候補の AST が保持される。`mapCandidates` はメソッド内で使い捨てるので影響はない
- `mapOnce` 自体が例外を投げた場合は、従来「全候補で同じ例外 → 最後に `toParseFailure`」だったのと同じ経路になるよう、例外を保持して
  `select` 時に投げるハンドルを返す

## 生データ

`raw/2026-09-22-single-mapping-per-root/`: `java-{baseline,candidate}-run{1,2,3}.json`、`java-any-{baseline,candidate}.json`、`java-scale-{baseline,candidate}.json`
