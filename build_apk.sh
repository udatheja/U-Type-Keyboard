#!/usr/bin/env bash
set -euo pipefail

echo "Building U Type debug APK and exporting to apk/LankaKeys.apk..."
if [ -x "./gradlew" ]; then
  ./gradlew :app:exportApk
else
  gradle :app:exportApk
fi

echo "Done! APK is ready at: apk/LankaKeys.apk"
ls -lh apk/LankaKeys.apk
