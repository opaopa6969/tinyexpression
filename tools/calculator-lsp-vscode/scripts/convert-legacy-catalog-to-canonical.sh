#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "usage: $0 <legacy-catalog-file> [<legacy-catalog-file> ...]" >&2
  echo "output: canonical tinyexpression catalog (v1) to stdout" >&2
  exit 1
fi

echo "# tinyexpression canonical catalog"
echo "${TINYEXPR_CANONICAL_MARKER:-tinyexpression-catalog-v1}"

for file in "$@"; do
  if [ ! -f "$file" ]; then
    echo "warning: skip missing file: $file" >&2
    continue
  fi
  awk -F'|' '
    BEGIN { OFS="|" }
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*$/ { next }
    {
      name=$1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", name)
      if (name == "") next
      if (substr(name,1,1) == "$") name=substr(name,2)
      if (name ~ /_\*$/) {
        prefix=name
        sub(/_\*$/, "", prefix)
        if (prefix != "") print "prefixWithSuffix", prefix, "_", "1"
      } else {
        print "exact", name
      }
    }
  ' "$file"
done | sort -u
