# 2.0.0: ubnfc 生成パーサを既定エンジンにする（issue #183）

2026-09-24。tinyexpression 2.0.0 で `P4PreferredAstMapper` の既定実装を ubnfc 生成パーサに切り替え、
旧 combinator 経路を `legacy` として残した。ここは repo 内に残す根拠（パリティ表・段別時間・判断）。
元になった計測と facade の設計は ubnfc の `docs/reports/2026-09-24-te-facade.md`（PR opaopa6969/ubnfc#72）。

## 1. 取り込みの形

| 物 | 場所 | 由来 |
|---|---|---|
| 生成パーサ（22 ファイル、JDK 21 のみ） | `src/main/java/org/unlaxer/tinyexpression/p4/ubnfc/generated/**` | ubnfc front（.ubnf → IR）+ ubnfc-java（IR → Java）、package だけ変えて生成 |
| extern scanner | `.../p4/ubnfc/P4Scanners.java` | ubnfc `examples/p4-java/.../P4Scanners.java` を package 置換で複写 |
| facade 本体 | `.../p4/ubnfc/UbnfcP4Parse.java` | ubnfc `examples/p4-java-facade` から移植（手で保守） |
| AST 変換器（86 record、1:1） | `.../p4/ubnfc/UbnfcAstConverter.java` | `scripts/generate-ubnfc-converter.py` が両側の record 定義から機械生成 |
| 生成器差の吸収 | `.../p4/ubnfc/VariantShapes.java` | 手書き（§3.1） |
| 旧実装 | `.../p4/LegacyP4PreferredAstMapper.java` | 旧 `P4PreferredAstMapper` をそのまま改名（package private） |
| pin | `.../p4/ubnfc/UBNFC_PIN` | ubnfc commit、文法と IR の sha256、vendored 全ファイルの sha256 |

**package の判断**: 生成物は `org.unlaxer.tinyexpression.p4.ubnfc.generated` に置いた（issue 本文の案
`org.unlaxer.tinyexpression.generated.p4.ubnfc` ではなく）。公開 jar に `org.ubnfc` という他所の名前空間を
持ち込まない、かつ facade（`...p4.ubnfc`）の直下にまとめるため。生成器が `--package` を取るので、
ubnfc の commit 済み生成物との比較ではなく**同じ生成器での再生成**との byte 一致で検証する。

**再生成の鎖**: `tinyexpression-p4.ubnf`（この repo）→ `ubnfc ir --extern-first-chars scanners/first-chars.json`
→ IR（sha256 `173e218d…`、ubnfc の golden `ir/fixtures/tools__tinyexpression-p4-lsp-vscode__grammar__tinyexpression-p4.ubnf.ir.json`
と byte 一致）→ `org.ubnfc.java.Main --package org.unlaxer.tinyexpression.p4.ubnfc.generated`。
`scripts/regenerate-ubnfc-parser.sh --check` は

1. 文法の sha256 が pin と同じ（文法を変えたら再生成が要る）、
2. vendored 全ファイルの sha256 が pin と同じ（手編集・追加・削除の検出）、
3. ubnfc の checkout がある場合（`UBNFC_DIR`、既定は隣の `../ubnfc`）は pin の commit を一時領域に展開して
   Rust front と Java backend をビルドし、文法 → IR → Java を再生成して byte 一致を要求する、

を行う。**ubnfc は private repository** なので、公開の GitHub Actions では 1 と 2 だけが走り、3 は ubnfc を
読める場所（手元・release 作業）で走らせる（`--require-full` で「無ければ失敗」にできる）。3 は 2026-09-24 に
ubnfc `6d909d0e` で実行して byte 一致を確認した。

## 2. エンジン選択

`org.unlaxer.tinyexpression.p4.P4ParserEngine`。優先順位（具体的なものが勝つ）:

1. スコープ指定: FormulaInfo ブロックの `p4Engine:ubnfc|legacy`、`CalculatorCreatorRegistry.forBackend(backend, engine)`、
   `P4ParserEngine.with(engine, ...)`
2. `-Dtinyexpression.p4.engine=ubnfc|legacy`（JVM 全体の非常口）
3. 既定 `ubnfc`

未知の値は例外（黙って既定に戻さない）。FormulaInfo は構築前にエンジンを解決するので、壊れたプロパティは
「式を emit できない」ではなくプロパティ名入りのメッセージで落ちる。Calculator には `_tinyP4ParserEngine`
マーカーが付く（P4 文法で解析するバックエンドのみ: `AST_EVALUATOR` / `DSL_JAVA_CODE` / `P4_AST_EVALUATOR` /
`P4_DSL_JAVA_CODE`）。

FormulaInfo のフィールドを JVM 全体のプロパティより優先したのは、ブロック単位の指定のほうが具体的で、
「1 式だけ legacy で様子を見る」を全体設定を変えずにできるようにするため。全体を一斉に戻す非常口は
プロパティのままで、ブロックに `p4Engine:` を書いていない限り効く。

