#!/usr/bin/env bash
# Repository root, JDK 17+ and Python 3.11+. No Android SDK or external dependency.
set -eu
task_classes=$(mktemp -d)
trap 'rm -rf -- "$task_classes"' EXIT
javac --release 17 -d "$task_classes" \
  android/app/src/main/java/com/example/photoanchor/EvidenceV3.java \
  android/tests/EvidenceV3Test.java
python3 -B android/tests/check_evidence_v3.py "$task_classes"
