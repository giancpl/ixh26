#!/usr/bin/env bash
# From the repository root: JDK 17+, Python prnu/hybrid extras.
set -euo pipefail
task_tmp=$(mktemp -d)
trap 'rm -rf -- "$task_tmp"' EXIT
if [[ $# -gt 1 ]]; then
    echo 'Usage: bash scripts/check-crypto-vectors.sh [verified-bcprov-1.87.jar]' >&2
    exit 1
fi
task_bcjar=${1:-$task_tmp/bcprov.jar}
if [[ $# -eq 0 ]]; then
    curl -fL --retry 3 --connect-timeout 20 --max-time 120 \
      https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.87/bcprov-jdk18on-1.87.jar \
      -o "$task_bcjar"
fi
echo "12d2d3dd82a9ec15e92ec7c7943ee64f6097c6dc12c07e984f1693b77565b9aa  $task_bcjar" | sha256sum -c -
bash android/tests/run-installation-signatures.sh
bash android/tests/run-receipt-vectors.sh
bash android/tests/run-evidence-v3.sh
bash android/tests/run-prnu.sh
bash android/tests/run-receipt-v3.sh
bash android/tests/run-hybrid-tests.sh "$task_bcjar"