## 3. パリティ（`UbnfcParityTest`、恒久テスト）

入力: テストが facade に渡す式 324 件（`src/test/resources/p4/ubnfc-parity/te-formulas.json`）+ ubnfc の
p4-java fixture 16 件 + 非 BMP 10 件 = **350 件**。同一 JVM で `legacy` と `ubnfc` を切り替えて
`ParsedAst` を canonical JSON（`{type, span, fields}`、span は両側とも `P4SourceText.spanOf`）で比較。
比較中は解析期限を外す（負荷で legacy だけ期限切れになるのは実装差ではない）。

| 判定 | 件数 |
|---|---:|
| `same`（AST・span・`selectionMode` 一致） | **327** |
| `both-reject`（例外型・メッセージまで一致） | **23** |
| `ast-differs` / `mode-differs` / 片側だけ拒否 / 拒否メッセージ差 | **0** |

非 BMP（`'こんにちは😀'`、`'😀a'[1:2]`、`/*😀*/ 'abcdef'[1:3]`、`match{'😀' == '😀' -> '𝄞', ...}` ほか）も span まで一致。
全行は実行ごとに `target/ubnfc-parity/report.tsv`。

### 3.1 公開 unlaxer-dsl 3.0.15 でビルドした場合

上の表は、手元の `~/.m2` にある unlaxer-dsl 3.0.15（**9/21 に手元で install された開発版で、公開 jar とは
別物**）で生成した AST を相手にした結果。Java CI と 2.0.0 のリリースは**公開 jar**でビルドする。
公開 jar の生成器は 2 点で違う。

1. **宣言型**: `External*InvocationExpr.className` が `QualifiedNameExpr`（開発版は `Optional<…>`）、
   `SliceExpr.start/end/step` が `String`（添字の字面を strip したもの。開発版は `Optional<Object>`）。
   変換器はこの 7 component を宣言型に依らない形で出し、`VariantShapes` が実行時に canonical constructor の
   型へ合わせる。変換器のソースはどちらの jar でも同一（`--check` が両方で一致）。
2. **mapper の不具合 2 件**（legacy はこれを引き継ぎ、ubnfc は引き継がない）:
   `import X as alias` で alias が `method` に入り `alias` が空になる。
   `receiver.contains/startsWith/endsWith(p…)`（`*DotExpr`）で `patterns` の先頭に receiver が重複する。

公開 jar でビルドしたときのパリティ（2026-09-24、隔離した local repo に Central から取得）:

| 判定 | 件数 |
|---|---:|
| `same` | 321 |
| `same-modulo-published-3.0.15-mapper-bugs`（上の 2 件だけで完全に説明できる差） | 6（fraud-alert の `.contains` 2、`P4OptionalExternalQualifierTest` の import 4） |
| `both-reject` | 23 |
| それ以外の差 | **0** |

ファジングは same 88 / 既知 2（import）/ both-reject 982 / 不一致 0。
既知の差を許すのは公開生成器を検出したとき（`SliceExpr.start` が `String`）だけで、判定は木を歩いて
「その 2 つの形以外は完全一致」を確かめる。件数には上限（10）を設けている。

## 4. 差分ファジング（`UbnfcDifferentialFuzzTest`）

コーパスの受理される式（240 文字以下）を固定 seed でトークン単位に 1 箇所だけ壊す（削除・重複・隣と交換・
語彙から挿入）。1 式 4 件、上限 1200 件。

| 変異体 | 両方受理（AST 一致） | 両方拒否（メッセージ一致） | 不一致 | legacy 期限切れで除外 |
|---:|---:|---:|---:|---:|
| 1072 | 90 | 982 | **0** | 0 |

## 5. 経路の行列（`P4EngineModeMatrixTest` ほか）

- FormulaInfo → 解析 → Java 生成 → javac → 実行 を、4 バックエンド × 5 通りの選び方
  （無指定 / `p4Engine:ubnfc` / `p4Engine:legacy` / `-D…=legacy` / `-D…=legacy` + `p4Engine:ubnfc`）× 11 式
  （非 BMP 6 式を含む）で通し、値とマーカーを確かめる。生成された Java ソースは両エンジンで同一であることも確かめる。
- `P4DslJavaCodeCalculatorLegacyEngineTest` / `P4AstEvaluatorCalculatorLegacyEngineTest` は `CalculatorImplTest`
  全体を `-Dtinyexpression.p4.engine=legacy` で回す（既定側は既存の `P4DslJavaCodeCalculatorTest` /
  `P4AstEvaluatorCalculatorTest`）。

## 6. 移した・範囲を絞ったテスト

