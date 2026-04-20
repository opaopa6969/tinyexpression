#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_GRAMMAR="${ROOT_DIR}/tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf"
DSL_DIR="${DSL_DIR:-}"
GRAMMAR="${GRAMMAR:-}"
GRAMMAR_FILE="${GRAMMAR_FILE:-}"
CODEGEN_PROJECT_DIR="${ROOT_DIR}"

if [[ -z "${DSL_DIR}" ]]; then
  for candidate in "${ROOT_DIR}/../unlaxer-dsl" "${ROOT_DIR}/../unlaxer-parser/unlaxer-dsl"; do
    if [[ -d "${candidate}" ]]; then
      DSL_DIR="${candidate}"
      break
    fi
  done
fi

if [[ -z "${GRAMMAR}" ]]; then
  if [[ -n "${GRAMMAR_FILE}" && -f "${ROOT_DIR}/docs/ubnf/${GRAMMAR_FILE}" ]]; then
    GRAMMAR="${ROOT_DIR}/docs/ubnf/${GRAMMAR_FILE}"
  elif [[ -n "${GRAMMAR_FILE}" && -f "${ROOT_DIR}/${GRAMMAR_FILE}" ]]; then
    GRAMMAR="${ROOT_DIR}/${GRAMMAR_FILE}"
  else
    GRAMMAR="${DEFAULT_GRAMMAR}"
  fi
fi
OUT_DIR_BASE="${ROOT_DIR}/target/generated-sources/tinyexpression-p4"
RUNTIME_OUT_DIR="${OUT_DIR_BASE}/runtime"
TOOLING_OUT_DIR="${OUT_DIR_BASE}/tooling"
RUNTIME_GENERATORS="AST,Parser,Mapper,Evaluator"
TOOLING_GENERATORS="LSP,Launcher,DAP,DAPLauncher"

if [[ ! -f "${GRAMMAR}" ]]; then
  echo "Grammar not found: ${GRAMMAR}" >&2
  exit 1
fi

mkdir -p "${RUNTIME_OUT_DIR}" "${TOOLING_OUT_DIR}"

if [[ -n "${DSL_DIR}" && -d "${DSL_DIR}" && -w "${DSL_DIR}" ]]; then
  CODEGEN_PROJECT_DIR="${DSL_DIR}"
fi

run_codegen() {
  local output_dir="$1"
  local generators="$2"
  mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.unlaxer.dsl.CodegenMain \
  -Dexec.args="--grammar ${GRAMMAR} --output ${output_dir} --generators ${generators} --report-format json"
}

pushd "${CODEGEN_PROJECT_DIR}" >/dev/null
if [[ "${CODEGEN_PROJECT_DIR}" == "${DSL_DIR}" ]]; then
  mvn -q -DskipTests -Dflatten.skip=true compile
fi
run_codegen "${RUNTIME_OUT_DIR}" "${RUNTIME_GENERATORS}"
run_codegen "${TOOLING_OUT_DIR}" "${TOOLING_GENERATORS}"
popd >/dev/null

echo "Generated sources:" >&2
echo "  runtime: ${RUNTIME_OUT_DIR}" >&2
echo "  tooling: ${TOOLING_OUT_DIR}" >&2
