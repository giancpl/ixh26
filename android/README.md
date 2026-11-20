# PhotoAnchor Android

The app captures a full-resolution JPEG through CameraX, stores it privately,
and can upload the exact file to the configured photo server. The camera screen
also exports an unsigned diagnostic ZIP with the original and a capture report.
See [capture feasibility](../docs/capture-feasibility.md) for independent checks.

## Build

Use JDK 17 and an Android SDK with Platform 35, Build Tools 35.0.0 and Platform
Tools. Install the SDK components and accept the Android SDK license with the
SDK Manager. Set `JAVA_HOME` and `ANDROID_HOME` for your environment, or set
`sdk.dir` in an untracked `local.properties`. Do not commit local SDK paths.

The repository includes Gradle Wrapper 8.11.1 with its distribution SHA-256
pinned in `gradle/wrapper/gradle-wrapper.properties`. No system Gradle is needed.
From this directory:

```sh
bash gradlew --no-daemon assembleDebug testDebugUnitTest
```

On Windows, use `gradlew.bat assembleDebug testDebugUnitTest`. First builds need
network access to download Gradle, plugins and Android dependencies.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. JVM test results
are in `app/build/reports/tests/testDebugUnitTest/`. The Gradle unit-test task
includes the original-byte export checks in `tests/CaptureEvidenceTest.java`.
Build and JVM test success do not establish camera credentials or hardware
attestation: those require a supported handset and independent trust policy.

To configure the default photo server when building:

```sh
bash gradlew assembleDebug -PSERVER_URL=http://192.168.1.20:8787
```

The server can also be configured in the app. The current app does not generate
attested installation keys or issue signed FingerprinTO receipts.

## Receipt byte construction

`ReceiptV1.java` implements deterministic CBOR export and the v1 fingerprint
tree. It consumes bounded streams for the original and detached credential,
keeps a private snapshot of metadata bytes, and produces T/R plus exact receipt
bytes and C from a supplied signature. It neither signs nor validates origin,
enrollment, SPKI/certificate structure or signature validity. Its size/type
checks match the Python construction layer, not the full verifier.

Given immutable original evidence, call `roots(assetStream, credentialStream)`
to obtain R. The proposed signing stage must use `SHA256withECDSA` on those raw
32 bytes. Preserve the returned DER signature exactly for `receipt(signature)`
and `ReceiptV1.commitment(R, signature)`. Timestamp C as a 32-byte file.
Origin acceptance and attested enrollment must precede production issuance.

From the repository root, run the cross-language tests with JDK 17+ and Python
3.11+ (the Android SDK is not required):

```sh
bash android/tests/run-receipt-vectors.sh
```

These tests compare all intermediates and receipt bytes from the four manual
fixtures, check their signatures through Java JCA, and add eight signed-integer
and UTF-8 boundary cases generated independently by the Python constructor.
The core is not connected to the CameraX capture UI yet.

## Installation key module

`InstallationKeys.kt` provisions one stable P-256 `AndroidKeyStore` alias with
an externally issued 32-byte enrollment challenge. `provision` refuses existing
aliases or residual enrollment records and never silently replaces a lost key.
StrongBox can be required explicitly; failure does not fall back to another
security level. The verifier still has to establish hardware backing from the
certificate chain; successful key generation is not an enrollment decision.

`load` returns defensive copies of SPKI, ID_I, the recorded enrollment challenge
and DER certificate chain. SPKI/challenge are persisted through AtomicFile in
`noBackupFilesDir`, with process and file locks. An incomplete record, missing
alias, or mismatch with the live certificate stops signing. Interrupted
provisioning can leave an alias without a record: the module refuses automatic
recovery/rotation to avoid changing identity. Explicit recovery and enrollment
policy are still required for that state.

`signRoot(expectedInstallationId, R)` is the low-level Keystore signing primitive.
Use it only after independent origin/enrollment acceptance. It verifies ID_I
against the live key, signs the 32 raw bytes with SHA256withECDSA, validates
strict DER/scalar bounds and checks the returned signature under the exact
P-256 public key. Preserve the returned DER bytes; retrying a network operation
must reuse the saved receipt/signature. This API does not itself accept a
camera credential or remotely enroll a key and exposes no `verified=true` flag.

```kotlin
// Run on an IO dispatcher. Challenge comes from the enrollment verifier.
val keys = InstallationKeys(context)
val evidence = keys.provision(externalChallenge, requireStrongBox = false)
// Send evidence.publicKey, enrollmentChallenge and certificateChain to the
// independent verifier; retain its challenge issuance and enrollment record.
// After trust checks, reconstruct R through ReceiptV1 using immutable inputs.
val sigma = keys.signRoot(evidence.installationId, rootR)
// Persist exact receipt bytes before anchor submission; never re-sign on retry.
```

`InstallationSignature.java` is independently usable and testable on the JVM.
It validates the exact v1 SPKI and P-256 point, checks strict DER and scalar
ranges, and signs/verifies with JCA. Run its standalone tests from repo root:

```sh
bash android/tests/run-installation-signatures.sh
```

Real JCA signatures, malformed DER, altered roots, wrong keys, invalid points
and incorrect root lengths were tested. A fresh Java signature also verified
through the Python verifier. Keystore provisioning, AtomicFile recovery and
hardware behavior have not been exercised on Android. No app build was run.

## Application validation status

The Gradle Wrapper was generated successfully using the verified 8.11.1
distribution. The standalone Java export tests and Java/Python interoperability
test passed previously. The app build and Android Gradle unit-test task have
not completed: SDK Platform/Build Tools installation was stopped at the license
prompt, and the user explicitly chose configuration only. No SDK license was
accepted. No handset or emulator test has been performed.
