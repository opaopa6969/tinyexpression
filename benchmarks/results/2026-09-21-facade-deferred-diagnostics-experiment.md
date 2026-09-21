# facade を診断 `DETAILED_ON_FAILURE` の経路にする（2026-09-21、tinyexpression #168、Java）

## 結論

unlaxer-parser #261 で `ParseOptions` の既定診断方針が `AUTO` になったが、`P4PreferredAstMapper.parseWithRoot` は低水準 `new ParseContext(...)` を使い、
生成 entry も marker 未宣言の手書き parser を含んでいたため、Java の facade は `DETAILED` のまま速くならなかった。本変更で facade 自身が
「root から到達する parser が unlaxer library / 生成 parser / allowlist の手書き 5 クラスだけか」を判定し、安全なら `DETAILED_ON_FAILURE` で parse、
失敗・未消費時だけ同じ残り予算で `DETAILED` 再解析する。unlaxer の新 API はリフレクションで解決し、Maven Central 公開版 3.0.15（新 API 無し）では
従来経路（`new ParseContext` + `enableMemoize()`）に fallback する。実装は codex に委譲、レビュー済み。

**採用。** public facade 3-run 中央値で complex **-13.3%**、comparison-heavy **-13.1%**、complex-x64 -15%。失敗入力は再解析で +52〜+85%。

## 測定条件

- unlaxer-parser `6a4187c`（isolated Maven repo、両側同一）。baseline = tinyexpression master `cf44b3d4`、candidate = `62d542fc`
- JMH `publicFacade`（既定、3 run）と `facadeAny`（本 PR で追加。失敗入力 complex-half / complex-tail は例外を捕捉、1 fork × 5 iteration）

## Timing（Java、ms/op）

public facade 3-run 中央値:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 41.062 / 41.257 / 43.486 | **41.257** | 35.397 / 35.773 / 36.565 | **35.773** | **-13.29%** |
| Java | comparison-heavy | 24.918 / 26.963 / 25.769 | **25.769** | 23.649 / 22.407 / 22.225 | **22.407** | **-13.05%** |

`facadeAny` / `publicFacade`（1 fork × 5 iteration）:

| 経路 | Fixture | baseline ms | candidate ms | 変化 |
|---|---|---:|---:|---:|
| facadeAny | complex | 45.8 | 34.2 | -25.2% |
| facadeAny | complex-half | 20.6 | 31.3 | +51.9% |
| facadeAny | complex-tail | 37.1 | 68.8 | +85.2% |
| facadeAny | complex-x64 | 3519.6 | 3144.2 | -10.7% |
| publicFacade | complex | 41.1 | 34.0 | -17.2% |
| publicFacade | complex-x64 | 3715.6 | 3157.1 | -15.0% |

## 正確性

- p4-smoke 85、`P4SourceMappingTest` / `P4PreferredAstMapper*Test` 38、Full verify は master と同じ既存失敗 3 件のみ（新規失敗なし）
- published 相当（空 Maven repo で Central の 3.0.15 を取得、CI の published ジョブと同一フラグ）: 96 件成功、fallback 経路の使用をログで確認
- 12 入力で AST・選択モード・sourceText / span・`ParseDiagnostic` 全項目・例外文言が前後一致（codex）
- allowlist: `StringLiteralParser`、`javalang.CodeStartParser` / `CodeEndParser` / `TripleBackTickParser`、`javatype.JavaClassNameParser`（解析中に診断を読まず再実行できることをコードで確認）

## 注意

- 失敗入力は 2 回 parse になり、deadline 予算も実質 2 回分。編集途中の入力を多く扱う用途では従来経路が要るなら、`DETAILED` を明示する入口を検討する
- allowlist に無い手書き parser を P4 文法に足すと facade は自動で `DETAILED` に戻る（速度が落ちるが結果は不変）

## 生データ

`raw/2026-09-21-facade-deferred-diagnostics/`: `java-{baseline,candidate}-run{1,2,3}.json`、`java-any-{baseline,candidate}.json`
