# Test Baseline 運用

CI は `test-baseline.txt` と `tools/ci/check-test-baseline.sh` で「既知の失敗」を管理し、新規失敗の混入を検出する。

## 役割

| 要素 | 役割 |
|------|------|
| `test-baseline.txt` | 既知の失敗テスト一覧（`Class#method` 形式、1行1件）。空ファイル = 既知の失敗ゼロ件。 |
| `tools/ci/check-test-baseline.sh` | surefire の失敗をベースラインと比較するゲートスクリプト。 |

## `check-test-baseline.sh` の動作

前提: `mvn verify -Dmaven.test.failure.ignore=true` 実行後（`target/surefire-reports/` に結果があること）。

- **ベースラインに無い新規失敗** → `::error` を出し **exit 1** で CI を落とす。
- **ベースラインに有るが今回は通った** → `::notice` で警告のみ（ベースライン縮小を促す）。
- **新規失敗なし** → `OK: 新規失敗なし` を出し exit 0。

## baseline 更新手順

```bash
# 1. フルテストを実行（失敗を止めず最後まで走らせる）
mvn verify -Dmaven.test.failure.ignore=true \
  -Dtinyexpression.skipRailroad=true -Dgpg.skip=true \
  -Dmaven.javadoc.skip=true -Dspotbugs.skip=true -Derrorprone.skip=true

# 2. ベースラインを生成（target/surefire-reports から失敗を収集）
bash tools/ci/check-test-baseline.sh --write-baseline

# 3. 内容を確認してコミット
git diff test-baseline.txt
git add test-baseline.txt && git commit -m "test: update baseline"
```

`--write-baseline` は `target/surefire-reports/TEST-*.xml` から `failure` / `error` を持つ `testcase` を集計し `Class#method` 形式で書き出す。

## 運用方針

- **新規失敗は許容しない**。ベースライン未登録の失敗が出たら CI を落とし、修正するかベースラインに登録する（登録は人間判断）。
- **ベースラインの縮小は推奨**。ベースラインに有るテストが通ったら `::notice` で知らせるので、整理して縮小を図る。
- **ベースラインが空の場合**、既知の失敗ゼロ件を意味する。#58 のような「baseline に登録して運用する」想定の issue と実態が不整合にならないよう注意。

## 関連 issue

- #58: TernaryExpressionTest 失敗と baseline 運用の不整合
- #38: 指数バックトラックに起因するテスト失敗（#58 の前身）