- `P4PreferredAstMapperDiagnosticsTest`（12 件）: 旧実装の private 要素（`DEFERRED_DIAGNOSTICS_SAFE`、
  `parseWithRoot`、`Name.of(..., "parseDeadline")`）を反射で触る。**`legacy` 限定に移した**
  （対象を `LegacyP4PreferredAstMapper` に）。ubnfc にはこの内部が無い。公開面の振る舞いはパリティが押さえる。
  3.0 で `legacy` と一緒に消す。
- `P4SourceMappingTest#mapOnceRootFailureKeepsCandidateAndDefaultExceptionBehavior` の反射ヘルパ: 同じ理由で
  `LegacyP4PreferredAstMapper` を向くように変えた。
- 削除・除外したテストは無い。

## 7. 段別時間

### 7.1 ubnfc facade 報告の値（同じ facade コード、load 15〜18）

| 入力 | parse の割合 legacy | ubnfc | parse+生成+javac legacy | ubnfc |
|---|---:|---:|---:|---:|
| `valid-basic.tiny` | 1% | 0.3% | 57.1 ms | 31.4 ms |
| `complex.tiny` | 19% | 2.4% | 105.4 ms | 52.7 ms |
| `large-match.tiny` | 80% | 17.5% | 181.2 ms | 38.0 ms |
| fraud#1 | 76% | 2.5% | 148.0 ms | 75.7 ms |
| fraud#4 | 68% | 3.6% | 232.6 ms | 38.2 ms |
| fraud#5 | 98% | 4.3% | 2179.3 ms | 33.4 ms |
| `complex-x64.tiny` | 60% | 50.0% | 2398.5 ms | 154.5 ms |

FormulaInfo（fraud-alert 5 式、`backend:dsl-javacode`、読み込み〜Calculator 構築）: 2490.2 → 363.6 ms。

### 7.2 in-tree の再計測（`P4EngineStageTimingRunner`、エンジン切替）

2026-09-24 22:30 頃、共有マシンで load average 13〜17（別エージェントの Maven が並走）。warmup 2 / 計測 7 の中央値。
**javac 列は負荷で facade 報告の 3〜6 倍に膨らんでおり、旧/新の差は雑音**。parse 列の比だけを読むこと。
単位は ms（実行は µs/回）。

| 入力 | parse legacy | parse ubnfc | 倍率 | javac legacy | javac ubnfc |
|---|---:|---:|---:|---:|---:|
| `valid-basic.tiny` | 9.74 | 0.17 | 56× | 280.7 | 277.5 |
| `complex.tiny` | 209.59 | 7.93 | 26× | 295.0 | 318.6 |
| `flat-arithmetic.tiny` | 383.12 | 3.52 | 109× | 304.3 | 384.5 |
| `large-match.tiny` | 1308.55 | 87.08 | 15× | 299.7 | 214.7 |
| `complex-x4.tiny` | 883.70 | 84.90 | 10× | 582.7 | 492.7 |
| `complex-x16.tiny` | 4205.49 | 400.55 | 10× | 1003.7 | 800.1 |
| `complex-x64.tiny` | 15380.41 | 675.64 | 23× | 507.5 | 491.7 |
| fraud#1 | 1005.95 | 4.23 | 238× | 288.9 | 214.2 |
| fraud#2 | 86.16 | 0.98 | 88× | 306.0 | 304.3 |
| fraud#3 | 5.15 | 0.52 | 10× | 276.8 | 308.9 |
| fraud#4 | 1284.86 | 4.47 | 287× | 329.9 | 307.8 |
| fraud#5 | **既定 10 s の期限切れで解析不可** | 3.58 | — | — | 291.6 |

FormulaInfo（fraud-alert 5 式）: ubnfc 2199.3 ms（ほぼ javac 5 回ぶん、負荷込み）、**legacy は fraud#5 が既定の
解析期限 10 s に当たって読み込み不可**。負荷の高い環境で legacy が実運用の式を期限内に解析できないこと自体が、
既定を切り替える理由の 1 つ。

再現:

```bash
mvn -o -q test-compile -Dtinyexpression.skipRailroad=true
mvn -o -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
for e in ubnfc legacy; do
  java -Xss16m -Dtinyexpression.p4.engine=$e -cp target/classes:target/test-classes:$(cat target/cp.txt) \
    org.unlaxer.tinyexpression.evaluator.javacode.P4EngineStageTimingRunner target/timing/$e.tsv
done
```

## 8. 範囲外（2.0 では触らない）

- LSP/DAP（`tools/tinyexpression-p4-lsp-vscode`）の構文診断は `TinyExpressionP4Mapper` を直接使うまま。
  LSP が `P4PreferredAstMapper` 経由で呼ぶ箇所は既定エンジンになる。依存は tinyExpression 2.0.0 に上げた。
- unlaxer-common / unlaxer-dsl は 3.0.15 のまま（ubnfc 生成パーサは unlaxer に依存しない）。Java CI は公開版
  3.0.15 jar を使う。
- `legacy` の削除は 3.0。
