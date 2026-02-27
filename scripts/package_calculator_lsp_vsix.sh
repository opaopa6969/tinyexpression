#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VENDORED_PLUGIN_DIR="$ROOT_DIR/tools/calculator-lsp-vscode"
SIBLING_PLUGIN_DIR="$ROOT_DIR/../calculator-lsp-vscode"
if [[ -d "$SIBLING_PLUGIN_DIR" ]]; then
  PLUGIN_DIR="$SIBLING_PLUGIN_DIR"
elif [[ -d "$VENDORED_PLUGIN_DIR" ]]; then
  PLUGIN_DIR="$VENDORED_PLUGIN_DIR"
else
  PLUGIN_DIR="$SIBLING_PLUGIN_DIR"
fi
OUT_DIR="$ROOT_DIR/build/vsix"
SKIP_SERVER=1
MATRIX_THREE=0

inject_runtime_dependencies() {
  local plugin_dir="$1"
  local vsix_path="$2"
  local normalized_plugin_dir
  local tmp_dir
  local repacked_vsix
  normalized_plugin_dir="$(cd "$plugin_dir" && pwd)"
  tmp_dir="$(mktemp -d)"
  repacked_vsix="$tmp_dir/repacked.vsix"
  unzip -q "$vsix_path" -d "$tmp_dir/unpacked"
  mkdir -p "$tmp_dir/unpacked/extension/node_modules"

  while IFS= read -r dep_path; do
    [[ -z "$dep_path" ]] && continue
    local rel_path="${dep_path#"$normalized_plugin_dir"/}"
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
    --plugin-dir)
      PLUGIN_DIR="$2"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    --skip-server)
      SKIP_SERVER=1
      shift
      ;;
    --with-server-build)
      SKIP_SERVER=0
      shift
      ;;
    --matrix-three)
      MATRIX_THREE=1
      shift
      ;;
    *)
      echo "unknown option: $1" >&2
      echo "usage: scripts/package_calculator_lsp_vsix.sh [--plugin-dir <dir>] [--out-dir <dir>] [--skip-server] [--with-server-build] [--matrix-three]" >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "$PLUGIN_DIR" ]]; then
  echo "plugin directory not found: $PLUGIN_DIR" >&2
  exit 1
fi
PLUGIN_DIR="$(cd "$PLUGIN_DIR" && pwd)"

mkdir -p "$OUT_DIR"

cd "$PLUGIN_DIR"

if [[ "$SKIP_SERVER" -eq 0 ]]; then
  npm run build:server
fi

npm run compile

mkdir -p "$PLUGIN_DIR/extension/out"
cp "$PLUGIN_DIR/out/extension.js" "$PLUGIN_DIR/extension/out/extension.js"
cp "$PLUGIN_DIR/out/extension.js.map" "$PLUGIN_DIR/extension/out/extension.js.map"
mkdir -p "$PLUGIN_DIR/extension/extension/out"
cp "$PLUGIN_DIR/out/extension.js" "$PLUGIN_DIR/extension/extension/out/extension.js"
cp "$PLUGIN_DIR/out/extension.js.map" "$PLUGIN_DIR/extension/extension/out/extension.js.map"

EXT_NAME="$(node -p "require('./package.json').name")"
EXT_VERSION="$(node -p "require('./package.json').version")"
TMP_VSIX="$PLUGIN_DIR/${EXT_NAME}-${EXT_VERSION}.vsix"
OUT_PREFIX="tinyExpression-${EXT_NAME}-${EXT_VERSION}"
OUT_FILE="$OUT_DIR/${OUT_PREFIX}.vsix"
START_TS="$(date +%s)"

attempt=1
max_attempts=5
while true; do
  if npm run package; then
    if [[ -f "$TMP_VSIX" ]]; then
      TMP_TS="$(stat -c %Y "$TMP_VSIX")"
      if [[ "$TMP_TS" -ge "$START_TS" ]]; then
        break
      fi
    fi
  fi
  if [[ "$attempt" -ge "$max_attempts" ]]; then
    echo "failed to package VSIX after ${max_attempts} attempts" >&2
    break
  fi
  echo "VSIX packaging attempt ${attempt} failed, retrying..." >&2
  attempt=$((attempt + 1))
  sleep 1
done

if [[ ! -f "$TMP_VSIX" ]]; then
  echo "VSIX was not created: $TMP_VSIX" >&2
  exit 1
fi
TMP_TS="$(stat -c %Y "$TMP_VSIX")"
if [[ "$TMP_TS" -lt "$START_TS" ]]; then
  echo "VSIX was not refreshed by this run: $TMP_VSIX" >&2
  exit 1
fi
cp "$TMP_VSIX" "$OUT_FILE"
inject_runtime_dependencies "$PLUGIN_DIR" "$OUT_FILE"
echo "VSIX packaged from tinyexpression workspace: $OUT_FILE"

if [[ "$MATRIX_THREE" -eq 1 ]]; then
  TOKEN_OUT="$OUT_DIR/${OUT_PREFIX}-token.vsix"
  AST_OUT="$OUT_DIR/${OUT_PREFIX}-ast.vsix"
  DSL_OUT="$OUT_DIR/${OUT_PREFIX}-dsl-javacode.vsix"
  cp "$OUT_FILE" "$TOKEN_OUT"
  cp "$OUT_FILE" "$AST_OUT"
  cp "$OUT_FILE" "$DSL_OUT"

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

  echo "DAP matrix artifacts:"
  echo "  $TOKEN_OUT"
  echo "  $AST_OUT"
  echo "  $DSL_OUT"
  echo "Runtime mode presets:"
  echo "  $PRESET_DIR/token.settings.json"
  echo "  $PRESET_DIR/ast.settings.json"
  echo "  $PRESET_DIR/dsl-javacode.settings.json"
fi
