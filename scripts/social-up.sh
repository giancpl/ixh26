#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
scripts/social-compose.sh build bitcoin bootstrap calendar api worker
scripts/social-compose.sh up -d --wait --wait-timeout 120 bitcoin bootstrap calendar relay api worker
printf '%s\n' "API http://127.0.0.1:${PHOTOANCHOR_API_PORT:-8080} • relay ws://127.0.0.1:${PHOTOANCHOR_RELAY_PORT:-7777} • regtest only"
