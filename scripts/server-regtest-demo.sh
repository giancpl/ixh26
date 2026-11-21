#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
compose() {
    docker compose -p "${IXH26_REGTEST_PROJECT:-photoanchor-server-regtest}" -f compose.regtest.yml -f compose.server-regtest.yml "$@"
}
export IXH26_AUTO_CONFIRM_REGTEST=0
compose build bitcoin bootstrap calendar server
compose up -d --wait --wait-timeout 120 bitcoin bootstrap calendar server
compose run --rm --no-deps --entrypoint python server /app/scripts/server_regtest_demo.py --mode manual
restore_manual() {
    IXH26_AUTO_CONFIRM_REGTEST=0 compose up -d --no-deps --wait --wait-timeout 120 server
}
trap restore_manual EXIT
export IXH26_AUTO_CONFIRM_REGTEST=1
compose up -d --no-deps --wait --wait-timeout 120 server
compose run --rm --no-deps --entrypoint python server /app/scripts/server_regtest_demo.py --mode auto
mkdir -p social-artifacts/server-regtest
compose cp server:/artifacts/. social-artifacts/server-regtest/
printf '%s\n' 'Both regtest modes passed; automatic mining is being disabled again. Artifacts: social-artifacts/server-regtest/'
