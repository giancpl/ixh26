# Real capture feasibility check

The Android camera screen now offers **Esporta originale e rapporto** after a
successful capture in the current session. Choose a destination using Android's
document picker and transfer the ZIP to an independent host without editing it.
The original remains in app-private storage; no bitmap/EXIF rewrite is performed
for the diagnostic export. Hashing and export run on the IO dispatcher.

The ZIP contains exactly:

- `original.jpg`: the file returned by the CameraX save callback;
- `capture-report.json`: declared device/app information, camera route and lens
  selection, completion wall-clock time, size and SHA-256.

This is a diagnostic capture export, distinct from the signed evidence bundle
handled by `python -m fingerprinto`. It contains no installation signature,
attestation, ledger proof or claim of accepted origin. The report is unsigned.
It records `origin: indeterminate` and `reason: credential_not_evaluated`.
It does not collect a detached credential. Do not use it as a valid receipt.

On the independent host:

1. Preserve the ZIP and original inputs. Extract these two known members.
2. Calculate SHA-256 of `original.jpg` and compare it to the report. Also compare
   byte size. A mismatch stops the experiment.
3. Record actual handset, OS, capture route and mode. Add the camera provider
   identity/version and hardware identifiers needed for the experiment from the
   device; the report does not attest them or query the CameraX provider version.
4. Run an independently configured C2PA/vendor validator on the original JPEG.
   Record validator version, credential presence/placement, active manifest,
   signature/binding results, issuer, exact signed capture assertion and trust
   policy identifier. An image parser or a string search is not this validation.
5. If all origin-policy checks pass, archive the positive validation report. Then
   modify an authenticated range while retaining a parseable container and check
   that binding validation fails. Use a native-camera output as a diagnostic
   control if useful; it cannot establish support for this app's CameraX route.

An absent credential leaves the app route unsupported for verified receipts.
Missing policy or validator yields an indeterminate result. None of these can
be turned into accepted origin by hashing or anchoring the file.

The Java export writer checks the original's size/hash again while copying.
Changed bytes or output errors fail the export; the UI attempts to delete the
partial document and asks the user to discard any remaining incomplete file.
Do not change/export the source concurrently using external tools. The chooser
exports the selected capture snapshot even if another photo is captured meanwhile.
The button covers captures from the current session; restart recovery/history
selection remains future application work. A sidecar report is saved beside
each private original for future recovery.

Validation completed in this checkout: the Java writer was compiled with
Eclipse ECJ 3.40.0 targeting Java 17 and executed on OpenJDK 21. Checks cover
multi-chunk byte preservation, valid ZIP entries, unchanged source, altered
content, size mismatch and destination failure. The Android SDK/Gradle toolchain
is not available here, so the Compose integration has not been built, installed
or exercised on a handset. A qualifying real capture remains outstanding.
