#!/usr/bin/env bash
set -euo pipefail

benchmark_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repository_dir=$(cd -- "$benchmark_dir/.." && pwd)
classpath_file="$repository_dir/target/jmh-classpath.txt"

maven_arguments=(
  -B
  -Pparser-benchmark
  -DskipTests
  -Dtinyexpression.skipRailroad=true
)
if [[ -n "${TINYEXPRESSION_MAVEN_REPO_LOCAL:-}" ]]; then
  maven_arguments+=("-Dmaven.repo.local=$TINYEXPRESSION_MAVEN_REPO_LOCAL")
fi

cd -- "$repository_dir"
mvn "${maven_arguments[@]}" test-compile \
  org.apache.maven.plugins:maven-dependency-plugin:3.7.0:build-classpath \
  -DincludeScope=test -Dmdep.outputFile="$classpath_file"

benchmark_classpath=$(tr -d '\r\n' < "$classpath_file")
exec java \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  -cp "target/test-classes:target/classes:$benchmark_classpath" \
  org.openjdk.jmh.Main "$@"
