#!/usr/bin/env bash
# Installs org.unlaxer:unlaxer-{common,dsl}:<unlaxer.version> into the job-local Maven
# repository from unlaxer-parser sources when that version is not on Maven Central yet.
#
#   MAVEN_REPO=/path/to/repo scripts/ci/install-unlaxer-if-unpublished.sh
#
# Declarative P4 lexical modules need unlaxer 3.3.0-SNAPSHOT, not the published 3.1.1.
# Until a compatible version is published, CI builds the pinned unlaxer-parser commit
# (.github/unlaxer-source-pin) whose <revision> must equal pom.xml's unlaxer.version. Once the
# version is on Central this script does nothing, and the pin file can be deleted.
set -euo pipefail

repo=$(cd "$(dirname "$0")/../.." && pwd)
: "${MAVEN_REPO:?set MAVEN_REPO to the job-local Maven repository}"

version=$(sed -n 's:.*<unlaxer.version>\(.*\)</unlaxer.version>.*:\1:p' "$repo/pom.xml" | head -1)
if [[ -z "$version" ]]; then
  echo "pom.xml has no unlaxer.version" >&2
  exit 1
fi

central=https://repo1.maven.org/maven2/org/unlaxer
published=true
for artifact in unlaxer-common unlaxer-dsl; do
  if ! curl -fsI "$central/$artifact/$version/$artifact-$version.pom" > /dev/null; then
    published=false
  fi
done
if $published; then
  echo "unlaxer $version is on Maven Central; nothing to install."
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
mvn -B -q -f "$work/pom.xml" -pl .,unlaxer-common,unlaxer-dsl \
  install -DskipTests -Dgpg.skip=true -Dmaven.repo.local="$MAVEN_REPO"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "unlaxer $version: not on Maven Central yet, built from unlaxer-parser \`$commit\`" >> "$GITHUB_STEP_SUMMARY"
fi
