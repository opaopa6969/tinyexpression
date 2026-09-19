#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$script_dir/.." && pwd)
unlaxer_source=${1:-"$repo_dir/../unlaxer-parser"}
revision=$(tr -d '[:space:]' < "$script_dir/unlaxer-revision.txt")

grep -Fq "rev = \"$revision\"" "$script_dir/Cargo.toml" || {
  echo "rust/Cargo.toml runtime revision differs from rust/unlaxer-revision.txt" >&2
  exit 1
}

actual_revision=$(git -C "$unlaxer_source" rev-parse HEAD)
test "$actual_revision" = "$revision" || {
  echo "unlaxer source is $actual_revision; expected $revision" >&2
  exit 1
}

cargo run --locked --manifest-path "$unlaxer_source/rust/Cargo.toml" \
  -p unlaxer-generator -- generate --target rust \
  --grammar "$repo_dir/tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf" \
  --output "$script_dir/tinyexpression-rs/src/generated" --check
