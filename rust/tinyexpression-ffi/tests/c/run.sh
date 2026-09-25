#!/usr/bin/env bash
# Builds libtinyexpression.so and links tests/c/smoke.c against it with gcc (issue #181).
#   rust/tinyexpression-ffi/tests/c/run.sh [cargo profile, default release]
set -euo pipefail
here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
rust_dir=$(cd -- "$here/../../.." && pwd)
profile=${1:-release}
cargo build --locked --manifest-path "$rust_dir/Cargo.toml" -p tinyexpression-ffi --profile "$profile"
lib_dir="$rust_dir/target/$profile"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
${CC:-gcc} -std=c11 -Wall -Wextra -Werror -I "$rust_dir/tinyexpression-ffi/include" \
  "$here/smoke.c" -L "$lib_dir" -ltinyexpression -Wl,-rpath,"$lib_dir" -o "$work/smoke"
"$work/smoke"
