# Work Unit 1 — alpha18 feasibility

**Verdict: NO-GO.** Alpha18 artifacts resolve and its JVM procedural writer and Android player compile, but the required Kotlin/JVM *Compose capture* path is unavailable through public APIs. No Work Unit 2 work was started.

## Environment

- Timestamp: 2026-09-13T21:06:49Z
- Host: Darwin 25.6.0 arm64
- JDK: Temurin 17.0.10
- Android SDK: `/Users/santiago/Library/Android/sdk` (platforms 34–37 installed)
- Pins: Gradle 8.10.2, AGP 8.8.2, Kotlin 2.1.20, Compose BOM 2025.04.01, Remote Compose 1.0.0-alpha18, compile/target SDK 35, min SDK 26.

## Public API evidence

The Android test directly resolves `androidx.compose.remote:remote-creation-compose:1.0.0-alpha18` (and its public `remote-creation-core` signature type). It assigns `::captureSingleRemoteDocument` to a function type whose first parameter is `android.content.Context`. This is a compile-time public-API proof: removing or changing that Context parameter makes the test source fail to compile. It does not invoke capture or reflect on app behavior.

`remote-creation-jvm` still supplies the procedural `RemoteComposeWriter` path, not a Context-free public Compose capture path. No substitute or internal API was used.

## Checks and outcomes

| Command | Outcome |
| --- | --- |
| `./gradlew :contract:test :creator-jvm:test --no-daemon` before implementations | RED: failed with unresolved `RemoteDocumentPayload` and `MatchDocumentCreator` |
| `./gradlew :contract:test :creator-jvm:test --no-daemon` | GREEN: success; transport truncation rejected and same input emitted identical non-empty bytes |
| `./gradlew test --no-daemon` (first host attempt) | Failed closed: player requires min SDK 26, not 23 |
| `./gradlew test --no-daemon` (second host attempt) | Failed closed: direct `remote-core` dependency was required for player supertypes |
| `./gradlew test --no-daemon` | GREEN: success; debug and release Android sources compiled; JVM tests passed |
| `./gradlew :creator-jvm:test --no-daemon` after triangulation | Success via Kotlin compiler fallback after a daemon cache-registration error; test passed |
| `./gradlew :app-android:testDebugUnitTest --no-daemon` (API probe RED) | Failed as expected: the partial probe had unresolved `creation` symbols before the artifact was declared. |
| `./gradlew :app-android:testDebugUnitTest --no-daemon` (API probe GREEN) | Success: the direct alpha18 artifact and exact Context-first function reference compiled and the test passed. |
| `./gradlew :creator-jvm:test :app-android:testDebugUnitTest --no-daemon` | Success. |
| `./gradlew test --no-daemon` | Success: all 53 tasks completed successfully. |

The host shell calls the public `RemoteComposePlayer.setDocument(ByteArray)` only through `RemoteComposePlayerAdapter`. `RemoteDocumentPayload.from` rejects an empty payload or a declared-size mismatch before that boundary. This proves transport truncation detection, not validation of arbitrary malformed Remote Compose bytes.

## TDD evidence

| Phase | Evidence |
| --- | --- |
| RED | `:app-android:testDebugUnitTest` failed with unresolved capture symbols before `remote-creation-compose` was declared. |
| GREEN | Added direct test dependencies and compiled the Context-first `captureSingleRemoteDocument` function reference; the Android unit test passed. |
| TRIANGULATE | `:creator-jvm:test :app-android:testDebugUnitTest` and then the complete `./gradlew test --no-daemon` suite passed. |
| REFACTOR | Kept the probe as a direct function-reference type check; no internal API, copied source, or app-behavior reflection was introduced. |

## GO/NO-GO and follow-up

**NO-GO for the alpha18 baseline.** The reproducible probe verifies that the public `captureSingleRemoteDocument` path is Context-first, so it does not meet the Context-free JVM creator requirement. Do not implement Work Unit 2. Re-evaluate only if AndroidX publishes a public Context-free JVM Compose capture API, or a maintainer explicitly changes the creator requirement to the procedural writer path.

Rollback boundary: remove `contract/`, `creator-jvm/`, `app-android/`, root Gradle bootstrap, and these two documents; no existing tracked implementation is coupled to them.
