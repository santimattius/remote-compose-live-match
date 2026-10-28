# Remote Compose Live Match

An AndroidX Remote Compose companion that demonstrates a signed sports-match UI document rendered
by `RemoteComposePlayer`.

The supported architecture keeps match data and its signed envelope on the backend, then has the
Android host validate that input and convert accepted data into `@RemoteComposable` UI through
public APIs. The host retains control of navigation, permissions, identity, and every business
effect. `creator-jvm` only encodes and signs match data; it never builds Remote Compose documents.

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
| Android runtime | API 29 or newer |
| Gradle | Use the included wrapper (`8.10.2`) |
| adb | Required to install and launch on a device or emulator |

The project uses Android Gradle Plugin 8.8.2, Kotlin 2.1.20, and AndroidX Remote Compose
`1.0.0-alpha22`. All Remote Compose artifacts move together to this version so the supported
Android public-capture path can use the required configuration, time, and animation APIs. Keep the
fixture and host compatibility checks with that upgrade; do not mix Remote Compose artifact
versions.

### Build and test

```bash
./gradlew test
./gradlew :app-android:assembleDebug :app-android:assembleDebugAndroidTest
./gradlew \
  :creator-jvm:checkScheduledFixture \
  :creator-jvm:checkLiveFixture \
  :creator-jvm:checkFinishedFixture
```

Fixture checks validate the committed artifacts without loading a private signing key.

### Install and run

Start an API 29+ Android device or emulator, then run:

```bash
./gradlew :app-android:installDebug
adb shell am start -n example.livematch.android/.MainActivity
```

The launcher opens the Scheduled match document after decoding and validating its signed envelope.

## Supported architecture

```text
backend match data ──▶ signed envelope ──▶ Android host validation
                                                │
                                                ▼
                              public @RemoteComposable capture
                                                │
                                                ▼
                                    RemoteComposePlayer
                                                │
                                                ▼
                              allowlisted host-owned actions
```

| Boundary | Responsibility |
| --- | --- |
| Backend | Creates match data and signs its bounded, versioned envelope. It does not control host effects. |
| `contract` | Defines dependency-free models, action allowlist, envelope codec, canonical signing input, public key, and validation policy. |
| `app-android` | Validates the envelope before use, maps accepted data to public `@RemoteComposable` UI, hosts the player, and dispatches only allowlisted actions. |
| `RemoteComposePlayer` | Replays the captured document; it does not authorize navigation, authentication, permissions, or other business effects. |

The committed fixtures carry signed match data, not Remote Compose documents. Android captures the
document on-device after validation, using the public alpha22 capture API, so the minimum SDK is 29.

## Current fixture verification flow

1. A creator encodes match data with `MatchPresentationCodec`.
2. `EnvelopeSigner` signs canonical metadata and the payload hash with Ed25519.
3. `SignedFixturePublisher` writes the raw `.match` payload, signed `.rce`, and manifest artifacts.
4. Android reads the `.rce` asset through `LastKnownGoodDocumentRepository`, which delegates to
   `EnvelopeMatchLoader`.
5. `RemoteUiEnvelopeCodec` rejects invalid magic, versions, lengths, UTF-8, truncation, or trailing
   bytes.
6. `EnvelopeValidator` checks schema, algorithm, host compatibility, timestamps, expiry, SHA-256,
   and signature.
7. Only validated match data is compiled by `AndroidMatchDocumentCompiler` and reaches
   `RemoteComposePlayer`. A candidate that is rejected keeps the last-known-good match, and with
   none the host shows a local message instead of a blank player. The message offers a Retry button
   that resolves the candidate again by the same path as the first launch.
8. Action listeners are registered after the document is loaded, and every emitted action passes
   through the match-phase allowlist.

### Revisions and replays

A validly signed envelope can still be an old one. The cache therefore keeps the `matchId`, `revision`, creation and
expiry times of the match it stores, and refuses to replace it with an older document of the same match:

