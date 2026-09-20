# WU4 — Live match document

## Scope

Per user decision (2026-09-19), this work unit is scoped to the Live document variant only (`docs/spec-live-match-center.md` §3.2, document content), mirroring exactly how WU2 built the Scheduled variant. Explicitly deferred: actions dispatch (`open_match`/`open_highlight`), signed envelope beyond the existing `PayloadEnvelope`, `RemoteUiRepository`/caching/network validation, test-server, accessibility, observability, benchmarks, and the continuous local LIVE-pulse animation (the "LIVE" label rendered here is a static text label, not an animation).

## Contract and creator

- `contract/.../LiveMatchDocument.kt`: `LiveMatch` (id/homeTeam/awayTeam/homeScore/awayScore/period/clock, validated non-blank ids/teams, non-negative scores, non-blank period/clock), `LiveMatchRequest` (match/viewport/localeTag/theme/generationEpochMillis), `LiveMatchDocumentCreator` interface, `LiveMatchDocument` (payload/envelope/metadata). Reuses `Viewport`/`Theme`/`CreatorMetadata`/`RemoteDocumentPayload`/`PayloadEnvelope`/`SigningBoundary` from the existing WU2 contract unchanged.
- `creator-jvm/.../ProceduralJvmLiveMatchDocumentCreator.kt`: navy background (`0xFF002B5C`) + white text (`0xFFFFFFFF`), both explicitly set via `getRcPaint().setColor(...).setAntiAlias(true).commit()` before each draw (mirrors WU3.5's fixed pattern). Four separate short text lines: `"LIVE"`, `"{homeTeam} {homeScore}"`, `"{awayTeam} {awayScore}"`, `"{period} {clock}"`.

## Design correction during this work unit: avoid `drawTextAnchored` center-clip on long strings

The first implementation drew one concatenated score line (`"Orcas 2 - 1 Tigers"`). Device evidence showed roughly the first half of that string missing on-screen. `drawTextAnchored(text, x, y, panX=0f, panY=0f, angle=0)` anchors around its `(x,y)` point rather than starting flush-left at `x`, so long strings extend before `x=16` and get pushed off the left edge of the screen. Short strings (≤ ~8 chars, matching WU2's `"Orcas"`/`"Tigers"` pattern) stay on-screen. Fix applied **within this work unit's scope**: split the score into two short per-team lines instead of one long concatenated line — no change to the shared `drawTextAnchored` call itself. See `docs/alpha-learnings.md` for the general finding (this also explains a pre-existing, previously-unflagged artifact in WU3.5's own Scheduled evidence).

## Strict-TDD evidence

| Phase | Command | Outcome |
| --- | --- | --- |
| RED | `./gradlew :creator-jvm:test --tests example.livematch.creator.ProceduralJvmLiveMatchDocumentCreatorTest --no-daemon` | Expected compile failure: `ProceduralJvmLiveMatchDocumentCreator` unresolved, before production code existed. |
| GREEN | Same command | PASS after adding the contract types and creator: determinism, score/clock-mutation distinctness, explicit background/text color literals present in the encoded bytes. |
| Fixture generation | `./gradlew :creator-jvm:generateLiveFixture --no-daemon` then `:creator-jvm:checkLiveFixture --no-daemon` | PASS; `checkLiveFixture` byte-compares the regenerated document against the committed fixture. |
| Full regression | `./gradlew test --no-daemon` (see `docs/evidence/wu04/full-test-suite.txt`... consolidated with WU5, single run covers both) | PASS. |

## Fixture identity

| Fixture | SHA-256 |
| --- | --- |
| `fixtures/live-match.rc` | `03508160c0037d33a4363b7b89314dc1bf5121035a931cee1d3c0c77ea5b90d9` |

Source manifest: `docs/evidence/wu04/fixture-hashes.txt`. Packaged APK asset hash (`app-android/build/outputs/apk/debug/app-android-debug.apk!/assets/live-match.rc`) matches exactly: `docs/evidence/wu04/apk-asset-hashes.txt`.

## Device evidence

- Device: `emulator-5554`, AVD `Pixel_9`, Android 15 / API 35, `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug/dev-keys` — verified by fingerprint per the WU3.3 methodology gotcha, not just serial. See `docs/evidence/wu04/device-identity.txt`.
- Instrumentation: `ANDROID_SERIAL=emulator-5554 ./gradlew :app-android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=example.livematch.android.DiagnosticPlaybackTest --no-daemon` — PASS. `DiagnosticPlaybackActivity`'s fixture allowlist was extended with `LIVE_MATCH_FIXTURE_NAME = "live-match.rc"` (and `FINISHED_MATCH_FIXTURE_NAME`, used by WU5) and the fixture is staged into debug assets via `stageDiagnosticFixture` in `app-android/build.gradle.kts`. No change to the WU3.5 Compose host beyond adding these fixture names to the existing allowlist.
- Manual cold launch: `adb -s emulator-5554 shell am start -W -n example.livematch.android/.DiagnosticPlaybackActivity --es mode post_attach --es fixture live-match.rc` — `Status: ok`, cold, 400 ms. See `docs/evidence/wu04/live-match-launch.txt`.
- Screenshot: `docs/evidence/wu04/live-match.png` (full 1080x2424) and `docs/evidence/wu04/live-match-zoom.png` (top-left 270x150 crop, 3x nearest-neighbor upscale, for legibility). All four lines are present and legible: `LIVE`, `Orcas 2`, `Tigers 1`, `Half 67:12`.
- Process-scoped logcat: cleared before launch, captured by PID, searched for `RemoteComposePlayer`/`Error loading document`/`Error setting document`/`Unsupported document`/`exception` — no matches. See `docs/evidence/wu04/live-match-logcat.txt` and `-logcat-audit.txt`.

### Known cosmetic overlap (not a defect in this document)

The system status bar's own clock (large glyphs, e.g. `"4:55"` in the captured screenshot) visually overlaps the `"Orcas 2"` line in the top-left corner, because `DiagnosticPlaybackActivity` does not apply window insets and the small 320x180 document viewport sits within the status bar's screen region. This is a host/layout characteristic, not a text-encoding defect — the underlying `"Orcas 2"` text is present and legible around the overlap (confirmed in the zoomed crop). This is the same class of issue as WU3.4's ActionBar occlusion but distinct (status bar, not ActionBar; not fixed by the WU3.5 `NoActionBar` theme). See `docs/alpha-learnings.md` — logged as a follow-up, out of scope for this document-content-only work unit.

## Verdict

**GO** for the Live document's data content (score, team lines, period/clock, LIVE label) rendering correctly and legibly on Pixel_9/API 35, once the center-anchor text-length issue was worked around by using short per-line strings. The status-bar overlap is a separate, pre-existing, out-of-scope host/layout issue, not a regression introduced here.
