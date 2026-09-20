# WU5 — Finished match document

## Scope

Same scope decision as WU4 (see `docs/evidence/wu04-live-match-document.md`): Finished document content only, per `docs/spec-live-match-center.md` §3.3. `open_summary` action dispatch, envelope signing, caching, and the rest of the deferred list from WU4 are out of scope here too.

## Contract and creator

- `contract/.../FinishedMatchDocument.kt`: `FinishedMatch` (id/homeTeam/awayTeam/finalHomeScore/finalAwayScore/statusLabel, same validation style as `LiveMatch`), `FinishedMatchRequest`, `FinishedMatchDocumentCreator` interface, `FinishedMatchDocument`. Reuses the same shared contract types as WU2/WU4.
- `creator-jvm/.../ProceduralJvmFinishedMatchDocumentCreator.kt`: same navy/white explicit-paint pattern as WU4. Three short lines: status label, `"{homeTeam} {finalHomeScore}"`, `"{awayTeam} {finalAwayScore}"`.

## Design correction carried over from WU4

The fixture's status label was originally `"FULL TIME"` (9 characters). Device evidence showed its leading `"F"` clipped off the left edge of the screen — the same `drawTextAnchored` center-anchor behavior documented in WU4/`docs/alpha-learnings.md`, at a smaller magnitude (partial single-character clip rather than half the string). Fixed by shortening the label to `"FINAL"` (5 characters, same length class as `"LIVE"`/`"Orcas"`/`"Tigers"`, which do not clip) — a wording choice within this work unit's scope, not a change to the shared text-drawing helper.

## Strict-TDD evidence

| Phase | Command | Outcome |
| --- | --- | --- |
| RED | `./gradlew :creator-jvm:test --tests example.livematch.creator.ProceduralJvmFinishedMatchDocumentCreatorTest --no-daemon` | Expected compile failure: `ProceduralJvmFinishedMatchDocumentCreator` unresolved. |
| GREEN | Same command | PASS: determinism, final-score/status mutation distinctness, explicit background/text color literals present. |
| Fixture generation | `./gradlew :creator-jvm:generateFinishedFixture --no-daemon` then `:creator-jvm:checkFinishedFixture --no-daemon` | PASS. |
| Full regression | `./gradlew test --no-daemon` | PASS (single run covering contract, creator-jvm, and app-android unit tests for both WU4 and WU5). |

## Fixture identity

| Fixture | SHA-256 |
| --- | --- |
| `fixtures/finished-match.rc` | `0e4af90474d3e7ee4b64e9bc85ebe4d11f3f3da5f4e29a72982bcf86b66be377` |

Source manifest: `docs/evidence/wu05/fixture-hashes.txt`. Packaged APK asset hash matches exactly: `docs/evidence/wu05/apk-asset-hashes.txt`.

## Device evidence

- Device: same `emulator-5554` / `Pixel_9` / API 35 session as WU4; identity re-confirmed by fingerprint, see `docs/evidence/wu05/device-identity.txt`.
- Instrumentation: the same `DiagnosticPlaybackTest` run covers `FINISHED_MATCH_FIXTURE_NAME` — PASS (see WU4's evidence doc for the command; one combined run covers all four diagnostic fixtures plus Live and Finished).
- Manual cold launch: `adb -s emulator-5554 shell am start -W -n example.livematch.android/.DiagnosticPlaybackActivity --es mode post_attach --es fixture finished-match.rc` — `Status: ok`, cold, 408 ms. See `docs/evidence/wu05/finished-match-launch.txt`.
- Screenshot: `docs/evidence/wu05/finished-match.png` and zoomed crop `docs/evidence/wu05/finished-match-zoom.png`. All lines legible: `FINAL`, `Orcas 2`, `Tigers 1`.
- Process-scoped logcat: no matches for the same error-pattern search as WU4. See `docs/evidence/wu05/finished-match-logcat.txt` / `-logcat-audit.txt`.

### Known cosmetic overlap (not a defect in this document)

Same system-status-bar-clock overlap as documented in WU4 affects the `"Orcas 2"` line here too (confirmed present in `finished-match-zoom.png`). Same conclusion: host/layout characteristic, not a document defect, logged as a follow-up in `docs/alpha-learnings.md`.

## Verdict

**GO** for the Finished document's data content (status label, both team/score lines) rendering correctly and legibly on Pixel_9/API 35.

## What's still deferred (WU4 + WU5 combined)

- Actions: `open_match`, `open_highlight`, `open_summary` dispatch and the host-side allowlist/dispatcher.
- Envelope signing beyond the existing unsigned `PayloadEnvelope.forPayload(..., EXTERNAL_SIGNATURE_REQUIRED)` marker.
- `RemoteUiRepository`, caching, ETag-based network validation, ordering/expiry policy.
- `test-server`.
- Accessibility (TalkBack, font scale, semantic score phrasing).
- Observability metrics.
- Benchmarks.
- Cancelled and Suspended states.
- The continuous local LIVE-pulse animation (current `"LIVE"` label is static text).
- Wiring `MainActivity` to actually switch between Scheduled/Live/Finished based on real match state — `MainActivity` still only loads `scheduled-match.rc`; Live/Finished are currently reachable only through the debug-only `DiagnosticPlaybackActivity`, same as the WU3.x diagnostic fixtures.
- The system-status-bar overlap on small-viewport diagnostic documents (see `docs/alpha-learnings.md`).
