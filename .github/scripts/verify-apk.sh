#!/usr/bin/env bash
# Inspects an APK with aapt2 and prints package/label/version info.
set -euo pipefail
APK="${1:?usage: verify-apk.sh <apk>}"
AAPT2=$(ls -1 "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)
if [ -z "${AAPT2:-}" ]; then echo "aapt2 not found"; exit 1; fi
"$AAPT2" dump badging "$APK" | grep -E "^package:|application-label:|sdkVersion:|targetSdkVersion:|launchable-activity:"
echo "size_bytes=$(stat -c %s "$APK")"
