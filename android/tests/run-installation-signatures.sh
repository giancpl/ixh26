#!/usr/bin/env bash
# Run from repository root with JDK 17+; no Android SDK or licensing operation.
set -eu
classes=$(mktemp -d)
trap 'rm -rf -- "$classes"' EXIT
javac --release 17 -d "$classes" \
  android/app/src/main/java/com/example/photoanchor/ReceiptV1.java \
  android/app/src/main/java/com/example/photoanchor/InstallationSignature.java \
  android/tests/InstallationSignatureTest.java
java -cp "$classes" InstallationSignatureTest "$@"
