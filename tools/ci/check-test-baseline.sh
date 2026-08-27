#!/usr/bin/env bash
# surefire の失敗テストを test-baseline.txt (既知の失敗) と比較する。
#
# - ベースラインに無い新規失敗 → exit 1 (CI を落とす)
# - ベースラインに有るが今回は通った → 警告のみ (ベースラインの縮小を促す)
#
# 使い方: リポジトリルートで `bash tools/ci/check-test-baseline.sh`
# 前提: `mvn verify -Dmaven.test.failure.ignore=true` 実行後であること。
set -euo pipefail

baseline="test-baseline.txt"
reports_dir="target/surefire-reports"

if [ "${1:-}" != "--write-baseline" ] && [ ! -f "$baseline" ]; then
  echo "ERROR: $baseline がありません。フルテスト実行後に以下で生成してください:" >&2
  echo "  bash tools/ci/check-test-baseline.sh --write-baseline" >&2
  exit 1
fi

collect_failures() {
  python3 - "$reports_dir" <<'PY'
import glob, sys, xml.etree.ElementTree as ET

names = set()
for path in glob.glob(sys.argv[1] + "/TEST-*.xml"):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            names.add(f"{case.get('classname')}#{case.get('name')}")
print("\n".join(sorted(names)))
PY
}

actual_file=$(mktemp)
collect_failures > "$actual_file"

if [ "${1:-}" = "--write-baseline" ]; then
  cp "$actual_file" "$baseline"
  echo "wrote $(wc -l < "$baseline") failures to $baseline"
  exit 0
fi

new_failures=$(comm -13 <(sort "$baseline") <(sort "$actual_file"))
fixed=$(comm -23 <(sort "$baseline") <(sort "$actual_file"))

if [ -n "$fixed" ]; then
  echo "::notice::ベースラインに有るが今回は通ったテスト ($(echo "$fixed" | wc -l) 件)。test-baseline.txt から削除を検討:"
  echo "$fixed" | sed 's/^/  FIXED: /'
fi

if [ -n "$new_failures" ]; then
  echo "::error::ベースラインに無い新規失敗 ($(echo "$new_failures" | wc -l) 件):"
  echo "$new_failures" | sed 's/^/  NEW: /'
  exit 1
fi

echo "OK: 新規失敗なし (既知の失敗 $(wc -l < "$baseline") 件は test-baseline.txt 参照)"
