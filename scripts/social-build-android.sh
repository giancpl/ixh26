#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
build_image="${PHOTOANCHOR_IMAGE_PREFIX:-photoanchor}-android:local"
docker build -t "$build_image" -f docker/social/Dockerfile.android .
# Retain the debug signing identity so reinstalling a rebuilt APK can preserve
# the installed app's encrypted profile and outbox. This is not a user Nostr key.
docker run --rm -v "$PWD:/workspace:z" -v photoanchor-gradle:/root/.gradle -v photoanchor-android-home:/root/.android "$build_image" ./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
mkdir -p social-artifacts
cp android/app/build/outputs/apk/debug/app-debug.apk social-artifacts/photoanchor-debug.apk
printf '%s\n' 'APK: social-artifacts/photoanchor-debug.apk'
