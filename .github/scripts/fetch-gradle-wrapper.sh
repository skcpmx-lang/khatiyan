#!/usr/bin/env bash
# Fetches the Gradle wrapper (gradlew scripts + wrapper jar/properties) from the
# specified Gradle version tag on gradle/gradle if it isn't committed in the repo.
set -euo pipefail
VER="${1:-8.9}"
cd "$(dirname "$0")/../.."
if [ -f gradle/wrapper/gradle-wrapper.jar ]; then echo "wrapper present"; exit 0; fi
base="https://raw.githubusercontent.com/gradle/gradle/refs/tags/v${VER}"
curl -fsSL "$base/gradle/wrapper/gradle-wrapper.jar" -o gradle/wrapper/gradle-wrapper.jar
curl -fsSL "$base/gradle/wrapper/gradle-wrapper.properties" -o gradle/wrapper/gradle-wrapper.properties
curl -fsSL "$base/gradlew" -o gradlew
curl -fsSL "$base/gradlew.bat" -o gradlew.bat
chmod +x gradlew
sha256sum gradle/wrapper/gradle-wrapper.jar
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
