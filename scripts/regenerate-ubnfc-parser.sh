#!/usr/bin/env bash
# Regenerates / verifies the vendored ubnfc-generated P4 parser (tinyexpression 2.0.0 default engine).
#
#   scripts/regenerate-ubnfc-parser.sh [--check]         verify (default)
#   scripts/regenerate-ubnfc-parser.sh --check --require-full
#   scripts/regenerate-ubnfc-parser.sh --write           regenerate from the pinned commit
#   UBNFC_REV=<sha> scripts/regenerate-ubnfc-parser.sh --write   move the pin
#
# What is pinned (src/main/java/org/unlaxer/tinyexpression/p4/ubnfc/UBNFC_PIN):
#   - the ubnfc commit whose front (Rust, .ubnf -> IR) and Java backend (IR -> Java) are used,
#   - sha256 of this repo's P4 grammar and of the IR produced from it,
#   - sha256 of every vendored file (generated/** and P4Scanners.java).
#
# --check always verifies, without ubnfc:
#   (1) the grammar still has the pinned sha256 (a grammar edit needs a regeneration),
#   (2) every vendored file still has its pinned sha256 and no file was added or removed.
# When an ubnfc checkout is available (UBNFC_DIR, default ../ubnfc next to this repo) it also
#   (3) rebuilds the pinned ubnfc, regenerates grammar -> IR -> Java into a temp dir and requires
#       a byte-identical result (IR sha256 and every file).
# ubnfc is a private repository, so public CI runs (1)+(2); (3) runs wherever ubnfc is readable.
# --require-full turns "ubnfc not available" into a failure.
#
# Environment: UBNFC_DIR, UBNFC_REV (--write only), CARGO_FLAGS (e.g. --offline), MVN_FLAGS (e.g. -o).
# The AST converter (UbnfcAstConverter.java) is checked separately after `mvn compile` by
# scripts/generate-ubnfc-converter.py --check.
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
vendor_rel=src/main/java/org/unlaxer/tinyexpression/p4/ubnfc
vendor="$repo/$vendor_rel"
pin="$vendor/UBNFC_PIN"
java_package=org.unlaxer.tinyexpression.p4.ubnfc.generated
scanners_source=examples/p4-java/src/main/java/org/ubnfc/p4/P4Scanners.java
first_chars=scanners/first-chars.json

mode=--check
require_full=false
for arg in "$@"; do
  case "$arg" in
    --check|--write) mode=$arg ;;
    --require-full) require_full=true ;;
    *) echo "usage: $0 [--check [--require-full]|--write]" >&2; exit 2 ;;
  esac
done

pin_value() { if [[ -f "$pin" ]]; then sed -n "s/^$1=//p" "$pin"; fi; }
sha() { sha256sum "$1" | cut -d' ' -f1; }

# Vendored files in a stable order, relative to $vendor.
vendored_files() {
  (cd "$vendor" && { find generated -type f -name '*.java'; echo P4Scanners.java; } | LC_ALL=C sort)
}

grammar_rel=$(pin_value grammar)
grammar_rel=${grammar_rel:-tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf}
pinned_commit=$(pin_value ubnfc_commit)

fail=0
offline_check() {
  local expected actual
  expected=$(pin_value grammar_sha256)
  actual=$(sha "$repo/$grammar_rel")
  if [[ "$expected" != "$actual" ]]; then
    echo "DRIFT: $grammar_rel sha256 $actual != pinned $expected" >&2
    echo "       the grammar changed; regenerate: UBNFC_DIR=... $0 --write" >&2
    fail=1
  else
    echo "ok: grammar sha256 matches pin ($actual)"
  fi
  local manifest_pinned manifest_actual
  manifest_pinned=$(sed -n 's/^file //p' "$pin")
  manifest_actual=$(vendored_files | while read -r f; do echo "$(sha "$vendor/$f") $f"; done)
  if [[ "$manifest_pinned" != "$manifest_actual" ]]; then
    echo "DRIFT: vendored files differ from UBNFC_PIN (hand edit, or files added/removed):" >&2
    diff <(echo "$manifest_pinned") <(echo "$manifest_actual") >&2 || true
    fail=1
  else
    echo "ok: $(echo "$manifest_actual" | wc -l) vendored files match pin"
  fi
}

ubnfc_dir=${UBNFC_DIR:-$repo/../ubnfc}
full_available() {
  [[ -d "$ubnfc_dir/.git" || -f "$ubnfc_dir/.git" ]] || return 1
  git -C "$ubnfc_dir" cat-file -e "${1}^{commit}" 2>/dev/null
}

scratch=""
cleanup() { if [[ -n "$scratch" ]]; then rm -rf "$scratch"; fi; }
trap cleanup EXIT

