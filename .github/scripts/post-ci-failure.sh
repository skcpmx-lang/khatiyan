#!/usr/bin/env bash
# Build a failure digest from the teed gradle log + JUnit XML reports and file it as an issue.
set -u
SHA="$1"
LOG=/tmp/gradle.log
BODY=/tmp/ci-failure-body.md

{
  echo "Run: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
  echo
  echo '## Gradle failure lines'
  if [ -f "$LOG" ]; then
    grep -E 'FAILURE:|Task .*FAILED|tests completed|There were failing tests|Execution failed|^e: ' "$LOG" | sort -u | head -40
  else
    echo "(no /tmp/gradle.log)"
  fi
  echo
  echo '## Test failures'
  python3 .github/scripts/testdigest.py app/build/test-results 2>/dev/null | head -180
  echo
  echo '## Tail of gradle log'
  tail -50 "$LOG" 2>/dev/null
} > "$BODY"

gh api repos/:owner/:repo/labels -f name=ci-failure -f color=E11C48 -d '{"description":"GitHub Actions failure digest"}' >/dev/null 2>&1 || true
gh issue create --title "CI DIGEST @ ${SHA:0:7}" --body-file "$BODY" --label ci-failure
