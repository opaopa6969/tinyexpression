#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/build"
SKIP_SERVER=0
MATRIX_THREE=0

inject_runtime_dependencies() {
  local root_dir="$1"
  local vsix_path="$2"
  local normalized_root_dir
  local tmp_dir
  local repacked_vsix
  normalized_root_dir="$(cd "$root_dir" && pwd)"
  tmp_dir="$(mktemp -d)"
  repacked_vsix="$tmp_dir/repacked.vsix"
  unzip -q "$vsix_path" -d "$tmp_dir/unpacked"
  mkdir -p "$tmp_dir/unpacked/extension/node_modules"

  while IFS= read -r dep_path; do
    [[ -z "$dep_path" ]] && continue
    local rel_path="${dep_path#"$normalized_root_dir"/}"
    if [[ "$rel_path" == "$dep_path" ]]; then
      rel_path="node_modules/${dep_path##*/node_modules/}"
    fi
    local target_dir="$tmp_dir/unpacked/extension/$(dirname "$rel_path")"
    mkdir -p "$target_dir"
    cp -a "$dep_path" "$target_dir/"
  done < <(npm list --omit=dev --parseable --depth=99999 | tail -n +2)

  (
    cd "$tmp_dir/unpacked"
    zip -qr "$repacked_vsix" .
  )
  mv "$repacked_vsix" "$vsix_path"
  rm -rf "$tmp_dir"
}

while (($#)); do
  case "$1" in
    --skip-server)
      SKIP_SERVER=1
      shift
      ;;
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    --matrix-three)
      MATRIX_THREE=1
      shift
      ;;
    *)
      echo "unknown option: $1" >&2
      echo "usage: scripts/package-vsix.sh [--skip-server] [--out-dir <dir>] [--matrix-three]" >&2
      exit 1
      ;;
  esac
done

cd "$ROOT_DIR"

mkdir -p "$OUT_DIR"

if [[ "$SKIP_SERVER" -eq 0 ]]; then
  npm run build:server
fi

npm run compile

EXT_NAME="$(node -p "require('./package.json').name")"
EXT_VERSION="$(node -p "require('./package.json').version")"
OUT_FILE="$OUT_DIR/${EXT_NAME}-${EXT_VERSION}.vsix"
TMP_VSIX="$ROOT_DIR/${EXT_NAME}-${EXT_VERSION}.vsix"

# vsce 2.26 may miss root out/extension.js in this repo layout.
# Keep a compatibility copy under extension/out to make packaging stable.
mkdir -p "$ROOT_DIR/extension/out"
cp "$ROOT_DIR/out/extension.js" "$ROOT_DIR/extension/out/extension.js"
cp "$ROOT_DIR/out/extension.js.map" "$ROOT_DIR/extension/out/extension.js.map"

rm -f "$TMP_VSIX"
npm run package
if [[ ! -f "$TMP_VSIX" ]]; then
  echo "VSIX packaging did not produce expected file: $TMP_VSIX" >&2
  exit 1
fi
cp "$TMP_VSIX" "$OUT_FILE"
inject_runtime_dependencies "$ROOT_DIR" "$OUT_FILE"

echo "VSIX packaged: $OUT_FILE"

if [[ "$MATRIX_THREE" -eq 1 ]]; then
  TOKEN_OUT="$OUT_DIR/${EXT_NAME}-${EXT_VERSION}-token.vsix"
  AST_OUT="$OUT_DIR/${EXT_NAME}-${EXT_VERSION}-ast.vsix"
  DSL_OUT="$OUT_DIR/${EXT_NAME}-${EXT_VERSION}-dsl-javacode.vsix"
  cp "$OUT_FILE" "$TOKEN_OUT"
  cp "$OUT_FILE" "$AST_OUT"
  cp "$OUT_FILE" "$DSL_OUT"
  echo "DAP matrix artifacts:"
  echo "  $TOKEN_OUT"
  echo "  $AST_OUT"
  echo "  $DSL_OUT"
  PRESET_DIR="$OUT_DIR/runtime-mode-presets"
  mkdir -p "$PRESET_DIR"
  cat > "$PRESET_DIR/token.settings.json" <<'JSON'
{
  "tinyExpressionLsp.runtimeMode": "token"
}
JSON
  cat > "$PRESET_DIR/ast.settings.json" <<'JSON'
{
  "tinyExpressionLsp.runtimeMode": "ast"
}
JSON
  cat > "$PRESET_DIR/dsl-javacode.settings.json" <<'JSON'
{
  "tinyExpressionLsp.runtimeMode": "dsl-javacode"
}
JSON
  echo "Runtime mode presets:"
  echo "  $PRESET_DIR/token.settings.json"
  echo "  $PRESET_DIR/ast.settings.json"
  echo "  $PRESET_DIR/dsl-javacode.settings.json"
fi
