#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DSL_DIR="${ROOT_DIR}/../unlaxer-dsl"
GRAMMAR="${ROOT_DIR}/docs/ubnf/tinyexpression-p4-draft.ubnf"
OUT_DIR="${ROOT_DIR}/target/generated-sources/tinyexpression-p4"
GENERATORS="AST,Parser,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher"

if [[ ! -f "${GRAMMAR}" ]]; then
  echo "Grammar not found: ${GRAMMAR}" >&2
  exit 1
fi

if [[ ! -d "${DSL_DIR}" ]]; then
  echo "unlaxer-dsl directory not found: ${DSL_DIR}" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"

pushd "${DSL_DIR}" >/dev/null
mvn -q -DskipTests compile
mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.unlaxer.dsl.CodegenMain \
  -Dexec.args="--grammar ${GRAMMAR} --output ${OUT_DIR} --generators ${GENERATORS} --report-format json"
popd >/dev/null

echo "Generated sources:" >&2
echo "  ${OUT_DIR}" >&2
