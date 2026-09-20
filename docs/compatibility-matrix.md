# Compatibility matrix

| Layer | Pinned version | Feasibility result |
| --- | --- | --- |
| JDK | Temurin 17.0.10 | GO for Gradle/Kotlin compilation |
| Gradle wrapper | 8.10.2 | GO |
| Android Gradle Plugin | 8.8.2 | GO with compile/target SDK 35 |
| Kotlin plugins | 2.1.20 | GO |
| Compose BOM | 2025.04.01 | GO — now actually used (WU3.5): `MainActivity`/`DiagnosticPlaybackActivity` host `RemoteComposePlayer` via `ComponentActivity` + `setContent` + `AndroidView`, with Material3 and `androidx.activity:activity-compose:1.10.1` (1.13.0 requires compileSdk 36/AGP 8.9.1+, incompatible with this project's pinned compileSdk 35/AGP 8.8.2) |
| AndroidX Remote Compose | 1.0.0-alpha19 (bumped from alpha18 in WU3.3; drop-in, no source changes, byte-identical fixtures) | Artifacts resolve |
| JVM procedural creation | `remote-core`, `remote-creation-core`, `remote-creation-jvm` | GO: deterministic non-empty bytes compile and test. Covers Scheduled (WU2), Live (WU4), and Finished (WU5) document variants. |
| JVM Compose capture | `remote-creation-compose` / `captureSingleRemoteDocument` | **NO-GO**: direct alpha18/19 compile probe requires `android.content.Context` as the first parameter |
| Android player | `remote-core`, `remote-player-view` | GO: compile, exact fixture installation, focused device test, **and visible pixel-correct rendering confirmed in production (WU3.5)** — hosted via Jetpack Compose (`AndroidView`) under a `NoActionBar` theme, not the legacy imperative `actionBar?.hide()` diagnostic workaround |
| Transport truncation guard | `RemoteDocumentPayload` | GO for declared-length mismatch before player handoff |
| Full Live Match Center | alpha19 baseline | **GO for the WU3 rendering gate** (WU3.5): the real `MainActivity` + `scheduled-match.rc` document now renders both its navy background and its team-name/kickoff-time text on Pixel_9/API 35. **Live (WU4) and Finished (WU5) document variants are also GO** for their data content (score, team lines, period/clock, status label) via the debug-only `DiagnosticPlaybackActivity`; `MainActivity` itself still only loads `scheduled-match.rc` — actual phase-switching, actions, envelope signing, caching, and the rest of the deferred list (see `docs/evidence/wu05-finished-match-document.md`) remain out of scope. **WU6 fixed the two rendering defects found along the way** (see rows above and note below) — all three documents now render fully un-clipped and free of system-bar overlap. |

The Context requirement is reproducibly checked by `./gradlew :app-android:testDebugUnitTest --no-daemon`: `CaptureApiEvidenceTest` assigns the public function to a Context-first type, so a signature without that parameter does not compile. The complete verification sequence is `./gradlew :creator-jvm:test :app-android:testDebugUnitTest --no-daemon` followed by `./gradlew test --no-daemon`; both passed.

The alpha18/19 player uses `RemoteComposePlayer.setDocument(ByteArray)` through the isolated Android adapter. Compile, exact fixture installation, and focused device-test evidence are GO. Visible rendering was root-caused as NO-GO through WU3–WU3.3 due to the host Activity's default ActionBar occluding the player's content (see `docs/evidence/wu03.4-actionbar-occlusion.md` and `docs/alpha-learnings.md`); WU3.4 confirmed pixel-correct rendering for both diagnostic fixtures once that occlusion is removed via a diagnostic-only workaround. WU2's four-arg and WU3.2's six-arg `RemoteComposeWriter` paths are both confirmed correct — WU3.2's original "compare 4-arg vs 6-arg header/serialization" follow-up is superseded and no longer needed. **WU3.5 replaced the diagnostic workaround with a real production fix**: a `NoActionBar` theme plus a Jetpack Compose host (`ComponentActivity`/`AndroidView`), and additionally fixed an incidental missing-text-paint-color defect in `ProceduralJvmMatchDocumentCreator`. The real `scheduled-match.rc` document now renders both background and text on-device — see `docs/evidence/wu03.5-compose-host-migration.md`. The canonical fixture SHA-256 changed accordingly: `2feee2fa05b3fe13bb9b21e90269212ad3620ee91f3d00e5afb85e30cb555447`.

**WU4/WU5** added `LiveMatchDocument`/`FinishedMatchDocument` contract types and `ProceduralJvmLiveMatchDocumentCreator`/`ProceduralJvmFinishedMatchDocumentCreator`, reusing WU2's shared `Viewport`/`Theme`/`CreatorMetadata`/`RemoteDocumentPayload`/`PayloadEnvelope` types unchanged. Device evidence on Pixel_9/API 35 confirmed both new fixtures render their data content correctly through `DiagnosticPlaybackActivity`. Along the way, found a `drawTextAnchored` center-anchor text-clipping behavior (long strings pushed off-screen) that also latently affected WU2's Scheduled document, and a separate system-status-bar/document-viewport visual overlap present since WU3.5.

**WU6** fixed both findings for real (not worked around): `drawTextAnchored`'s `panX` changed from `0f` (center) to `-1f` (flush-left, confirmed via bytecode decompilation of `DrawTextAnchored.paint()` — see `docs/alpha-learnings.md`) across all three creators, and `Modifier.windowInsetsPadding(WindowInsets.systemBars)` added to the `AndroidView` in both `MainActivity` and `DiagnosticPlaybackActivity`. This regenerated all three canonical fixtures again:

| Fixture | SHA-256 (current) |
| --- | --- |
| `fixtures/scheduled-match.rc` | `320b34f5f6925c722f260691d52406bf3c20854fcb2a37f1359cd9c26a6f62fd` |
| `fixtures/live-match.rc` | `33348eb6973b6d49041e5948c603d1cdec2b53da634d16f56c5565c9868bfc91` |
| `fixtures/finished-match.rc` | `343475cacd58475391cbfcbe4e1170ba69b80dcb9afffcd1e155e4a268541f3a` |

All three verified on Pixel_9/API 35 with full un-clipped text and no status-bar overlap. See `docs/evidence/wu04-live-match-document.md`, `docs/evidence/wu05-finished-match-document.md`, and `docs/evidence/wu06-text-anchor-and-inset-fix.md`.

**WU7** fixed the third user-flagged finding: all three documents were rendering full-content-correct but visually tiny (a `320x180` card in the corner of a `1080x2424` screen). Decompiled `RemoteComposePlayer`/`RemoteComposeView` (alpha19) and confirmed there is no public scale-to-fit API, and this project's raw absolute-pixel drawing style (`drawRect`/`drawTextAnchored`) is never rescaled by the player regardless of the host view's actual size — see `docs/alpha-learnings.md`. Fix: authored `Viewport(1080, 480)` (matching the Pixel_9's physical width) instead of `320x180`, with an explicit larger `setTextSize(48f)` and rescaled line positions. This regenerated all three fixtures again:

| Fixture | SHA-256 (current) |
| --- | --- |
| `fixtures/scheduled-match.rc` | `d46ee2a7cecb25dcb4f24e2c3cbd3bfa5d491fa8034f838437f45252fb370623` |
| `fixtures/live-match.rc` | `2066bb73c4d959b2cca5fb5f3061a28d735f86bcb27a589127e1df864ebcde02` |
| `fixtures/finished-match.rc` | `38951ed0066d18e32ff118d15538f5803822dfc3b742ea0c72f1220d70136a1b` |

All three verified on Pixel_9/API 35: full-width card, legible text, no clipping, no status-bar overlap. See `docs/evidence/wu07-viewport-sizing-fix.md`. This closes all three findings the user raised on the Live/Finished work (text clipping, status-bar overlap, tiny viewport).
