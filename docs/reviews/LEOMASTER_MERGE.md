# leomaster / main reconciliation

Date: 21 November 2026. The author explicitly requested merging the branch
through its PR, superseding the earlier instruction to leave it unmerged.
OpenAI Codex performed this reconciliation and the document checks below.

Inputs: `leomaster` at `d82043f`, current main at `18ae342` (final app), including
the previously merged Pixel acceptance report. PR #18 targets main; PR #16
targets leomain and overlaps this work.

## Resolution

- Preserve main's current PhoTO root README and its app architecture. Keep the
  technical guide under docs/crypto and add the beacon/presentation links there.
- Preserve main's protocol/server/social extras; add only the beacon extra.
- Keep both independent documentation addenda, explicitly distinguishing the
  historical source audit from current native integration/recorded Pixel tests.
- Keep the latest author-reviewed deck and media, rather than the older pitch
  draft from main. At the author's further request, remove Sensor A / Phone B
  and the five-claim table: show the actual capture/sign/publish path and Nostr
  event signature, published-image hash and independent OTS checks instead.
  Explicitly separate optional P-256 device evidence from the Nostr profile key.
  Full receipt equations move into the byte-contract backup; main mathematics
  is reduced. Pitch remains eight main screens, nine backups and 237 spoken words.

## Checks actually performed

`python -B -m pytest tests/fingerprinto -q` passed **97 tests and 134 subtests**
on the merged runtime tree. The initial unittest invocation could not import
pytest-based recovery tests; installing the repository-constrained pytest 9.2.0
in the local virtualenv enabled the complete successful run. This was an
environment/test-runner issue, not a suppressed passing result.

Byte/diff inspection against main verifies Android, web, social, server,
settlement, contracts and upp_protocol runtime files are unchanged. Within
fingerprinto, the only runtime difference is the new beacon.py. All main
dependency extras are retained. No Android build, SDK licence acceptance,
Docker/device rerun or public-network anchoring was performed for this merge.
The real Pixel results are attributed to their existing report, not repeated here.

The changed slides were compiled and rendered. The full byte-contract backup
was checked after moving formulas there; final logs have no overfull/underfull
boxes. Current PDF/source archives are rebuilt and retain the video assets and
links. The two new slides were checked against protocol.py, the contract guide
and native PhotoProtocol.kt. No timed rehearsal or GUI video launch is claimed.

The merge preserves the existing main application and adds the branch's code,
research documentation and reviewed presentation. Other branches are not targets
of this operation. The local checkout remains on leomaster.
