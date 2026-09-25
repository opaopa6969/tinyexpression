#!/usr/bin/env bash
# Re-derives the Java golden of the differential test (issue #179) from the Java
# P4_AST_EVALUATOR backend (AstEvaluatorCalculator → P4TypedAstEvaluator).
#
#   regenerate-java-golden.sh           # rewrite tests/java-diff/golden/
#   regenerate-java-golden.sh --check   # regenerate into a scratch dir and diff
#
# Needs a JDK 21, Maven (the project's dependencies resolvable; pass e.g.
# MVN_ARGS="-o" or MVN_ARGS="-Dmaven.repo.local=/path"), python3 and cargo.
# Parse/probe deadlines are disabled: under load the default 5 s probe budget turns slow
# parses of the deep fraud-alert formulas into rejections that are not semantic.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../../.." && pwd)
mode=${1:-write}
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

echo "[1/5] building tinyexpression classes" >&2
(cd "$root" && mvn -q ${MVN_ARGS:-} -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile="$scratch/cp.txt" >&2)
cp="$root/target/classes:$(cat "$scratch/cp.txt")"

echo "[2/5] compiling the Java driver and external test classes" >&2
mkdir -p "$scratch/driver"
javac -nowarn -d "$scratch/driver" -cp "$cp" \
  "$here/JavaDiffDriver.java" \
  "$here/externals/CheckDigits.java" \
  "$here/externals/sample/v1/CheckAlphabets.java" \
  "$root/src/test/java/org/unlaxer/tinyexpression/Fee.java" \
  "$root/src/test/java/org/unlaxer/tinyexpression/parser/TestSideEffector.java"

echo "[3/5] collecting the corpus" >&2
python3 "$here/build_corpus.py" > "$scratch/corpus.jsonl"
cargo build --release --locked --manifest-path "$root/rust/Cargo.toml" -p tinyexpression-rs >&2
python3 "$here/split_corpus.py" "$scratch/corpus.jsonl" "$root/rust/target/release/tinyexpression" \
  "$scratch/corpus-accepted.jsonl" "$scratch/corpus-probe.jsonl"

echo "[4/5] evaluating on the Java side" >&2
for part in accepted probe; do
  java -Xmx3g -Dtinyexpression.p4.parse.timeout.millis=0 -Dtinyexpression.p4.probe.timeout.millis=0 \
    -cp "$scratch/driver:$cp" JavaDiffDriver \
    "$scratch/corpus-$part.jsonl" "$scratch/java-$part.jsonl" p4ast
done

echo "[5/5] writing the golden" >&2
out="$here/golden"
if [[ "$mode" == "--check" ]]; then
  out="$scratch/golden"
fi
python3 "$here/merge_golden.py" "$out" \
  "$scratch/corpus-accepted.jsonl" "$scratch/java-accepted.jsonl" \
  "$scratch/corpus-probe.jsonl" "$scratch/java-probe.jsonl"
if [[ "$mode" == "--check" ]]; then
  diff -r "$here/golden" "$out" && echo "golden is up to date" >&2
fi
