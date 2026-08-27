# TinyExpression リリース・インストール runbook

issue #11 §1 (release/install 導線が環境依存のまま) を解消するための再現手順集。
package / install / deploy を 1 本化し、制約付き環境でも `/tmp` 逃がし先で
dry-run できる状態を維持する。

## 1. 前提

- Java 21 (Temurin 推奨)
- Maven 3.8+
- GnuPG 2.x (deploy 時のみ)
- ネットワーク (deploy 時のみ — Central Publisher Portal への到達)
- 認証情報 (deploy 時のみ — `~/.m2/settings.xml` の `<servers>` に `central` を登録)

`pom.xml` は `central-publishing-maven-plugin` を有効化している。旧OSSRHは
2025-06-30に終了済みのため使用しない。

Centralは`org.unlaxer`全体でUTC月1回のrelease trainとする。正本の台帳は
[unlaxer-parserのrelease queue](https://github.com/opaopa6969/unlaxer-parser/blob/master/release/central-release-queue.yml)。
VSIX・文書だけの変更ではMaven versionを公開しない。

## 2. レシピ早見表

| 用途 | コマンド | local repo 書込 | GPG | Network |
|---|---|---|---|---|
| dry-run package | `mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true package` | 不要 | 不要 | 不要 |
| smoke test | `mvn -P p4-smoke test` | 不要 | 不要 | 不要 |
| local install | `mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true install` | 必要 | 不要 | 不要 |
| Central利用量確認 | `scripts/release-central.sh` | 不要 | 不要 | 必要 |
| release deploy | `scripts/release-central.sh --execute --confirm org.unlaxer/YYYY-MM` | 必要 | 必要 | 必要 |

## 3. 制約付き環境での逃がし先

`~/.m2/repository` または `~/.gnupg` が書込不可のとき、`/tmp` などの
writable パスへ逃がす。CI sandbox や読取専用 home を持つコンテナで再現性を
保つために必要。

```bash
# 逃がし先準備（依存はMaven Centralから解決可能）
mkdir -p /tmp/m2repo
mkdir -p /tmp/gnupg && chmod 700 /tmp/gnupg

# package のみ (network/repo 書込不要 — pre-populate も不要)
mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true package

# install までは local repo へ書込が必要
mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true \
    -Dmaven.repo.local=/tmp/m2repo install

# Central bundle作成のみ（POMの既定値もskipPublishing=true）
mvn -DskipTests -Dgpg.skip=true \
    -Dmaven.repo.local=/tmp/m2repo \
    -DskipPublishing=true \
    deploy
```

GPG を使うrelease deployでも直接`mvn deploy`せず、guarded scriptを使う。
スクリプトはcleanな`master`、`origin/master`との一致、未公開version、当月枠を
検証してからだけ`skipPublishing=false`を渡す。

```bash
scripts/release-central.sh
scripts/release-central.sh --execute --confirm org.unlaxer/YYYY-MM
```

緊急のセキュリティ・データ損失・重大互換性修正のみ、理由と強い確認を要求する:

```bash
scripts/release-central.sh --execute --emergency \
  --reason "critical compatibility fix" \
  --confirm EMERGENCY:org.unlaxer/YYYY-MM
```

## 4. LSP モジュール込みのフル install

`tools/tinyexpression-p4-lsp-vscode/pom.xml` は `org.unlaxer:tinyExpression`
を依存に持つので、LSP module を build する前に root を local install しておく。

```bash
mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true \
    -Dmaven.repo.local=/tmp/m2repo install
(cd tools/tinyexpression-p4-lsp-vscode && \
   mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true \
       -Dmaven.repo.local=/tmp/m2repo verify)
```

CI (`.github/workflows/ci.yml`) の `smoke` ジョブもこの順序を踏む
(`mvn -P p4-smoke test` → `mvn install -DskipTests` → LSP module の
`mvn -P p4-smoke test`)。

## 5. 失敗モードと診断

| 症状 | 原因 | 対処 |
|---|---|---|
| `Unable to load file ~/.m2/repository/...` | local repo 書込不可 | `-Dmaven.repo.local=/tmp/m2repo` |
| `gpg: signing failed: Inappropriate ioctl for device` | pinentry が tty を取れない | `--pinentry-mode loopback` (pom で設定済み) と `GNUPGHOME=/tmp/gnupg` |
| `Could not find artifact org.unlaxer:tinyExpression:jar:1.4.11` (LSP build 中) | root install 漏れ | root で先に `mvn install` を実行 |
| Central upload が `401` / `403` | 認証またはnamespace権限 | `~/.m2/settings.xml` の `central` user tokenと`org.unlaxer`権限を確認 |
| LSP test の reflective access エラー | `--add-opens` 漏れ | `MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED"` |

## 6. Central Publisher Portal

- `central-publishing-maven-plugin` が`mvn deploy`時にbundleを作成してPortalへuploadする
- `autoPublish=true`によりvalidation成功後に自動公開する
- `waitUntil=published`により、コマンドは`PUBLISHED`到達まで待機する
- `skipPublishing=true`が既定で、guarded scriptだけがuploadを有効化する
- 認証にはCentral Portalで生成したuser tokenを使う

## 7. 関連ファイル

- [pom.xml](../pom.xml) — Central publishing plugin、GPG署名、公開座標
- [tools/tinyexpression-p4-lsp-vscode/pom.xml](../tools/tinyexpression-p4-lsp-vscode/pom.xml) — LSP module 依存
- [.github/workflows/ci.yml](../.github/workflows/ci.yml) — CI install 順序
- [docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md](./TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md) §9 — smoke / 確認コマンド
- [docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md](./TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md) — `unlaxer-dsl` 連携時の install

---

## VSIX (P4 LSP/DAP 拡張) のリリース

VSIX releaseはCentralの月次枠とは独立している。VSIX、walkthrough、文書だけの
変更ではroot Maven versionを上げず、VSIX側のversionだけを更新する。

### ビルド毎の artifact

`master` への push ごとに CI の Full verify が VSIX をビルドし、Actions artifact
として添付する:

**Actions タブ → 最新 run → Artifacts → `tinyexpression-p4-lsp-vsix`**

artifact は保持期限 (既定90日) で消える。配布には下のタグ付き Release を使う。

### タグ push で GitHub Release (恒久添付)

`v*` タグを push すると `.github/workflows/release-vsix.yml` が発動し、VSIX を
ビルドして Release を自動作成 (リリースノート自動生成) し、恒久添付する:

```bash
git tag -a vsix-vX.Y.Z -m "TinyExpression VSIX X.Y.Z"
git push origin vsix-vX.Y.Z
# → https://github.com/opaopa6969/tinyexpression/releases/tag/vsix-vX.Y.Z
```

既存タグでの再実行は asset を上書き (`--clobber`) するので、失敗時はワークフローを
re-run すればよい。

利用者のインストール: Release ページから `.vsix` をダウンロード →
VS Code の **Extensions: Install from VSIX...**。

ローカルビルド: ルートで `mvn install -DskipTests` 後、
`tools/tinyexpression-p4-lsp-vscode` で `mvn verify` → `target/*.vsix`。
