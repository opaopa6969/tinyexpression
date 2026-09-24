#!/usr/bin/env bash
# Verifies every generated file under rust/tinyexpression-rs/src/generated.
#
# Since issue #178 the parser is the vendored ubnfc Rust backend output, not the
# unlaxer-runtime generator, so this script no longer takes an unlaxer-parser checkout and
# rust/unlaxer-revision.txt is gone. It now checks:
#   1. src/generated/ubnfc/ and src/generated/ubnfc_formula_info/ (issue #180) against a fresh
#      ubnfc generation at the pin in rust/ubnfc-pin.txt
#      (rust/scripts/regenerate-ubnfc.sh --check; the ubnfc checkout is $UBNFC_DIR or
#      ../ubnfc next to this repository). Where no ubnfc checkout exists (CI: the ubnfc
#      repository is private) the vendored files are checked against the SHA-256 manifest
#      rust/ubnfc-vendored.sha256 / rust/ubnfc-formula-info-vendored.sha256 that --write
#      records, which still catches hand edits;
#   2. src/generated/compat.rs against the two ast.rs files it is derived from.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

repo_dir=$(cd -- "$script_dir/.." && pwd)
if [ -d "${UBNFC_DIR:-$repo_dir/../ubnfc}" ]; then
  bash "$script_dir/scripts/regenerate-ubnfc.sh" --check
else
  echo "note: no ubnfc checkout; verifying the vendored files against rust/ubnfc-vendored.sha256" >&2
  (cd "$script_dir/tinyexpression-rs/src/generated/ubnfc" &&
    find . -type f | LC_ALL=C sort | xargs sha256sum) |
    diff - "$script_dir/ubnfc-vendored.sha256"
  echo "vendored manifest: matches"
  (cd "$script_dir/tinyexpression-rs/src/generated/ubnfc_formula_info" &&
    find . -type f | LC_ALL=C sort | xargs sha256sum) |
    diff - "$script_dir/ubnfc-formula-info-vendored.sha256"
  echo "vendored FormulaInfo manifest: matches"
fi
python3 "$script_dir/scripts/generate-compat.py" --check
