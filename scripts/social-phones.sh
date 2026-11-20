#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
apk=${1:-social-artifacts/photoanchor-debug.apk}
test -f "$apk" || { printf '%s\n' 'Build the APK first with scripts/social-build-android.sh' >&2; exit 1; }
devices=$(adb devices | awk 'NR>1 && $2 == "device" {print $1}')
test -n "$devices" || { printf '%s\n' 'Connect and authorize the Android USB devices first.' >&2; exit 1; }
for serial in $devices; do
    adb -s "$serial" reverse tcp:8080 tcp:8080
    adb -s "$serial" reverse tcp:7777 tcp:7777
    adb -s "$serial" install -r "$apk"
    printf 'Installed and configured %s\n' "$serial"
done
