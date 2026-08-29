#!/usr/bin/env bash
# Detect and strip UTF-8 BOM (EF BB BF) from all .java files.
set -euo pipefail

found=0
while IFS= read -r -d '' f; do
  first3=$(head -c 3 "$f" | od -An -tx1 | tr -d ' \n')
  if [ "$first3" = "efbbbf" ]; then
    echo "BOM found: $f"
    found=$((found+1))
    if [ "${1:-}" = "--fix" ]; then
      # Strip the first 3 bytes (BOM) in place
      tail -c +4 "$f" > "$f.nobom" && mv "$f.nobom" "$f"
      echo "  -> stripped"
    fi
  fi
done < <(find . \( -name "*.java" -o -name "*.yml" -o -name "*.yaml" -o -name "*.properties" \) -type f -print0)

echo "Total files with BOM: $found"
