#!/usr/bin/env bash
set -eu
classes=$(mktemp -d)
trap 'rm -rf -- "$classes"' EXIT
javac --release 17 -d "$classes" \
 android/app/src/main/java/com/example/photoanchor/PrnuExperiment.java \
 android/app/src/main/java/com/example/photoanchor/PrnuReferenceRecord.java \
 android/tests/PrnuExperimentTest.java
python3 -B android/tests/check_prnu.py "$classes"
