#!/usr/bin/env bash
# Installs org.unlaxer:unlaxer-{common,dsl}[-jdk17]:<unlaxer.version> into the job-local Maven
# repository from unlaxer-parser sources when that version is not on Maven Central yet.
#
#   MAVEN_REPO=/path/to/repo scripts/ci/install-unlaxer-if-unpublished.sh
#
# tinyexpression 2.0.1 needs unlaxer 3.1.1 (its -jdk17 artifacts, tinyexpression #220), which is
# built and merged but not published. Until it is, CI builds the pinned unlaxer-parser commit
# (.github/unlaxer-source-pin) whose <revision> must equal pom.xml's unlaxer.version. Once the
# version is on Central this script does nothing, and the pin file can be deleted.
set -euo pipefail

repo=$(cd "$(dirname "$0")/../.." && pwd)
: "${MAVEN_REPO:?set MAVEN_REPO to the job-local Maven repository}"

version=$(sed -n 's:.*<unlaxer.version>\(.*\)</unlaxer.version>.*:\1:p' "$repo/pom.xml" | head -1)
jdk17_version=$(sed -n 's:.*<unlaxer.version>\(.*\)</unlaxer.version>.*:\1:p' "$repo/tinyexpression-jdk17/pom.xml" | head -1)
if [[ -z "$version" || "$version" != "$jdk17_version" ]]; then
  echo "unlaxer.version differs: pom.xml '$version' vs tinyexpression-jdk17/pom.xml '$jdk17_version'" >&2
  exit 1
fi

central=https://repo1.maven.org/maven2/org/unlaxer
published=true
for artifact in unlaxer-common unlaxer-dsl unlaxer-common-jdk17 unlaxer-dsl-jdk17; do
  if ! curl -fsI "$central/$artifact/$version/$artifact-$version.pom" > /dev/null; then
    published=false
  fi
done
if $published; then
  echo "unlaxer $version (with -jdk17) is on Maven Central; nothing to install."
  exit 0
fi

pin_file="$repo/.github/unlaxer-source-pin"
commit=$(sed -n 's/^commit=//p' "$pin_file")
if [[ -z "$commit" ]]; then
  echo "unlaxer $version is not on Maven Central and $pin_file has no commit=" >&2
  exit 1
fi
work=$(mktemp -d "${RUNNER_TEMP:-/tmp}/unlaxer-source.XXXXXX")
trap 'rm -rf "$work"' EXIT
git init -q "$work"
git -C "$work" fetch -q --depth 1 https://github.com/opaopa6969/unlaxer-parser.git "$commit"
git -C "$work" checkout -q FETCH_HEAD
revision=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' "$work/pom.xml" | head -1)
if [[ "$revision" != "$version" ]]; then
  echo "unlaxer-parser $commit has revision '$revision', but unlaxer.version is '$version'" >&2
  exit 1
fi
echo "unlaxer $version is not on Maven Central; installing it from unlaxer-parser $commit"
mvn -B -q -f "$work/pom.xml" -pl .,unlaxer-common,unlaxer-dsl,unlaxer-common-jdk17,unlaxer-dsl-jdk17 \
  install -DskipTests -Dgpg.skip=true -Dmaven.repo.local="$MAVEN_REPO"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "unlaxer $version: not on Maven Central yet, built from unlaxer-parser \`$commit\`" >> "$GITHUB_STEP_SUMMARY"
fi
