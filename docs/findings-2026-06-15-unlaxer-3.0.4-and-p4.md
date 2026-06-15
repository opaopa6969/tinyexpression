# P4 インタプリタ: 性能改善・網羅テスト・発見したバグ (2026-06-15)

このブランチ (`p4-perf-and-tests`) は現 `master` の P4 実装の上に、(1) インタプリタの性能改善、
(2) 文法由来の網羅テスト、(3) 発見した P4 バグの記録、を追加する。

> 背景: master は既に P4 一次経路を稼働させている（`CodeBlock` のトークン直接インライン化、
> `StringLiteralParser`、`BooleanComparable`/`BooleanEqualityExpression`、`InMethod` 等）。
> 当初別ブランチで進めていた文法レベルの P4 復活作業は master に取り込み済みのため本ブランチには含めない。
> 生成器側の改善 unlaxer-parser#41/#42 は unlaxer-parser リポジトリの別ブランチで扱う。

## 1. 性能: AST キャッシュ（実装）

`AstEvaluatorCalculator.apply()` は P4 一次経路で**毎回フルパース＋マップ**していた。巨大式では
再パースが支配的（1評価あたり秒オーダー）。宣言を含まない式で typed 経路が成功した AST を
インスタンスにキャッシュ (`cachedTypedAst`) し、以降の apply は再パースを省略して現在の
`CalculationContext` で評価する（構造のみキャッシュ＝変数値は最新）。

実測（`BackendSpeedBenchmarkTest`, @Ignore）:

| terms | bytes | インタプリタ apply 前 | インタプリタ apply 後 | javacode |
|---|---|---|---|---|
| 1 | 1B | 7.64ms | 0.02ms | ~0ms |
| 2000 | 4KB | 1351ms | 0.06ms | **CompileError** |
| 6000 | 12KB | 5717ms | **0.19ms** | **CompileError** |

- **javacode は ~4KB 超でコンパイル不能**（JVM の1メソッド 64KB バイトコード上限）。数十KB の式は
  インタプリタが唯一の現実解。AST キャッシュで繰り返し評価が約3万倍高速化。
- インタプリタと javacode の同一性は `InterpreterJavacodeParityTest` で共通サブセットを検証
  （算術・単一boolean・if・比較・match・文字列）。math 関数は javacode 未対応のため対象外。

## 2. 追加テスト

- `GrammarCoverageInterpreterTest` — 文法由来の式と実行時の答えを網羅（算術・math関数・boolean
  優先順位 OR&lt;AND&lt;XOR・not入れ子・if/ternary/match・文字列・変数・キャッシュ整合）。高速インタプリタ実行。
- `InterpreterJavacodeParityTest` — インタプリタ↔javacode のパリティ証明（共通サブセット）。
- `KnownP4BugsTest` — 下記バグの再現（@Ignore、修正後に外せば回帰検知になる）。
- `BackendSpeedBenchmarkTest` — 性能ベンチ（@Ignore、手動実行用）。

## 3. 発見した P4 インタプリタのバグ（master 現状で再現）

| ID | 症状 | 再現テスト |
|---|---|---|
| tinyexpression#25 | top-level の `not(...)` を `_boolean` 評価すると常に false（`if` 内は正常、javacodeは正しい）。外側 NotExpr が root マッピングで脱落する疑い | `KnownP4BugsTest.standaloneNotReturnsFalse` |
| tinyexpression#21 | `min/max` の3引数以上（P4は正しく1だが cross-check が壊れた legacy=3 を優先）、`if(1>0\|0>1&1>2)` の優先順位も同様に legacy で上書き | `variadicMinMax` / `booleanPrecedenceInIf` |
| unlaxer-parser#43 | `abs(-3)+pow(2,3)` 等 math 関数を含む算術。生成 `BinaryExpr` レコードのオペランド型が `BinaryExpr` 固定で MathFunction を保持できず誤評価 | `functionTermArithmetic` |

ネスト ternary `(true ? (false ? 1 : 2) : 3)` は master で正しく評価される（`nestedTernary` は通常テストとして保持）。

## 4. 関連 issue（unlaxer-parser, 別リポジトリ）

- **#41** GrammarValidator: ルール名 vs トークンパーサクラス名の衝突を generate 時にエラー化
  （master は症状をインライン化で回避したが検出ガードは無い）。→ 実装済み（unlaxer-parser ブランチ）。
- **#42** MapperGenerator: リテラル`@value`の WordParser キャッチオール衝突（paren-boolean 誤捕捉）。
  → 実装したが master の新 boolean 文法と相性問題があり再検証が必要。本ブランチには含めない。

## 5. テスト速度に関する補足

surefire の `forkCount` 並列化を試したが、複数の既存テストがプロセス横断の静的状態
（`JavaCodeBlockPolicy`、`Parser.get` キャッシュ）を共有しており並列フォークで落ちるため**採用しない**。
速度は AST キャッシュ（インタプリタ本流化）と `-Dtinyexpression.skipRailroad=true` で確保する。

## 6. テスト不安定性の調査（既存 master の問題）

ローカルで一部の既存テストが master 単体でも失敗する。調査した根本原因は以下（**いずれも本ブランチの
変更とは無関係。本ブランチは clean master 比で新規失敗を追加しない**）:

### 6.1 グローバル静的 `JavaCodeBlockPolicy` の実行順依存（主因）
`JavaCodeBlockPolicy.ENABLED` はプロセス全体で共有される `AtomicBoolean`、デフォルト false
（secure-by-default）。Java コードブロック式（例: CheckDigits）を使うテスト
（`FormulaInfoParserTest`, `FormulaInfoListTest`, `TinyExpressionsExecutorTest` 等）は
ポリシー有効化が必要だが**自分で `setEnabled(true)` を呼ばず**、別テストが有効化した状態に暗黙依存している。
`JavaCodeBlockPolicyTest` は @Before/@After で `reset()`（→false）するため、その後に走るこれらのテストは
`CompileError: Java code block execution is disabled` で落ちる。

結果として: (a) 実行順依存（CI の順序ではたまたま緑）、(b) 単体実行で落ちる、(c) **surefire forkCount 並列で
クラス分散が変わると落ちる**。これが §5 の forkCount 不採用の根本理由でもある。

**修正案**: コードブロックを使うテストは各自の @Before で `JavaCodeBlockPolicy.setEnabled(true)`、@After で
`reset()` を呼び、自己完結させる（順序・並列非依存になり、forkCount 高速化も解禁できる）。→ tinyexpression#26

### 6.2 `Parser.get` 等のプロセス横断シングルトン
パーサ/評価器がプロセス全体のシングルトンキャッシュを共有するため、reuseForks 並列でクラス間が干渉しうる。
6.1 と併せて並列実行を不安定にする。

### 6.3 陳腐化した生成コードコーパス（policy 無関係）
`DslJavaCodeGenerationExtractedParityTest.testExtractedLegacyCorpusJavaCodeParity` は、コミット済みの
期待 Java コードコーパスと現 codegen 出力の差で落ちる（例: `(1+1)/5` の括弧出力が
`([1.0f+1.0f])` vs `([(1.0f+1.0f)])`）。テスト不安定性ではなくゴールデン陳腐化。要コーパス再生成。
