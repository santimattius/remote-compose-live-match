# Remote Compose Live Match

An AndroidX Remote Compose example that authors sports-match UI documents on the JVM, signs them
with Ed25519, validates them at the Android trust boundary, and renders them with
`RemoteComposePlayer`.

The project models three match phases:

- **Scheduled** — upcoming Orcas vs Tigers match with a reminder action.
- **Live** — 2–1 in the second half with match and highlight actions.
- **Finished** — final 2–1 result with a summary action.

The launcher app renders the packaged Scheduled document. Live and Finished documents are
available through a debug-only diagnostic activity.

## Quick start

### Requirements

| Tool | Required version |
| --- | --- |
| JDK | 17 |
| Android SDK | Platform 35 |
| Android runtime | API 26 or newer |
| Gradle | Use the included wrapper (`8.10.2`) |
| adb | Required to install and launch on a device or emulator |

The project currently uses Android Gradle Plugin 8.8.2, Kotlin 2.1.20, and AndroidX Remote Compose
`1.0.0-alpha19`.

### Build and test

```bash
./gradlew test
./gradlew :app-android:assembleDebug :app-android:assembleDebugAndroidTest
./gradlew \
  :creator-jvm:checkDiagnosticFixture \
  :creator-jvm:checkScheduledFixture \
  :creator-jvm:checkLiveFixture \
  :creator-jvm:checkFinishedFixture
```

Fixture checks validate the committed artifacts without loading a private signing key.

### Install and run

Start an API 26+ Android device or emulator, then run:

```bash
./gradlew :app-android:installDebug
adb shell am start -n example.livematch.android/.MainActivity
```

The launcher opens the Scheduled match document after decoding and validating its signed envelope.

## Architecture

```text
creator-jvm ─────▶ contract
     │                ▲
     │ signed fixtures│ validation models and policies
     ▼                │
  fixtures ─────▶ app-android ─────▶ RemoteComposePlayer
```

| Module | Responsibility |
| --- | --- |
| `contract` | Dependency-free JVM models, action allowlist, envelope codec, canonical signing input, public key, and validation policy. |
| `creator-jvm` | Deterministically creates raw Remote Compose documents, signs envelopes, and writes fixture manifests. |
| `app-android` | Packages fixtures, validates signed envelopes, renders accepted documents, and dispatches allowlisted actions. |

`app-android` depends on `contract`, not on `creator-jvm`. Committed fixtures are the boundary
between document production and the Android host.

## Signed document flow

1. A creator builds raw Remote Compose document bytes.
2. `EnvelopeSigner` signs canonical metadata and the document hash with Ed25519.
3. `SignedFixturePublisher` writes the raw `.rc`, signed `.rce`, and manifest artifacts.
4. Android reads the `.rce` asset through `EnvelopeDocumentLoader`.
5. `RemoteUiEnvelopeCodec` rejects invalid magic, versions, lengths, UTF-8, truncation, or trailing
   bytes.
6. `EnvelopeValidator` checks schema, algorithm, host compatibility, timestamps, expiry, SHA-256,
   and signature.
7. Only validated document bytes reach `RemoteComposePlayer`.
8. Action listeners are registered after the document is loaded, and every emitted action passes
   through the match-phase allowlist.

Android verification uses an isolated Tink Ed25519 implementation because platform JCA provider
behavior is not reliable for this public-key import path across the supported Android range. JVM
signing and verification continue to use standard JCA APIs.

## Fixtures

Committed fixture sources live in `fixtures/`.

| Artifact | Purpose |
| --- | --- |
| `scheduled-match.rce` | Production Scheduled envelope packaged in the main APK. |
| `live-match.rce` | Debug Live envelope. |
| `finished-match.rce` | Debug Finished envelope. |
| `*.rc` | Raw document probes and creator outputs. |
| `*.manifest` | Metadata and hashes used to verify deterministic publication. |

### Verify fixtures

```bash
./gradlew :creator-jvm:checkDiagnosticFixture
./gradlew :creator-jvm:checkScheduledFixture
./gradlew :creator-jvm:checkLiveFixture
./gradlew :creator-jvm:checkFinishedFixture
```

### Regenerate fixtures

The diagnostic fixture is unsigned:

```bash
./gradlew :creator-jvm:generateDiagnosticFixture
```

Scheduled, Live, and Finished generation requires `LIVE_MATCH_SIGNING_PRIVATE_KEY` containing a
base64-encoded PKCS#8 DER Ed25519 private key:

```bash
export LIVE_MATCH_SIGNING_PRIVATE_KEY='<base64-pkcs8-der>'

./gradlew :creator-jvm:generateScheduledFixture
./gradlew :creator-jvm:generateLiveFixture
./gradlew :creator-jvm:generateFinishedFixture
```

Never commit the private key. Local PEM files under `keys/` are ignored. See
[`keys/README.md`](keys/README.md) for local key generation, conversion, and rotation guidance.
Production systems should use managed KMS or HSM-backed signing instead of environment variables.

## Debug scenarios

`DiagnosticPlaybackActivity` exists only in debug builds. It supports these modes:

- `pre_attach` — load before the player is attached; use this for interactive documents.
- `post_attach` — diagnostic path for studying late document replacement.

Supported debug fixtures include:

- `paint-probe.rc`
- `alpha18-api6-control.rc`
- `live-match.rce`
- `finished-match.rce`

Example:

```bash
adb shell am start \
  -n example.livematch.android/.DiagnosticPlaybackActivity \
  --es mode pre_attach \
  --es fixture live-match.rce
```

Raw `.rc` probes intentionally bypass signed-envelope validation. Live and Finished `.rce` files
use the same validation boundary as the launcher app.

## CI

Two GitHub Actions workflows protect pull requests:

- **PR Governance** runs from trusted default-branch code and validates approved issue linkage,
  labels, template completeness, branch and commit conventions, and review budget.
- **PR Build** uses JDK 17 to run unit tests, build the debug APK and Android test APK, and verify all
  committed fixtures.

Neither workflow requires the private signing key.

## Troubleshooting

### The document appears blank

Check ActionBar and system-bar occlusion before changing document encoding or Remote Compose
versions. A correctly measured player can still be hidden by opaque host chrome.

### Click areas exist but taps do nothing

Load the document first, then register `addIdActionListener`. Calling `setDocument` replaces the
internal document and discards listeners registered beforehand.

### Click areas do not appear after replacing a document

Load interactive documents before the player is attached. Late replacement can update the document
model without materializing new `ClickAreaView` children.

### Content renders too small

Procedural coordinates are physical pixels. `RemoteComposePlayer` does not scale absolute document
coordinates to fit the device.

### Text clips near the left edge

Use `panX=-1f` for flush-left `drawTextAnchored` content. `panX=0f` centers text on the supplied
x-coordinate.

### The wrong emulator produced the result

Do not trust the adb serial alone. Verify the AVD name, SDK level, Android release, and build
fingerprint before accepting device results.

## Durable learnings

See [`docs/alpha-learnings.md`](docs/alpha-learnings.md) for the maintained list of Remote Compose
integration lessons and diagnostic constraints.

Internal task records, generated evidence, and screenshots are intentionally excluded from the
repository. The durable learning log is the only maintained document under `docs/`.
