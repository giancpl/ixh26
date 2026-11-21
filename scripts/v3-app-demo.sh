#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
scripts/social-up.sh
scripts/social-compose.sh run --rm v3-app-demo
mkdir -p social-artifacts
scripts/social-compose.sh create v3-app-demo
container_id=$(scripts/social-compose.sh ps -aq v3-app-demo)
docker cp "${container_id}:/artifacts/." social-artifacts/
printf '%s\n' 'v3 artifacts: social-artifacts/v3-app-*/. Identity/camera fixtures are SYNTHETIC; relay, OTS and Core are real.'
