#!/usr/bin/env bash
# Builds a compiler-error digest from /tmp/out.log and files it as a GitHub issue
# (job logs are hard to fetch programmatically; an issue body is trivially readable).
set -uo pipefail
OUT="${1:-/tmp/out.log}"
ERRS=/tmp/errs.txt
grep -E "^e: " "$OUT" | sort -u | head -n 400 > "$ERRS" || true
{
  echo ""
  echo "---- tail of gradle output ----"
  tail -n 60 "$OUT"
} >> "$ERRS"
SIZE=$(wc -c < "$ERRS")
head -c 60000 "$ERRS" > /tmp/errs-trunc.txt
gh label create ci-failure --color B3261E --description "GitHub Actions build failure" 2>/dev/null || true
gh issue create \
  --title "COMPILE DIGEST @ $(git rev-parse --short HEAD)" \
  --body-file /tmp/errs-trunc.txt \
  --label ci-failure || \
gh issue create \
  --title "COMPILE DIGEST @ $(git rev-parse --short HEAD)" \
  --body-file /tmp/errs-trunc.txt || true
echo "digest filed (${SIZE} bytes)"
