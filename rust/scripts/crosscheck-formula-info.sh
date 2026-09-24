#!/usr/bin/env bash
# Generates the Rust and the Java parser for grammar/formula-info.ubnf with ubnfc and runs
# every FormulaInfo fixture through both (issue #180). Nothing generated here is committed:
# the Java backend exists only for this check; the Rust one is vendored separately by
# regenerate-ubnfc.sh.
#
#   crosscheck-formula-info.sh
#
# Needs the ubnfc checkout ($UBNFC_DIR, else ../ubnfc next to this repository) with its Java
# backend jar built (`cd ubnfc-java && mvn -o -q -DskipTests package`, or UBNFC_JAVA_JAR),
# a JDK 21 and cargo with the serde crates in the offline cache (the generated driver's `json`
# feature). Nothing is written inside the ubnfc checkout.
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$script_dir/../.." && pwd)
ubnfc_dir=${UBNFC_DIR:-"$repo_dir/../ubnfc"}
grammar="$repo_dir/grammar/formula-info.ubnf"
jar=${UBNFC_JAVA_JAR:-$(ls "$ubnfc_dir"/ubnfc-java/target/ubnfc-java-*.jar 2>/dev/null | head -1)}
test -f "$jar" || {
  echo "ubnfc Java backend jar not found (build ubnfc-java or set UBNFC_JAVA_JAR)" >&2
  exit 2
}
export UBNFC_JAVA_JAR="$jar"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export CARGO_TARGET_DIR="$work/target"

echo "== ubnfc validate --strict ==" >&2
(cd "$ubnfc_dir" && cargo build -q --offline --bin ubnfc)
ubnfc="$work/target/debug/ubnfc"
"$ubnfc" validate --grammar "$grammar" --strict

echo "== generate both backends ==" >&2
"$ubnfc" gen --grammar "$grammar" --target rust --out "$work/rust-out" >/dev/null
"$ubnfc" gen --grammar "$grammar" --target java --out "$work/java-out" >/dev/null
(cd "$work/rust-out" && cargo build -q --offline --features json --bin driver)
mkdir -p "$work/java-classes"
javac -encoding UTF-8 -nowarn -d "$work/java-classes" $(find "$work/java-out/src/main/java" -name '*.java')

echo "== run the fixtures ==" >&2
python3 "$script_dir/crosscheck-formula-info.py" "$repo_dir" \
  "$work/target/debug/driver" "$work/java-classes"
