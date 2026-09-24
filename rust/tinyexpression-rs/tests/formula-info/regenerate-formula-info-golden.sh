#!/usr/bin/env bash
# Re-derives the Java golden of the FormulaInfo loader parity test (issue #180) from the
# hand-written Java loader (org.unlaxer.tinyexpression.loader, FormulaInfoList.parse).
#
#   regenerate-formula-info-golden.sh           # rewrite tests/formula-info/golden/java.jsonl
#   regenerate-formula-info-golden.sh --check   # regenerate into a scratch dir and diff
#
# Needs a JDK 21 and Maven (pass e.g. MVN_ARGS="-o"). `cargo test` never needs a JVM: it
# compares the Rust loader with the committed golden (tests/formula_info.rs).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../../.." && pwd)
mode=${1:-write}
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

echo "[1/3] building tinyexpression classes" >&2
(cd "$root" && mvn -q ${MVN_ARGS:-} -DskipTests -Dtinyexpression.skipRailroad=true compile dependency:build-classpath \
  -Dmdep.outputFile="$scratch/cp.txt" >&2)
cp="$root/target/classes:$(cat "$scratch/cp.txt")"

echo "[2/3] compiling the Java driver and external test classes" >&2
mkdir -p "$scratch/driver"
javac -nowarn -d "$scratch/driver" -cp "$cp" \
  "$here/FormulaInfoParityDriver.java" \
  "$here/../java-diff/externals/CheckDigits.java" \
  "$here/../java-diff/externals/sample/v1/CheckAlphabets.java"

echo "[3/3] dumping the Java loader's view of every fixture" >&2
cd "$root"
mapfile -t fixtures < <(
  printf '%s\n' src/test/resources/formulaInfo.fi
  find src/test/resources/formulaInfo-test -name formulaInfo.txt | LC_ALL=C sort
  find src/test/resources/formulaInfo-ubnf -name '*.fi' | LC_ALL=C sort
)
out="$here/golden/java.jsonl"
if [[ "$mode" == "--check" ]]; then
  out="$scratch/java.jsonl"
fi
java -cp "$scratch/driver:$cp" FormulaInfoParityDriver "$root" "$out" "${fixtures[@]}"
if [[ "$mode" == "--check" ]]; then
  diff "$here/golden/java.jsonl" "$out" && echo "golden is up to date" >&2
fi
