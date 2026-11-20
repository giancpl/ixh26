#!/usr/bin/env bash
# Run from the repository root. Requires JDK 17+ and Python 3.11+; no Android SDK.
set -eu
classes=$(mktemp -d)
trap 'rm -rf -- "$classes"' EXIT
javac --release 17 -d "$classes" \
  android/app/src/main/java/com/example/photoanchor/ReceiptV1.java \
  android/tests/ReceiptV1Test.java
python3 -B android/tests/check_receipt_vectors.py "$classes"
