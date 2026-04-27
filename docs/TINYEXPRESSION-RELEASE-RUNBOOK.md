# TinyExpression リリース・インストール runbook

issue #11 §1 (release/install 導線が環境依存のまま) を解消するための再現手順集。
package / install / deploy を 1 本化し、制約付き環境でも `/tmp` 逃がし先で
dry-run できる状態を維持する。

## 1. 前提

- Java 21 (Temurin 推奨)
- Maven 3.8+
- GnuPG 2.x (deploy 時のみ)
- ネットワーク (deploy 時のみ — Sonatype OSSRH への到達)
- 認証情報 (deploy 時のみ — `~/.m2/settings.xml` の `<servers>` に `ossrh` を登録)

`pom.xml` は `central-publishing-maven-plugin` をコメントアウトしており、
現時点では従来の `ossrh` (`oss.sonatype.org`) 経路を使う。
切り替えは別 issue で扱う。

## 2. レシピ早見表

| 用途 | コマンド | local repo 書込 | GPG | Network |
|---|---|---|---|---|
| dry-run package | `mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true package` | 不要 | 不要 | 不要 |
| smoke test | `mvn -P p4-smoke test` | 不要 | 不要 | 不要 |
| local install | `mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true install` | 必要 | 不要 | 不要 |
| snapshot deploy | `mvn -DskipTests deploy` (version が `*-SNAPSHOT` のとき) | 必要 | 必要 | 必要 |
| release deploy | `mvn deploy` | 必要 | 必要 | 必要 |

## 3. 制約付き環境での逃がし先

`~/.m2/repository` または `~/.gnupg` が書込不可のとき、`/tmp` などの
writable パスへ逃がす。CI sandbox や読取専用 home を持つコンテナで再現性を
保つために必要。

> **重要 caveat**: `unlaxer-common` / `unlaxer-dsl` / `unlaxer-parser` は
> Maven Central には公開されておらず、依存解決は `~/.m2/repository` の
> ローカルキャッシュ (もしくは個別の private repo) に依存している。
> 真っ新な `/tmp/m2repo` で `install` すると次のような失敗になる:
>
> ```
> Could not resolve dependencies for project org.unlaxer:tinyexpression:jar:...
>   org.unlaxer:unlaxer-common:jar:3.0.2 was not found in
>   https://repo.maven.apache.org/maven2 ...
> ```
>
> 逃がし先を使う前に、必要な org/unlaxer/* を pre-populate する:
>
> ```bash
> mkdir -p /tmp/m2repo/org/unlaxer
> cp -r ~/.m2/repository/org/unlaxer/{unlaxer-common,unlaxer-dsl,unlaxer-parser} \
>       /tmp/m2repo/org/unlaxer/
> ```

```bash
# 逃がし先準備 (org/unlaxer/* は事前コピーが必要 — 上記 caveat 参照)
mkdir -p /tmp/m2repo/org/unlaxer
cp -r ~/.m2/repository/org/unlaxer/{unlaxer-common,unlaxer-dsl,unlaxer-parser} \
      /tmp/m2repo/org/unlaxer/
mkdir -p /tmp/gnupg && chmod 700 /tmp/gnupg

# package のみ (network/repo 書込不要 — pre-populate も不要)
mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true package

# install までは local repo へ書込が必要
mvn -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true \
    -Dmaven.repo.local=/tmp/m2repo install

# deploy dry-run (実際には push しない場合は altDeploymentRepository で逃がす)
mvn -DskipTests -Dgpg.skip=true \
    -Dmaven.repo.local=/tmp/m2repo \
    -DaltDeploymentRepository=local::default::file:///tmp/m2staging \
    deploy
```

GPG を使う release deploy の場合のみ:

```bash
export GNUPGHOME=/tmp/gnupg
gpg --import < /path/to/private.key
mvn deploy -Dmaven.repo.local=/tmp/m2repo
```

## 4. LSP モジュール込みのフル install

`tools/tinyexpression-p4-lsp-vscode/pom.xml` は `org.unlaxer:tinyexpression`
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
| `Could not find artifact org.unlaxer:tinyexpression:jar:1.4.11` (LSP build 中) | root install 漏れ | root で先に `mvn install` を実行 |
| `Could not resolve org.unlaxer:unlaxer-common:jar:3.0.2 ... not found in repo.maven.apache.org` | 逃がし先 repo に org/unlaxer/* を pre-populate していない | §3 の `cp -r ~/.m2/repository/org/unlaxer/{unlaxer-common,unlaxer-dsl,unlaxer-parser} /tmp/m2repo/org/unlaxer/` を先に実行 |
| `transferFailed: ... oss.sonatype.org` | 認証 / network | `~/.m2/settings.xml` の `ossrh` server を確認、network を確認 |
| LSP test の reflective access エラー | `--add-opens` 漏れ | `MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED"` |

## 6. ossrh vs central-publishing-plugin

現時点の方針: **ossrh を維持**。

- `pom.xml` の `central-publishing-maven-plugin` は extensions=true 付きで
  コメントアウトされており、明示的な切替判断が必要
- 切替には新規 namespace verification と settings.xml の認証情報差し替えが要る
- 3.0.2 移行・smoke set 固定が落ち着いた後、別 issue で評価する

## 7. 関連ファイル

- [pom.xml](../pom.xml) — `<distributionManagement>`, `gpg` plugin, `ossrh` profile
- [tools/tinyexpression-p4-lsp-vscode/pom.xml](../tools/tinyexpression-p4-lsp-vscode/pom.xml) — LSP module 依存
- [.github/workflows/ci.yml](../.github/workflows/ci.yml) — CI install 順序
- [docs/TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md](./TINYEXPRESSION-UNLAXERDSL-HANDBOOK.md) §9 — smoke / 確認コマンド
- [docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md](./TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md) — `unlaxer-dsl` 連携時の install
