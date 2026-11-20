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

## Validation status

The Gradle Wrapper was generated successfully using the verified 8.11.1
distribution. The standalone Java export tests and Java/Python interoperability
test passed previously. The app build and Android Gradle unit-test task have
not completed: SDK Platform/Build Tools installation was stopped at the license
prompt, and the user explicitly chose configuration only. No SDK license was
accepted. No handset or emulator test has been performed.
