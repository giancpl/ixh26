#!/usr/bin/env bash
set -eu
classes=$(mktemp -d)
trap 'rm -rf -- "$classes"' EXIT
javac --release 17 -d "$classes" \
 android/app/src/main/java/com/example/photoanchor/ReceiptV1.java \
 android/app/src/main/java/com/example/photoanchor/ReceiptV3.java \
 android/app/src/main/java/com/example/photoanchor/InstallationSignature.java \
 android/tests/ReceiptV3Test.java
python3 -B android/tests/check_receipt_v3.py "$classes"