- A higher `revision` replaces the cached match. A different `matchId` replaces it too.
- The same `revision` is accepted only when the content is identical (a harmless re-delivery); different content under
  the same revision is a conflict and is refused.
- A lower `revision` is a rollback and is refused. The match that was already cached keeps playing, and the refusal is
  reported through the resolver's failure callback.

So a backend must increase `revision` for every new document of the same match. These fields are covered by the
envelope signature, so they cannot be forged. A cache file written before this rule existed holds no metadata, cannot
be ordered, and is ignored.

### Stale cached matches

What the cache shows when it is offline must still be true. A match read back from the cache is shown as current only
while it is fresh:

- Every match stops being shown once its envelope expires. The cache used to skip this check.
- A live match is shown for at most 15 minutes after the envelope was created (\`SnapshotFreshness.LIVE_MAX_AGE_SECONDS\`),
  because its score and clock stop being true while the app is offline.
- A scheduled match stops being shown once its kickoff has passed, because it would still say "UPCOMING".
- A finished match does not age before its envelope expires.

A match that is not shown this way produces the "Match information is out of date." message, reported through the
failure callback, instead of the old data. A fresh candidate is judged only by its envelope expiry, because the backend
vouches for it. The 15 minutes are a choice of this example, not a property of Remote Compose.

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
| `*.match` | Raw match-data payloads that each `.rce` envelope signs. |
| `diagnostic/*.rc` | Raw document probes for the debug diagnostic activity. |
| `*.manifest` | Metadata and hashes used to verify deterministic publication. |

### Verify fixtures

```bash
./gradlew :creator-jvm:checkScheduledFixture
./gradlew :creator-jvm:checkLiveFixture
./gradlew :creator-jvm:checkFinishedFixture
```

### Regenerate fixtures

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
use the same validation and on-device capture path as the launcher app.

## Measuring performance

`PerformanceMeasurementTest` reports the host-side cost of the signed match-data pipeline to
logcat under the `LiveMatchPerf` tag: envelope validation and decoding, on-device document
capture, and the time from launching the launcher activity until the match content is visible.
It runs on a connected device or emulator:

```bash
adb logcat -c
./gradlew :app-android:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=example.livematch.android.PerformanceMeasurementTest
adb logcat -d -s LiveMatchPerf
```

Cold is the first run in the process; median and best come from 20 warm runs. The test only fails
on absurd values (over 10 seconds), so compare numbers between runs of the same device. One
Pixel 9 API 35 emulator run measured validation about 3 ms (cold 104 ms), capture about 4 ms
(cold 67 ms), and first visible content about 584 ms. Treat those as an example, not a budget.

## Comparing approaches

The article asks for the same card built with local Compose, SDUI JSON and Remote Compose, with a
WebView as a reference. The debug build contains all four (`ComparableCards.kt`), each in its own
process and showing the same content: the LIVE label pulsing, both scores, a ticking match clock, a
local "Show local match details" toggle and two actions.

```bash
./gradlew :app-android:installDebug
python3 scripts/measure_footprint.py 3 emulator-5554
```

One run of that script on a Pixel 9 API 35 emulator (debug build, median of 3, range in brackets):

| Approach | CPU ms per second | Frames per second | PSS MB | Launch ms |
| --- | --- | --- | --- | --- |
| Compose local | 97 [92-129] | 19 [18-27] | 68.5 | 1460 |
| SDUI JSON | 127 [87-127] | 24 [17-26] | 68.3 | 1505 |
| WebView | 134 [92-134] | 27 [19-55] | 101.3 | 1222 |
| Remote Compose | 48 [38-50] | 9.5 [7-10] | 73.6 | 1544 [513-1664] |

What travels and what is built:

| Artifact | Bytes | DEFLATE |
| --- | --- | --- |
| Signed envelope (match data) | 319 | 262 |
| Remote Compose document built on the device | 1539 | 732 |
| SDUI JSON tree | 405 | 216 |
| HTML, CSS and JavaScript | 759 | 487 |

How to read it:

- Remote Compose used about half the CPU of the others, **and drew about half the frames**. The
  pulse is a continuous animation, so fewer frames means fewer paints, not necessarily cheaper
  paints. This run does not say which. Compare it with the frame rate before quoting the CPU.
- The signed envelope is the smallest thing that travels because it carries data only. The card's
  structure is code in the app, so the built document is larger than the SDUI tree or the HTML.
  Sending the document itself instead would change that comparison.
- The WebView's renderer runs in a separate process that these numbers do not include, so its
  memory is understated.
- It is an emulator, a debug build and three runs. The launch time of the Remote card has one run
  at 513 ms that was not explained. Do not turn any of this into a claim about battery or about
  production devices.

## Verified environments

The instrumentation suite (25 tests) has been run on these emulators, with the results below. Anything not listed
was not tested.

| Environment | Result |
| --- | --- |
| `Pixel_9` AVD, Android 15 (API 35), arm64 | 25 of 25 pass, including the TalkBack test. |
| `Pixel_API29` AVD (Pixel 5 profile), Android 10 (API 29), arm64, `google_apis` image, software rendering | 24 pass and 1 is skipped: the TalkBack test, because that image does not include TalkBack. |

API 29 is the lowest level the app installs on (`minSdk` 29). Not tested: API 30 to 34, physical devices, TalkBack
on API 29, and keyboard or D-pad focus. API 26 to 28 are excluded by `minSdk`.

To run the suite on one device when several are connected, assemble both APKs and use `am instrument`.
`connectedDebugAndroidTest` runs on every connected device, which can change the settings of a device you are using
(font scale, window size and accessibility services are changed and restored by some tests).

```bash
./gradlew :app-android:assembleDebug :app-android:assembleDebugAndroidTest
adb -s <serial> install -r app-android/build/outputs/apk/debug/app-android-debug.apk
adb -s <serial> install -r app-android/build/outputs/apk/androidTest/debug/app-android-debug-androidTest.apk
adb -s <serial> shell am instrument -w -r   example.livematch.android.test/androidx.test.runner.AndroidJUnitRunner
```

## CI

Two GitHub Actions workflows protect pull requests:

- **PR Governance** runs from trusted default-branch code and validates approved issue linkage,
  labels, template completeness, branch and commit conventions, and review budget.
- **PR Build** uses JDK 17 to run unit tests, build the debug APK and Android test APK, and verify all
  committed fixtures.

Neither workflow requires the private signing key.

### Landing a chain of pull requests

Each pull request must stay at or below 400 changed lines, so a larger change lands as a chain
where every PR is based on the previous branch and links the same approved issue with `Closes #N`.

- Merge with merge commits, not squash. After a squash the next PR would diff against `main`
  again and recount the lines it inherits.
- GitHub closes the linked issue when the first PR merges to `main`, and governance requires the
  linked issue to be open. Reopen it before retargeting each following PR to `main`; it stays
  closed after the last one lands. Alternatively merge from the tip down, each PR into its parent
  branch, and land the first PR, the only one that targets `main`, last.
- Closing references are only computed once a PR targets `main`, so read the issue number from the
  PR body when scripting this.
- Each PR must build on its own. Check the `PR Build` command locally at every step of the chain.

## Troubleshooting

### The document appears blank

Check ActionBar and system-bar occlusion before changing document encoding or Remote Compose
versions. A correctly measured player can still be hidden by opaque host chrome.

### Click areas exist but taps do nothing

Load the document first, then register `addIdActionListener`. Calling `setDocument` replaces the
internal document and discards listeners registered beforehand.

### Taps never reach the host

Use the id-based `hostAction(id, metadata)` overload. `hostAction(name)` creates a named action
that `addIdActionListener` never receives. Documents captured by the public API do not
materialize `ClickAreaView` children; instrumentation tests tap the player's accessibility nodes
instead.

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
