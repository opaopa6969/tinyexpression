#!/usr/bin/env bash
# Alternating Java A/B for unlaxer changes measured through tinyexpression's JMH benchmarks.
#
# Usage:
#   benchmarks/tools/run-java-ab.sh <results-dir> <name>=<maven-repo-local> [<name>=<maven-repo-local> ...]
#
# Each <maven-repo-local> is an isolated Maven repository that contains the unlaxer build to
# measure (see benchmarks/README.md, "isolated Maven repository"). Variants are run alternately
# (run1: v1 v2 ..., run2: v1 v2 ..., run3), each preceded by an idle wait, so host noise spreads
# evenly. Results land in <results-dir>/java-<name>-run<N>.json plus java-x64-<name>.json, and
# can be summarised with benchmarks/tools/ab-summary.py.
#
# Run from a detached benchmark worktree (git worktree add --detach <sha>), not from the PR
# branch's worktree: `gh pr merge --delete-branch` removes a worktree that has the branch checked out.
set -uo pipefail
R=${1:?results dir}; shift
[ $# -ge 1 ] || { echo "at least one <name>=<repo> variant is required" >&2; exit 2; }
mkdir -p "$R"
BENCH=${AB_BENCH:-'P4ParserBenchmark.(publicFacade|parseOnlySafe)'}
FIXTURES=${AB_FIXTURES:-'complex.tiny,comparison-heavy.tiny'}
SCALED=${AB_SCALED_FIXTURES:-'complex-x64.tiny,complex-half.tiny,complex-tail.tiny'}
RUNS=${AB_RUNS:-3}
wait_idle() {
  for i in $(seq 1 120); do
    load=$(awk '{print $1}' /proc/loadavg)
    others=$(pgrep -f "org.codehaus.plexus.classworlds|org.openjdk.jmh|surefire" | wc -l)
    others=$((others + $(pgrep -x cargo | wc -l) + $(pgrep -x rustc | wc -l)))
    if awk "BEGIN{exit !($load < ${AB_MAX_LOAD:-2.5})}" && [ "$others" -eq 0 ]; then return; fi
    sleep 30
  done
  echo "idle wait timed out (load=$load others=$others)"
}
for run in $(seq 1 "$RUNS"); do
  for variant in "$@"; do
    name=${variant%%=*}; repo=${variant#*=}
    wait_idle
    echo "== $name run$run $(date +%T) load=$(awk '{print $1}' /proc/loadavg)"
    TINYEXPRESSION_MAVEN_REPO_LOCAL=$repo benchmarks/run-java-parser-benchmark.sh "$BENCH" -p "fixture=$FIXTURES" -rf json -rff "$R/java-$name-run$run.json" > "$R/java-$name-run$run.log" 2>&1
  done
done
for variant in "$@"; do
  name=${variant%%=*}; repo=${variant#*=}
  wait_idle
  echo "== scaled $name $(date +%T)"
  TINYEXPRESSION_MAVEN_REPO_LOCAL=$repo benchmarks/run-java-parser-benchmark.sh "$BENCH|P4ParserBenchmark.facadeAny" -p "fixture=$SCALED" -f 1 -wi 3 -i 5 -rf json -rff "$R/java-x64-$name.json" > "$R/java-x64-$name.log" 2>&1
done
echo "== all done $(date +%T)"
