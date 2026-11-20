#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -d web/dist ] || [ "${1:-}" = "--rebuild" ]; then
  echo "Building web frontend..."
  (cd web && npm run build)
fi

HOST="${HOST:-0.0.0.0}"
PORT="${PORT:-8787}"
echo "Starting BitPin · PhotoAnchor server on http://127.0.0.1:${PORT}"
exec python3 -m server
