#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Explicit operator action. No service mines continuously.
scripts/social-compose.sh run --rm --no-deps --entrypoint python api /app/scripts/social_mine.py "$@"