# Builds ubnfc at $1 and regenerates into $scratch/out/{grammar.ir.json,generated/,P4Scanners.java}.
regenerate() {
  local rev=$1
  scratch=$(mktemp -d)
  mkdir -p "$scratch/ubnfc" "$scratch/out"
  git -C "$ubnfc_dir" archive "$rev" | tar -x -C "$scratch/ubnfc"
  echo "building ubnfc front at $rev ..."
  # shellcheck disable=SC2086
  (cd "$scratch/ubnfc" && cargo build -q --release -p ubnfc-front ${CARGO_FLAGS:-})
  echo "building ubnfc Java backend at $rev ..."
  # shellcheck disable=SC2086
  mvn -q ${MVN_FLAGS:-} -f "$scratch/ubnfc/ubnfc-java/pom.xml" -Dmaven.test.skip=true package
  # Run from the repo root so the IR records the grammar by its repo-relative path.
  (cd "$repo" && "$scratch/ubnfc/target/release/ubnfc" ir --grammar "$grammar_rel" \
      --extern-first-chars "$scratch/ubnfc/$first_chars" --out "$scratch/out/grammar.ir.json" \
      2> "$scratch/front-warnings.txt")
  java -cp "$scratch/ubnfc/ubnfc-java/target/ubnfc-java-0.1.0-SNAPSHOT.jar" org.ubnfc.java.Main \
    --ir "$scratch/out/grammar.ir.json" --out "$scratch/gen" --package "$java_package" > /dev/null
  mv "$scratch/gen/${java_package//.//}" "$scratch/out/generated"
  {
    echo "// Vendored from ubnfc $scanners_source by"
    echo "// scripts/regenerate-ubnfc-parser.sh (package renamed only). Do not edit by hand; see UBNFC_PIN."
    sed -e 's/^package org\.ubnfc\.p4;/package org.unlaxer.tinyexpression.p4.ubnfc;/' \
        -e "s/org\\.ubnfc\\.p4\\.generated/${java_package//./\\.}/g" \
        "$scratch/ubnfc/$scanners_source"
  } > "$scratch/out/P4Scanners.java"
}

full_check() {
  regenerate "$pinned_commit"
  local ir_sha
  ir_sha=$(sha "$scratch/out/grammar.ir.json")
  if [[ "$ir_sha" != "$(pin_value ir_sha256)" ]]; then
    echo "DRIFT: IR sha256 $ir_sha != pinned $(pin_value ir_sha256)" >&2
    fail=1
  else
    echo "ok: grammar -> IR is byte-identical to the pin ($ir_sha)"
  fi
  if ! diff -r "$scratch/out/generated" "$vendor/generated" > "$scratch/diff.txt"; then
    echo "DRIFT: regenerated sources differ from $vendor_rel/generated:" >&2
    head -50 "$scratch/diff.txt" >&2
    fail=1
  elif ! cmp -s "$scratch/out/P4Scanners.java" "$vendor/P4Scanners.java"; then
    echo "DRIFT: $vendor_rel/P4Scanners.java differs from ubnfc $scanners_source" >&2
    fail=1
  else
    echo "ok: IR -> Java regeneration at ubnfc $pinned_commit is byte-identical"
  fi
}

case "$mode" in
  --check)
    offline_check
    if full_available "$pinned_commit"; then
      full_check
    elif [[ "$require_full" == true ]]; then
      echo "FAIL: ubnfc checkout with commit $pinned_commit not found at $ubnfc_dir" >&2
      fail=1
    else
      echo "skip: full regeneration (ubnfc checkout with $pinned_commit not at $ubnfc_dir;" \
           "ubnfc is private - set UBNFC_DIR to run it)"
    fi
    exit $fail
    ;;
  --write)
    rev=${UBNFC_REV:-$pinned_commit}
    [[ -n "$rev" ]] || { echo "no pinned commit; set UBNFC_REV" >&2; exit 2; }
    full_available "$rev" || { echo "ubnfc commit $rev not found at $ubnfc_dir" >&2; exit 2; }
    rev=$(git -C "$ubnfc_dir" rev-parse "${rev}^{commit}")
    regenerate "$rev"
    rm -rf "$vendor/generated"
    cp -R "$scratch/out/generated" "$vendor/generated"
    cp "$scratch/out/P4Scanners.java" "$vendor/P4Scanners.java"
    {
      echo "# ubnfc-generated P4 parser vendored into tinyexpression (default engine since 2.0.0)."
      echo "# Written by scripts/regenerate-ubnfc-parser.sh --write. Do not edit by hand."
      echo "# Chain: grammar --(ubnfc front, Rust)--> IR --(ubnfc-java)--> $java_package"
      echo "ubnfc_repository=https://github.com/opaopa6969/ubnfc"
      echo "ubnfc_commit=$rev"
      echo "grammar=$grammar_rel"
      echo "grammar_sha256=$(sha "$repo/$grammar_rel")"
      echo "extern_first_chars=$first_chars"
      echo "ir_sha256=$(sha "$scratch/out/grammar.ir.json")"
      echo "java_package=$java_package"
      echo "scanners_source=$scanners_source"
      vendored_files | while read -r f; do echo "file $(sha "$vendor/$f") $f"; done
    } > "$pin"
    echo "wrote $vendor_rel (ubnfc $rev) and UBNFC_PIN"
    echo "next: mvn compile && python3 scripts/generate-ubnfc-converter.py --write"
    ;;
esac
