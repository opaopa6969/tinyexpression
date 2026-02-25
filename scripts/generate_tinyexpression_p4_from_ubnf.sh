#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DSL_DIR="${ROOT_DIR}/../unlaxer-dsl"
GRAMMAR="${ROOT_DIR}/docs/ubnf/tinyexpression-p4-draft.ubnf"
OUT_DIR_BASE="${ROOT_DIR}/target/generated-sources/tinyexpression-p4"
RUNTIME_OUT_DIR="${OUT_DIR_BASE}/runtime"
TOOLING_OUT_DIR="${OUT_DIR_BASE}/tooling"
RUNTIME_GENERATORS="AST,Parser,Mapper,Evaluator"
TOOLING_GENERATORS="LSP,Launcher,DAP,DAPLauncher"

if [[ ! -f "${GRAMMAR}" ]]; then
  echo "Grammar not found: ${GRAMMAR}" >&2
  exit 1
fi

if [[ ! -d "${DSL_DIR}" ]]; then
  echo "unlaxer-dsl directory not found: ${DSL_DIR}" >&2
  exit 1
fi

mkdir -p "${RUNTIME_OUT_DIR}" "${TOOLING_OUT_DIR}"

pushd "${DSL_DIR}" >/dev/null
mvn -q -DskipTests compile
mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.unlaxer.dsl.CodegenMain \
  -Dexec.args="--grammar ${GRAMMAR} --output ${RUNTIME_OUT_DIR} --generators ${RUNTIME_GENERATORS} --report-format json"
mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.unlaxer.dsl.CodegenMain \
  -Dexec.args="--grammar ${GRAMMAR} --output ${TOOLING_OUT_DIR} --generators ${TOOLING_GENERATORS} --report-format json"
popd >/dev/null

echo "Generated sources:" >&2
echo "  runtime: ${RUNTIME_OUT_DIR}" >&2
echo "  tooling: ${TOOLING_OUT_DIR}" >&2
