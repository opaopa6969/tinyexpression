#!/usr/bin/env bash
# Isolated toolchain installation: runners need curl, not a preinstalled rustup.
set -euo pipefail
toolchain=${1:?Rust toolchain is required}
shift
rust_dir=$(mktemp -d "${RUNNER_TEMP:?}/tinyexpression-rust.XXXXXX")
export CARGO_HOME="$rust_dir/cargo"
export RUSTUP_HOME="$rust_dir/rustup"
export PATH="$CARGO_HOME/bin:$PATH"
curl --proto '=https' --tlsv1.2 -fsSL https://sh.rustup.rs -o "$rust_dir/rustup-init.sh"
bash "$rust_dir/rustup-init.sh" -y --no-modify-path --default-toolchain none
rustup toolchain install "$toolchain" --profile minimal --target wasm32-unknown-unknown "$@"
echo "$CARGO_HOME/bin" >> "${GITHUB_PATH:?}"
echo "CARGO_HOME=$CARGO_HOME" >> "${GITHUB_ENV:?}"
echo "RUSTUP_HOME=$RUSTUP_HOME" >> "$GITHUB_ENV"
echo "RUSTUP_TOOLCHAIN=$toolchain" >> "$GITHUB_ENV"
