#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
scripts/social-up.sh
scripts/social-compose.sh run --rm social-demo
mkdir -p social-artifacts
scripts/social-compose.sh create social-demo
container_id=$(scripts/social-compose.sh ps -aq social-demo)
docker cp "${container_id}:/artifacts/." social-artifacts/
printf '%s\n' 'Docker demo artifacts copied to social-artifacts/. Phone acceptance is a separate manual test.'
