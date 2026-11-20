#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
exec docker compose -p "${PHOTOANCHOR_PROJECT:-photoanchor}" -f compose.regtest.yml -f compose.social.yml "$@"
