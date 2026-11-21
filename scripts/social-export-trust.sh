#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p social-artifacts
scripts/social-compose.sh cp evidence-init:/evidence-config/authority.der social-artifacts/authority.der
printf '%s\n' 'Public authority SPKI: social-artifacts/authority.der' \
  'Build for this local operator with:' \
  'PHOTOANCHOR_AUTHORITY_SPKI_FILE="$PWD/social-artifacts/authority.der" ./scripts/social-build-android.sh'
