# Pietro native Android integration review

Source: `feature-mobileApp`, pinned to
`d6c2213bb2cf475a86782fc6f90a4adc9dbdff7d` (Pietro Valese).
Its two commits, `51f3556` and `d6c2213`, branch from `ba01942`.
Review/integration branch: `integration/pietro-mobile-review`, based on
`main` at `be33627`. The source branch is neither rewritten nor deleted.

## Scope and quality

The remaining diff changes only native `MainActivity.kt`: 1,778 insertions and
113 deletions, bringing it to 1,903 lines. This is distinct from the historical
WebView work already reviewed in PR #6. It adds a native masonry feed,
search, local bookmarks, created/saved profile grids, persistent theme, a
camera screen and a photo detail dialog. Stable event IDs key the lazy grids;
bookmark preferences contain only public IDs. The existing native SDK,
Keystore identity, immutable JPEG processing, signed outbox, backend API,
settlement and dependency versions are retained.

The visual design is reusable, but the source is not ready to merge unchanged.
The central problem is what the UI promises: cryptographic checks still happen
in the unchanged repository, but several labels and removed fields conceal
what was checked or imply stronger evidence than the protocol provides.

## Findings and integration treatment

| Severity | Finding in the pinned source | Integration treatment |
|---|---|---|
| High | Publish preview calls a photo “Bitcoin Anchored” before signing or submission (`MainActivity.kt:1426`). | Show “Not submitted”; explain signing, pending OTS and explicit regtest mining. |
| High | Detail fallback says “Verified Pin” for checking, errors and unavailable results; confirmation says “Anchored on Bitcoin” without identifying regtest (`1685–1690`). | Give each timestamp condition its own presentation; confirmation explicitly identifies the local node and regtest. Unknown/unavailable never become success. |
| High | “Authentic Capture” actually displays only JPEG hash integrity (`1811–1815`); signature/ID, publication, signer and event ID are removed. | Restore separate ID/signature, original-byte integrity, local-node timestamp and publication results, plus selectable public identifiers and evidence limits. |
| Medium | Every feed card has a Bitcoin “Pinned” badge although no timestamp is fetched there (`997–1023`). | Label only the locally checked event signature. Timestamp is checked separately in the detail dialog. |
| Medium | State messages and queued-event counts disappear (`PhotoAnchorApp`), hiding failures, offline retries and export outcomes. | Restore visible service/error messages, immutable-queue count and a permanent local-regtest indicator. |
| Medium | Publish changes tab immediately, even if upload/signing fails (`750–755`); the unsaved caption is then lost. | Stay on the draft during work and failures. Preserve the draft and caption on failure; retain the original capture-tab flow after successful durable submission. |
| Medium | A single 1,903-line activity mixes theme, icons, navigation, feed, capture and proof presentation. | Split by responsibility, with shared internal design primitives and a tested status presenter. Preserve the source commits as ancestors. |
| Medium | Icon-only camera, bookmark, theme and avatar controls lack accessible names/roles; narrow filter rows can overflow. | Add accessibility semantics and horizontal scrolling for filters. Device layout/accessibility acceptance remains a separate gate. |
| Low | Display names and posting dates lose their declared-metadata context; profile no longer exposes the public signing key or key-storage limitations. | Restore labels, selectable public key, local bookmark semantics and the no-recovery notice. |

No raw private-key storage, new external endpoint, signature implementation,
photo-byte mutation or backend/protocol changes were found in these two commits.
Names continue to be declarations, not verified civil identities. Bookmarks are
local preferences, not Nostr likes. Regtest confirmation is evidence from the
configured development chain, not mainnet anchoring or capture authenticity.

## Validation

Executed 2026-11-21, JDK 17 / SDK 35 / pinned Gradle 8.11.1 builder, with
`docker run --rm --network none`, the existing dependency cache and tasks
`./gradlew --offline --no-daemon assembleDebug testDebugUnitTest lintDebug`:

| Version | APK | JVM tests | Lint |
|---|---|---|---|
| Pinned source before corrections | Passed (4 min 43 s total) | 11 passed | 0 errors, 9 warnings |
| Corrected integration | Passed (3 min 54 s total) | 18 passed, no skips/failures/errors | 0 errors, 9 warnings |

The seven added JVM cases cover checking/missing, unavailable/unsubmitted,
pending/failure, explicit regtest confirmation, unknown tokens, independent
check outcomes and device locale. The existing eleven tests exercise the real
SDK and frozen Python/Kotlin fixtures. No new network mocks are used.
Warnings concern existing launcher resources, preference KTX suggestions and
an animated-offset performance suggestion; they are not hidden or suppressed.
The image/cache were already installed, so this is an offline build check,
not a claim of a fresh dependency download. Logs, lint reports, XML test
results and the APK are retained in `social-artifacts/pietro-review/`.
The integrated debug APK SHA-256 is `63a7561fa8ed7087be7e08a47529300dade9cec8ab48bbb411e7c9d0883353d8`.
The final diff outside Android and review/consolidation documentation is empty.
Hosted CI is an additional PR gate, recorded on GitHub. Compilation and JVM
checks do not establish camera, layout, TalkBack or two-phone acceptance.
Physical-phone acceptance remains deferred at the user's request.

### Remaining limits

The product still presents the backend's freshly checked Bitcoin report; the
phone independently checks event signatures, original JPEG hash and binding to
the expected OTS payload. It does not independently validate Bitcoin headers.
That trust boundary is part of the accepted MVP, not introduced by this UI.

Bookmarks are IDs only, and are shown only while their posts are present in the
cached/fetched feed (the repository bounds the feed to 50 photos). They do not
provide a permanent photo archive. Search is local to that feed. The BitPin
heading is cosmetic: the package ID, installed-app label and key-storage paths
remain PhotoAnchor, preserving existing identities and app data.

The presentation adapter currently consumes the repository's human-readable
status messages. Exact-token parsing fails neutral on unknown messages; a future
structured result type should replace this coupling when the repository API
changes. Large-font/small-screen layouts, touch-target sizing, TalkBack,
rotation during camera saving and the two-phone path still need actual-device
acceptance. No hardware attestation or capture-time guarantee is added.
