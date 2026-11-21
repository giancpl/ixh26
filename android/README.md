# PhotoAnchor Android debug demo

Build from the repository root with `./scripts/social-build-android.sh`, then
install and set up USB reverse with `./scripts/social-phones.sh`. See
`docs/social/README.md` for the complete Docker and two-phone procedure.

The supported toolchain is JDK 17, Gradle wrapper 8.11.1, AGP 8.9.1, Kotlin
2.0.21, Android SDK/build-tools 35 and minSdk 29. The wrapper distribution has a
pinned SHA-256. `app/gradle.lockfile` records the resolved debug, JVM test and
lint dependencies. To intentionally refresh it, run the normal build tasks
with `--write-locks` and review the resulting changes.

Nostr signatures and event IDs use `org.nostrdevkit:nostr-sdk:0.45.1` and its
official native libraries. JNA is declared explicitly because the SDK exposes
its types. Coroutine core is constrained to 1.9.0 to retain compatibility with
the required Kotlin 2.0.21 compiler; the SDK's default transitive 1.10.1 selects
newer Kotlin metadata. JVM tests load the official desktop native binding,
`nostr-sdk-jvm:0.45.1`, and exercise the same vectors as Python. These tests do
not exercise the camera or Android Keystore on an actual phone.

The debug variant only connects to `http://127.0.0.1:8080` and
`ws://127.0.0.1:7777`. Cleartext is allowed only for loopback in its debug
network policy. The release variant has no configured services and does not
enable cleartext. No public relay is configured.

The UI has Profile, Camera and Feed tabs. Profile keys are generated locally
and encrypted with AES-GCM under Android Keystore in the no-backup directory.
Names remain author declarations. A signed event is saved in the persistent
outbox before submission; retries retain that event. The ViewModel retries and
refreshes every five seconds while alive, and resumes from disk after restart.
Captured JPEGs have GPS and identifying EXIF fields removed before hashing;
their bytes are preserved after that finalization. Previews are resized only
in memory. Hashing, image processing and network calls run off the UI thread.

Verify photo checks ID/signature and downloaded JPEG hash locally, and requests
a fresh timestamp report from the local API. Confirmation is explicitly
labelled as local-node verification on regtest. Export proof bundle writes a
ZIP using Android's document picker; Check a JPEG copy independently compares
selected bytes with the signed hash. Neither operation exports a private key.

Actual two-phone acceptance is deferred at the user's request. Record it in
`docs/social/CHECKLIST.md`; successful builds and native JVM tests are separate
evidence.

## Native UI integration

The native BitPin interface includes a masonry feed, local search, saved-post
bookmarks, profile grids and a persistent light/dark theme. The installed app
remains PhotoAnchor with the same application ID and encrypted identity paths.
Saved posts are local bookmarks, not likes or a permanent media archive; search
and bookmark views operate on the bounded cached/fetched feed.

The camera draft says **Not submitted** until publication. A failed operation
keeps the draft and caption visible; after a successful submission the capture
tab becomes the camera again. The status banner exposes operation failures and
the persistent signed-event queue. A signed-post badge in the feed indicates
only event-signature validation. The detail screen shows signature, original
JPEG integrity, local-node regtest timestamp and relay publication separately.
Unknown or unavailable timestamp results never display a confirmation badge.

Review findings, corrective changes and validation are tracked in
[`docs/reviews/PIETRO_ANDROID.md`](../docs/reviews/PIETRO_ANDROID.md).

## Experimental cryptographic primitives

The portable receipt, enrollment-possession, hybrid and PRNU numeric classes
from `leomain@f3bd1ef` build alongside the native Nostr application. They have
independent JVM/Python vectors and hardware-key storage adapters. They do not
yet connect capture to v3 issuance or provide sensor-signed photographs.
`CaptureReport`/`CaptureEvidence` are unsigned diagnostic export utilities;
the former WebView callback has not replaced the native Compose capture flow.
See [the integration report](../docs/reviews/LEOMAIN_PR9.md) for executed checks
and the remaining Pixel/GrapheneOS and real-camera acceptance gates.
