#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
docker compose -f compose.regtest.yml build
docker compose -f compose.regtest.yml up -d --wait --wait-timeout 120 bitcoin bootstrap calendar
docker compose -f compose.regtest.yml run --rm demo
