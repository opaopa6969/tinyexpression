# unlaxer の memo state version 修正・安全な成功 memo・値オブジェクトの反射除去（2026-09-22、unlaxer #269 / #270、Java）

## 結論

tinyexpression の全テストを unlaxer 開発版で回して見つかった退行（5 段ネスト if 式の parse が公開版 3.0.15 の 25 ms → 4〜6 秒）を追った結果、
unlaxer 側で 3 つの変更が入った。tinyexpression 側のコードは変えていない（計測はすべて同じ tinyexpression master `c70416e1`）。

| unlaxer PR | 内容 | publicFacade complex | comparison-heavy | fraud 式 #5 |
|---|---|---:|---:|---:|
| #271（#270） | `IntegerValue` のアノテーション反射を class 単位キャッシュに、`NodeKind.getTag()` の Tag を保持 | 31.38 → 27.03 ms（**-13.9%**） | 18.12 → 16.09 ms（**-11.2%**） | — |
| #273（#269） | `ScopeStore.addReference` / `addDiagnostic` で memo の state version を進めない | 30.62 → 22.98 ms（**-25.0%**） | 17.84 → 18.22 ms（+2.2%、ノイズ） | 4,680 → **86 ms** |
| #274（#269 続き） | 安全な rule に限定した成功 memo（#273 と合わせて） | 30.62 → 12.32 ms（**-59.8%**） | 17.84 → 9.40 ms（**-47.3%**） | 4,680 → **59 ms** |

**すべて採用。** #273 と #274 の基準は unlaxer `cb128f7`（#270 を含まない）なので、3 行目の値は #271 の効果を含まない。

## 測定条件

- tinyexpression master `c70416e1`（tinyexpression #167 込み）。unlaxer は isolated Maven repo で差し替え（`TINYEXPRESSION_MAVEN_REPO_LOCAL`）
- JMH `publicFacade` / `parseOnlySafe`、交互 3-run 中央値。x64 / 失敗入力（`facadeAny`）は 1 fork × 3 warmup × 5 iteration
- `P4PackratFraudFormulaTest` の出力（cold JVM）で fraud 式 5 本の parse 時間
- Java 21.0.9（Oracle）、Linux 6.18 (WSL2)、他のベンチマークは同時に走らせていない

## Timing（Java、ms/op）

### #270（unlaxer `cb128f7` → PR #271）

| Bench | Fixture | Baseline runs | median | Candidate runs | median | 変化 |
|---|---|---|---:|---|---:|---:|
| publicFacade | complex | 31.489 / 29.915 / 31.376 | **31.376** | 27.025 / 27.585 / 25.328 | **27.025** | **-13.87%** |
| publicFacade | comparison-heavy | 18.142 / 17.902 / 18.115 | **18.115** | 15.213 / 16.263 / 16.087 | **16.087** | **-11.19%** |
| parseOnlySafe | complex | 35.084 / 37.185 / 35.278 | **35.278** | 30.551 / 30.409 / 30.036 | **30.409** | **-13.80%** |

x64: publicFacade 1960.0 → 1723.2 ms（-12.1%）、parseOnlySafe 2372.7 → 2358.1 ms（-0.6%）。

### #269（unlaxer `cb128f7` → PR #273「fix」→ PR #273 + #274「fix+memo」）

| Bench | Fixture | Baseline runs | median | fix runs | median | 変化 | fix+memo runs | median | 変化 |
|---|---|---|---:|---|---:|---:|---|---:|---:|
| publicFacade | complex | 30.623 / 31.662 / 30.172 | **30.623** | 22.980 / 23.337 / 21.935 | **22.980** | **-24.96%** | 12.193 / 13.008 / 12.320 | **12.320** | **-59.77%** |
| publicFacade | comparison-heavy | 17.838 / 17.811 / 19.155 | **17.838** | 18.221 / 18.654 / 18.069 | **18.221** | +2.15% | 9.904 / 9.396 / 9.314 | **9.396** | **-47.32%** |
| parseOnlySafe | complex | 37.072 / 35.511 / 39.809 | **37.072** | 25.991 / 26.920 / 27.486 | **26.920** | **-27.38%** | 15.049 / 14.974 / 17.214 | **15.049** | **-59.41%** |

| Bench | Fixture | baseline ms | fix ms | 変化 | fix+memo ms | 変化 |
|---|---|---:|---:|---:|---:|---:|
| publicFacade | complex-x64 | 2008.2 | 1426.0 | -29.0% | 958.1 | -52.3% |
| parseOnlySafe | complex-x64 | 2413.4 | 1825.3 | -24.4% | 973.8 | -59.7% |
| facadeAny | complex-half（失敗） | 31.5 | 25.9 | -17.6% | 16.1 | -49.0% |
| facadeAny | complex-tail（失敗） | 82.9 | 51.6 | -37.8% | 28.6 | -65.5% |

fraud 式（`P4PackratFraudFormulaTest`、cold JVM）:

| 式 | baseline | fix | fix+memo |
|---|---:|---:|---:|
| #1 | 721 ms | 495 ms | 418 ms |
| #2 | 45 ms | 30 ms | 19 ms |
| #3 | 9 ms | 9 ms | 7 ms |
| #4 | 445 ms | 82 ms | 57 ms |
| #5 | 4,680 ms | 86 ms | 59 ms |

## 何が起きていたか

- memo の key は `stateVersion` を含む。`ScopeStore.addReference` / `addDiagnostic` が毎回 version を進めていたので、`$var` の参照を 1 つ commit すると
  以降の lookup が全て miss になっていた（hit カウンタは非ゼロなので、カウンタだけでは気づけない。位置ごとの重複 commit を数えて判明）。
  参照と semantic diagnostic は parse 中には読まれないので version を進める必要はない。
- 成功 memo は #194 で無くなっていた。安全条件（失敗 memo 安全 かつ 推移閉包に listener rule / custom token を含まない exact class、生成 marker
  `SafeSuccessMemoizable`）を満たす rule と空白 delimitor の成功を token 木ごと replay する。tinyexpression の生成 parser では 50 class が対象。
- JFR（5 秒の parse で 259 サンプル）で `IntegerValue` のアノテーション反射が CPU の約 24%、`NodeKind.getTag()` が 9% と分かった。

## 正確性

- unlaxer-common / unlaxer-dsl の全テスト通過（#274 では common 694 / dsl 1,004 件）。`ScopeStoreTransactionTest` は新契約（参照・診断は version を変えない）に更新
- tinyexpression 全テスト（`mvn test`、fix+memo の unlaxer）: tests=770 failures=0 errors=0 skipped=10（unlaxer master では失敗 1・エラー 3 だった）。`P4PackratFraudFormulaTest` が unlaxer 開発版で初めて通過
- p4-smoke 85 件通過（fix+memo）

## 注意

- tinyexpression の Java CI は Maven Central 公開版 3.0.15 で走るので、これらの変更は次の unlaxer 公開まで CI の数値には出ない
- 成功 memo は `Memoization.SAFE_FAILURES` を選んだ時だけ効く（既定 OFF は不変）。facade は `SAFE_FAILURES` を使っている

## 生データ

`raw/2026-09-22-memo-version-and-success-memo/`: `java-270-{baseline,candidate}-run{1,2,3}.json`、`java-270-x64-{baseline,candidate}.json`、
`java-269-{baseline,fix,fixmemo}-run{1,2,3}.json`、`java-269-x64-{baseline,fix,fixmemo}.json`、`fraud-269-{baseline,fix,fixmemo}.txt`、`te-full-269-fixmemo.txt`
