#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
case "${1:-}" in
  '') scripts/social-up.sh ;;
  --reuse) [ "$#" -eq 1 ] || exit 1; set -- --no-deps ;;
  *) printf '%s\n' 'Usage: scripts/v3-app-demo.sh [--reuse]' >&2; exit 1 ;;
esac
scripts/social-compose.sh run --rm "$@" -v "$PWD/scripts/v3_app_demo.py:/app/scripts/v3_app_demo.py:ro,z" v3-app-demo
mkdir -p social-artifacts
container_id=$(scripts/social-compose.sh run -d --no-deps --entrypoint /bin/true v3-app-demo)
trap 'docker rm -f "$container_id" >/dev/null 2>&1 || true' EXIT
docker cp "${container_id}:/artifacts/." social-artifacts/
printf '%s\n' 'v3 artifacts: social-artifacts/v3-app-*/. Identity/camera fixtures are SYNTHETIC; relay, OTS and Core are real.'
