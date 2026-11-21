#!/usr/bin/env bash
# Repo root; JDK 17+, Python hybrid extra, and a verified BC 1.87 JAR are required.
set -eu
test "$#" -eq 1 || { echo "Usage: bash android/tests/run-hybrid-tests.sh /path/to/bcprov-1.87.jar" >&2; exit 1; }
task_bcjar=$(realpath "$1")
task_work=$(mktemp -d)
trap 'rm -rf -- "$task_work"' EXIT
javac --release 17 -cp "$task_bcjar" -d "$task_work/classes" \
  android/app/src/main/java/com/example/photoanchor/ReceiptV1.java \
  android/app/src/main/java/com/example/photoanchor/InstallationSignature.java \
  android/app/src/main/java/com/example/photoanchor/HybridSoftwareKeys.java \
  android/app/src/main/java/com/example/photoanchor/HybridKeyEnvelope.java \
  android/app/src/main/java/com/example/photoanchor/HybridReceiptV2.java \
  android/tests/HybridCryptoTest.java
java -cp "$task_work/classes:$task_bcjar" HybridCryptoTest "$task_work/output"
python3 -B android/tests/check_hybrid_interop.py "$task_work/output"
java -cp "$task_work/classes:$task_bcjar" HybridCryptoTest "$task_work/output/python" verify
