#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.gradle}"
export ANDROID_HOME="${ANDROID_HOME:-/workspace/.android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/.android-user}"
if [[ -x /workspace/.tools/thep-jdk21/bin/javac ]]; then
  export JAVA_HOME=/workspace/.tools/thep-jdk21
elif [[ -x /workspace/.tools/jdk-21.0.12.1/bin/javac ]]; then
  export JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
fi
exec ./gradlew "$@"
