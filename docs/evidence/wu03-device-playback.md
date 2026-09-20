# Work Unit 3 — device playback gate

## Verdict

**NO-GO.** The exact WU2 Scheduled fixture is packaged, its APK asset hash matches, the focused test reaches a resumed activity with a non-zero-size `RemoteComposePlayer`, and fixture installation does not throw. But both required cold-launch screenshots are visually blank/indistinguishable: only the default activity title is visible. This fails the visual rendering gate.

**First failing boundary:** visibility of the installed Remote Compose document on `Pixel_9` / `emulator-5554`, Android 15 (API 35). No WU2 fixture/creator change was made to conceal that result.

## Scope and source identity

- Canonical source fixture: `fixtures/scheduled-match.rc`
- Required WU2 SHA-256: `57884b403add2d49825b89d71e6257fbc5a15cd66f51581890a8442b54741d51`
- Launch-baseline allowed-surface snapshot: `docs/evidence/wu03/launch-surface.sha256`
- Post-implementation source identity: `docs/evidence/wu03/post-implementation-sources.sha256`
- Generated-evidence identity: `docs/evidence/wu03/evidence-manifest.sha256`; later evidence-only compaction receipt: `docs/evidence/wu03/compaction-delta.txt`.

The Android module stages only `fixtures/scheduled-match.rc` into generated APK assets. It does not add a project or runtime dependency on `creator-jvm`. `MainActivity` reads that asset, constructs a non-empty `RemoteDocumentPayload`, and passes it through the existing `RemoteComposePlayerAdapter`.

## TDD cycle evidence

| Phase | Command | Outcome |
| --- | --- | --- |
| RED | `ANDROID_SERIAL=emulator-5554 ./gradlew :app-android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=example.livematch.android.ScheduledFixturePlaybackTest --no-daemon` | Expected runtime failure: `java.io.FileNotFoundException: scheduled-match.rc`. The test compiled and ran on the named emulator, proving the fixture was not yet packaged. |
| GREEN | `./gradlew :creator-jvm:checkScheduledFixture :app-android:assembleDebug --no-daemon` | Passed; creator fixture check and debug APK assembly succeeded. |
| GREEN | `ANDROID_SERIAL=emulator-5554 ./gradlew :app-android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=example.livematch.android.ScheduledFixturePlaybackTest --no-daemon` | Passed one focused device test; it verifies target-app asset SHA-256, resumed activity, non-zero player size, and non-throwing installation. |
| TRIANGULATE | `./gradlew :creator-jvm:checkScheduledFixture :app-android:assembleDebug --no-daemon` | Passed again after cleanup. `unzip -p app-android/build/outputs/apk/debug/app-android-debug.apk assets/scheduled-match.rc | shasum -a 256` produced the required hash; see `wu03/apk-asset-sha256.txt`. |
| TRIANGULATE | Two cold launches via `adb -s emulator-5554 shell am force-stop example.livematch.android` then `adb -s emulator-5554 shell am start -W -n example.livematch.android/.MainActivity` | Both returned `Status: ok` (193 ms and 180 ms); screenshots, layouts, and one process-scoped log are listed below. Both screenshots agree on blank rendering. |
| REFACTOR | N/A | No source refactor followed GREEN because the visual NO-GO is a result to report, not a reason to alter the fixture or force a display. |

## APK, device, and runtime evidence

### APK asset

`wu03/apk-asset-sha256.txt` records both the exact `unzip -p` command and:

```text
assets/scheduled-match.rc
57884b403add2d49825b89d71e6257fbc5a15cd66f51581890a8442b54741d51  -
```

### Device identity

`wu03/device-identity.txt`:

```text
serial=emulator-5554
avd=sdk_gphone64_arm64
manufacturer=Google
model=sdk_gphone64_arm64
android_release=15
api=35
fingerprint=google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug/dev-keys
```

The named AVD is `Pixel_9`; Gradle reported `Pixel_9(AVD) - 15` for both focused instrumented runs.

### Cold launch 1 and 2

- Install receipt: `wu03/install.txt`
- `am start -W` receipts: `wu03/cold-launch-1.txt`, `wu03/cold-launch-2.txt`
- Layout dumps: `wu03/layout-1.xml`, `wu03/layout-2.xml`
- Screenshots: `wu03/screenshot-1.png`, `wu03/screenshot-2.png`
- Process ID and scoped log: `wu03/process.txt`, `wu03/process-logcat.txt`
- Log review: `wu03/process-logcat-audit.txt`

Visual inspection of both PNGs: the activity title `example.livematch.android.MainActivity` is present, but the app content area is an otherwise uniform blank surface. Neither screenshot visibly shows `Orcas`, `Tigers`, schedule content, or any distinguishable Scheduled fixture output. The two layout dumps likewise expose only an empty content-child `android.widget.FrameLayout`; they do not provide text evidence of fixture rendering.

The process-scoped log contains no `FATAL EXCEPTION`, `AndroidRuntime`, `RemoteCompose`, decode, parser, unsupported-operation, or render matches. It does contain one unrelated-looking `AccessibilityNodeInfo` self-child error; absence of the requested error patterns is necessary but not sufficient and does not override the blank screenshots.

## What is and is not proven

Proven on the named emulator:

1. WU2's exact fixture bytes are retained by the creator check and embedded in the debug APK with the exact SHA-256.
2. The app can read those bytes, reject neither non-emptiness nor declared size, and call the existing adapter without an instrumented-test failure.
3. The activity resumes and its root `RemoteComposePlayer` has non-zero dimensions.
4. Two independent cold launches start successfully without the searched crash/decode/parser/unsupported/render log patterns.

Not proven (and required for GO):

- visible Scheduled fixture rendering; this is disproven by both screenshots;
- semantic or accessibility representation of the Scheduled content;
- any network, cache, action, Live/Finished, resilience, or later-work behavior.

## Workload and rollback

- Delivery boundary: **WU3 only**; no commit, push, PR, native-ledger mutation, or later work unit was performed.
- Original implementation delta from the launch snapshot: **+180 / -1 lines (181 total)**, including this report; later evidence-only compaction reduced it from 92 to 90 lines, while 378 raw-log deletions are separate evidence files; authoritative remediation delta is **393** (not 392).
- Cleanup receipt and deletion state: `docs/evidence/wu03/cleanup.txt`.
- Rollback boundary independent of commits: remove the WU3 Android Test catalog/runner additions, fixture staging task, `MainActivity` asset-loading handoff, `ScheduledFixturePlaybackTest`, and `docs/evidence/wu03-device-playback.md` plus `docs/evidence/wu03/`. This removes only WU3 packaging/playback proof and leaves WU2's canonical fixture and manifest untouched.
