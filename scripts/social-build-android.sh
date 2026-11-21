#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
build_image="${PHOTOANCHOR_IMAGE_PREFIX:-photoanchor}-android:local"
docker build -t "$build_image" -f docker/social/Dockerfile.android .
# Retain the debug signing identity so reinstalling a rebuilt APK can preserve
# the installed app's encrypted profile and outbox. This is not a user Nostr key.
authority_pin=""
if [ -n "${PHOTOANCHOR_AUTHORITY_SPKI_FILE:-}" ]; then
  pin_size=$(wc -c < "$PHOTOANCHOR_AUTHORITY_SPKI_FILE" | tr -d ' ')
  pin_prefix=$(od -An -v -tx1 -N27 "$PHOTOANCHOR_AUTHORITY_SPKI_FILE" | tr -d ' \n')
  if [ "$pin_size" != 91 ] || [ "$pin_prefix" != 3059301306072a8648ce3d020106082a8648ce3d03010703420004 ]; then
    printf '%s\n' 'Expected a 91-byte P-256 public SPKI DER file; refusing to embed other key material.' >&2
    exit 1
  fi
  authority_pin=$(base64 < "$PHOTOANCHOR_AUTHORITY_SPKI_FILE" | tr -d '\n')
fi
docker run --rm -v "$PWD:/workspace:z" -v photoanchor-gradle:/root/.gradle -v photoanchor-android-home:/root/.android "$build_image" ./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug "-PphotoAuthoritySpki=$authority_pin"
mkdir -p social-artifacts
cp android/app/build/outputs/apk/debug/app-debug.apk social-artifacts/photoanchor-debug.apk
printf '%s\n' 'APK: social-artifacts/photoanchor-debug.apk'
