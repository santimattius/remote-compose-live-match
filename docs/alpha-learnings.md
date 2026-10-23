# AndroidX Remote Compose alpha — running learnings log

Dependency is pre-1.0 (`androidx.compose.remote`, currently pinned at `1.0.0-alpha19`). This log tracks concrete, evidence-backed gotchas and non-obvious behaviors found while integrating it, so later work doesn't re-discover the same surprises. Each entry is dated and links to the evidence that backs it. Keep entries short; full detail lives in `docs/evidence/`.

## 2026-09-20 — Remote Compose alpha19 has no built-in distribution/signing envelope; it's app-layer work (WU11)

`remote-core`/`remote-creation-core`/`remote-player-view` only cover document encoding and
on-device rendering — there is no library-provided concept of a signed envelope, schema version,
expiration, or `minHostVersion` compatibility check. Spec section 6.2's `RemoteUiEnvelope` and
section 9's validation list had to be built entirely with plain JDK APIs
(`java.security.Signature`/`KeyFactory` with `Ed25519`, no external dependency) wrapping the raw
`.rc` bytes, not anything the Remote Compose library itself exposes. Worth knowing before
assuming any part of "validate before decode" is a library feature you can just call into — none
of it is; see `docs/evidence/wu11-signed-envelope.md`.

## 2026-09-20 — Loading a document `post_attach` registers click areas but never materializes their `ClickAreaView` children; load `pre_attach`-style for interactive documents (WU10)

Extending WU9's `open_match`/`open_highlight`/`open_summary` buttons to the Live and Finished
documents initially showed **zero** `ClickAreaView` children on-device even though the byte-level
click-area data was present and correctly encoded (verified two independent ways). Reflection on
the live view tree (`RemoteComposeView.mDocument.getDocument().getClickAreas()`) confirmed the
click areas ARE registered at the document-model level (2 for Live, 1 for Finished, matching what
was authored) — but `RemoteComposeView`'s child count was `0` in both cases when the document was
loaded via `DiagnosticPlaybackActivity`'s `MODE_POST_ATTACH` path (`player.post { load(...) }`,
i.e. loading a document into a player that has already gone through at least one attach/layout
pass with no document). Switching to `MODE_PRE_ATTACH` (load synchronously before the view is
returned from the `AndroidView` factory — the same timing `MainActivity` already uses in
production) made the `ClickAreaView`s materialize correctly (child count 2 and 1, taps fire).
**Takeaway:** `updateClickAreas()`'s child-view creation appears to run during initial
attach/layout, not on every `setDocument()` call — swapping in a new document with click areas
*after* that point does not retroactively create their `ClickAreaView`s. This doesn't affect
`MainActivity` (always loads before attach) but is a real constraint on any future "swap the live
document mid-session" flow (e.g. Scheduled → Live transition) — that will need to be tested
explicitly, not assumed to work, once that flow exists. See `docs/evidence/wu10-live-finished-actions.md`.

## 2026-09-20 — `uiautomator dump` does not reliably show `ClickAreaView`; use reflection on the live view tree instead, and watch listener-registration order around `setDocument` (WU9)

WU8's "click delivery is broken, maybe needs a component/modifier DSL" hypothesis (below) was
**wrong**. The real defect: `RemoteComposePlayer.addIdActionListener(...)` registers directly
onto whichever `CoreDocument` instance the internal `RemoteComposeView.mDocument` currently
points to — with no deferral. `RemoteComposePlayer.setDocument(bytes)` **replaces** that
`CoreDocument` instance. Calling `addIdActionListener` before `setDocument` (this project's
original order) attaches the listener to a document that's about to be discarded; the live
document that the tap is actually evaluated against ends up with an empty listener set, so the
tap is silently swallowed with no crash, no log, nothing. **Fix: always call
`addIdActionListener` after `setDocument`/`load`, never before.** Separately: WU8's
`uiautomator dump` showing zero children under the player was a red herring — `uiautomator`
walks the **accessibility** tree, and `ClickAreaView` isn't exposed there even when it's a real,
correctly-bounded, clickable child of the live View tree (confirmed by reflecting on
`player`'s private `mInner`/`ClickAreaView` fields). **Takeaway:** when diagnosing whether a
View got created/attached, prefer reflection on the actual object graph (or a debug-only
accessor) over `uiautomator dump`, which can under-report. See
`docs/evidence/wu09-click-area-root-cause.md`.

## RESOLVED (WU9) — `RemoteComposeWriter.addClickArea` + `RemoteComposePlayer.addIdActionListener` exist and are wired in the player's source, but never fired for this project's immediate-mode authoring style (WU8)

**Resolved 2026-09-20 in WU9** — see the entry directly above. Both halves of a real
action-dispatch API are public: `RemoteComposeWriter.addClickArea(id, contentDescription, left, top, right, bottom, metadata)` on the creation side, and `RemoteComposePlayer.addIdActionListener(IdActionCallbacks)` (`onAction(int, String)`) on the player side, connected internally through `RemoteComposeView.updateClickAreas()` reading `CoreDocument.getClickAreas()` and materializing a real `ClickAreaView` child per click area. Authoring a click area against a bare `RemoteComposeWriter.apply { drawRect(...); addClickArea(...) }` sequence (this project's style since WU1, no component/layout tree) works fine — WU8 mistook a host-side listener-ordering bug plus an accessibility-tree blind spot for a library/authoring-style limitation. The originally-floated hypothesis that `remote-creation-core`'s component/modifier DSL (`ClickActionModifier`, `TouchActionModifier`, `RcActionScope`) would be required was investigated and **ruled out** — it was never the cause. See `docs/evidence/wu08-action-allowlist.md` (original investigation) and `docs/evidence/wu09-click-area-root-cause.md` (root cause and fix).

## 2026-09-19 — `RemoteComposePlayer` has no scale-to-fit; procedurally-drawn absolute pixel coordinates never adapt to the real device size (WU7)

Decompiled the alpha19 `remote-player-view` runtime (`javap -p`, no sources jar): `RemoteComposePlayer`'s full public surface has no scale/fit/content-scale API, and `RemoteComposeView.onDraw` sets the `RemoteContext`'s width/height to the *view's* actual measured size (which is already full-screen, since our hosts use `AndroidView(Modifier.fillMaxSize())`) but calls `RemoteDocument.paint(...)` with no `canvas.scale(...)` anywhere in the path. A document authored with raw `drawRect`/`drawTextAnchored` absolute pixel coordinates (this project's procedural style — not the library's relative/fill-based layout-component DSL) is drawn 1:1 into that bigger canvas: it does **not** stretch to fill it. A small declared `Viewport` (e.g. the original `320x180`) therefore renders as a small box in the corner of any larger screen, regardless of how the host view is sized — this looks like a layout bug but is actually just literal pixel coordinates on a bigger canvas. **Takeaway:** for this procedural/absolute-coordinate authoring style, the `Viewport` dimensions ARE the real on-screen pixel size — choose them to match the target device class (this project now uses `1080x480`, matching the Pixel_9's physical width), don't treat them as an abstract "design size" the player will scale up. Revisit if a future work unit switches to the library's relative-layout DSL, which may consult the context's actual width/height differently. See `docs/evidence/wu07-viewport-sizing-fix.md`.

## RESOLVED (WU6) — `drawTextAnchored(text, x, y, panX=0, panY=0, angle)` anchors on `(x,y)`, not flush-left; long strings clip off the left edge (WU4/WU5)

**Resolved 2026-09-19 in WU6**: decompiling `DrawTextAnchored.paint()` (`javap -c -p`, no sources jar available for alpha19) showed `getHorizontalOffset()` computes `-(w*(1+panX))/2 - mBounds[0]`, so `panX=-1f` is a real, documented-by-bytecode flush-left anchor (not a guess) — `panX=0f` centers, `panX=+1f` is flush-right. Changed `panX` from `0f` to `-1f` on every `drawTextAnchored` call across all three creators (Scheduled/Live/Finished); `panY` left at `0f` (no vertical-clipping symptom was found). All three canonical fixtures regenerated and reverified visible in full on Pixel_9/API 35 (13-digit timestamp, "2nd Half 67:12", "FINAL" — all previously-clipped strings now complete). See `docs/evidence/wu06-text-anchor-and-inset-fix.md`.

### Original finding

With `panX=0f`/`panY=0f`, `drawTextAnchored` centers the text on the given `(x,y)` point rather than starting flush-left at `x`. Short strings (≤ ~8 characters, e.g. `"Orcas"`, `"Tigers"`, `"LIVE"`) stay fully on-screen with the small `x=16` margin used throughout this project. Longer strings extend before `x`, and once that goes negative they're pushed off the left edge of the screen entirely. Concretely: a concatenated score line `"Orcas 2 - 1 Tigers"` (19 chars) lost roughly its first half (`"Orcas"` plus part of the separator) on Pixel_9/API 35; a 9-character status label (`"FULL TIME"`) lost only its leading `"F"`. **This is not new to WU4/WU5** — it also affects WU2's own Scheduled fixture: the `scheduledEpochMillis.toString()` line (13 digits) shows only its last ~9 digits on-device; this was visible in WU3.5's own `main-activity.png` evidence but not flagged as text-clipping at the time. **Takeaway:** keep any single `drawTextAnchored` call's text short (single word or short phrase); split longer composite content (e.g. "team + score") into multiple short lines instead of concatenating, or investigate the public API for a left-aligned anchor mode before authoring longer strings. See `docs/evidence/wu04-live-match-document.md` and `docs/evidence/wu05-finished-match-document.md`.

## RESOLVED (WU6) — The system status bar can visually overlap document content in a diagnostic/small-viewport host (WU4/WU5, previously latent since WU3.5)

**Resolved 2026-09-19 in WU6**: added `Modifier.windowInsetsPadding(WindowInsets.systemBars)` to the `AndroidView` hosting `RemoteComposePlayer` in both `MainActivity` and `DiagnosticPlaybackActivity` (the outer `Surface` keeps plain `fillMaxSize()`, so the screen background stays edge-to-edge — only the player's content area is inset). Reverified on Pixel_9/API 35 for all three fixtures: navy content box now renders entirely below the status bar, no glyph overlap. See `docs/evidence/wu06-text-anchor-and-inset-fix.md`.

### Original finding

`DiagnosticPlaybackActivity` (and `MainActivity`) render their `RemoteComposePlayer` full-screen via `AndroidView`/`Modifier.fillMaxSize()` without applying window insets. The Remote Compose document itself is drawn at its declared viewport size (e.g. 320x180) positioned at the view's origin — i.e. the very top-left of the physical screen, which is also where the system status bar (clock, icons) renders. On Pixel_9/API 35 this produces a real visual collision: the system status bar's own (much larger) clock glyphs partially overlap document text positioned near `y=64` in a 180-unit-tall document. This was already present in WU3.5's own `main-activity.png` evidence (the `"4:11"` visible there is the *system* clock, not document content — the Scheduled document never renders a formatted clock) but was not identified as an overlap at the time. **This is a different mechanism from WU3.4's ActionBar occlusion** (that was the host's own opaque `ActionBar` fully covering the player; this is the *system* status bar's own foreground glyphs partially overlapping content that otherwise renders correctly underneath). Not fixed here — out of scope for WU4/WU5 (document content only). A real fix would need window-insets-aware layout (e.g. padding content below the status bar, or choosing a viewport/placement that doesn't sit in that band) in a future host-layout work unit. See `docs/evidence/wu04-live-match-document.md`.

## 2026-09-19 — `RemoteComposePlayer` is a plain `View`; hosting it from Jetpack Compose needs `AndroidView`, and the newest `activity-compose` outran this project's AGP/compileSdk (WU3.5)

`androidx.compose.remote:remote-player-view`'s `RemoteComposePlayer` is a regular Android `View`/`ViewGroup`, not a `@Composable` — despite both libraries sharing the word "Compose", there is no Compose-native remote-compose player API. Hosting it from a real Jetpack Compose UI (`ComponentActivity` + `setContent`) requires `androidx.compose.ui.viewinterop.AndroidView` as ordinary View/Compose interop, same as any other legacy `View`.

Separately: `androidx.activity:activity-compose:1.13.0` (latest stable as of this check) requires compileSdk 36 and AGP 8.9.1+; this project is pinned to compileSdk 35 / AGP 8.8.2 (matching the Pixel_9/API 35 device evidence baseline used throughout WU3.x). Building against 1.13.0 fails fast with a clear Gradle dependency-constraint error naming the exact required bump. Used `activity-compose:1.10.1` instead — the latest version that still targets compileSdk 35. **Takeaway:** when adding any `androidx.activity`/`androidx.core`-family dependency to this project, check its required compileSdk/AGP floor before assuming "latest stable" is safe to pin; Gradle's own error message states the exact requirement, no guessing needed. See `docs/evidence/wu03.5-compose-host-migration.md`.

Also from the same WU: existing instrumentation tests that asserted `root.getChildAt(0) is RemoteComposePlayer` on `android.R.id.content` broke once the host became Compose-based — that child is now a `ComposeView`/`AndroidComposeView` wrapping the player several levels deep, not the player itself. Any future test asserting on the hosted player's view identity needs a recursive view-tree search, not a direct child index.

## 2026-09-19 — Root cause of "blank render" was ActionBar occlusion, not the library (WU3.4)

A plain `Activity`'s default `ActionBar` (opaque, ~289px tall on Pixel_9/API 35) composites on top of a full-screen `RemoteComposePlayer` added via bare `setContentView(player)`. The player is never resized or clipped by this — it still measures full-screen — but its content is drawn *underneath* the opaque chrome. Every document tested through WU3–WU3.3 (`scheduled-match.rc`, `paint-probe.rc`, both `320x180`; `alpha18-api6-control.rc`, `600x600` with a circle whose top three-quarters fall in that band) happened to paint content anchored near the player's origin, so it was entirely or mostly hidden. This produced three work units' worth of "blank/clipped render, must be a document/player/protocol compatibility bug" evidence that was actually a UI-chrome occlusion issue. Fix: call `actionBar?.hide()` before `setContentView`, or use a no-action-bar theme, or otherwise avoid opaque chrome overlapping the player. See `docs/evidence/wu03.4-actionbar-occlusion.md`.

**Takeaway for future diagnostics on this library:** when a document "doesn't render," check what's compositing on top of the player view (via `adb shell uiautomator dump`) before suspecting the document encoding, the player, or the library version. This check is one `adb` command and should be step zero, not a late-stage hypothesis.

## 2026-09-19 — alpha18 → alpha19 bump is a no-op for this project so far (WU3.3)

Upgrading `remote-compose` from `1.0.0-alpha18` to `1.0.0-alpha19` required no source changes and produced byte-identical fixtures from the same procedural `RemoteComposeWriter` calls. alpha19's changelog (density-scaling fix for `RemoteDp`, text-alignment fix) does not touch the four-arg/six-arg writer paths this project uses. Do not assume a version bump alone will change rendering behavior for procedurally-authored documents; verify with a real device screenshot, not just the changelog. See `docs/evidence/wu03.3-alpha19-upgrade.md`.

## 2026-09-16 — Legacy path logs a harmless recurring warning (WU3.2/WU3.3/WU3.4)

The four-argument (`paint-probe`) writer path logs `System.err: error unknown Paint Type 14` on every launch, on alpha18 and alpha19 alike, on both this diagnostic activity and confirmed cosmetic — it does not correlate with rendering failure (WU3.4 confirmed the same path renders pixel-correct once occlusion is removed, warning and all). The six-argument API-6 path never logs it. Don't chase this warning as a rendering-blocker lead; it isn't one. See `docs/evidence/wu03.2-alpha18-api6-control.md`.

## 2026-09-16 — Verify AVD/API identity by fingerprint, not just adb serial (WU3.3 methodology note)

An `emulator-5554` serial was found already running a `Pixel_10_Pro`/API 36 instance, not the intended `Pixel_9`/API 35, before WU3.3's device evidence was captured. A stale/wrong emulator on a reused serial would silently invalidate a cross-work-unit comparison. Always confirm `ro.build.version.release`, `ro.build.version.sdk`, and `ro.build.fingerprint` (or `adb emu avd name`) match the intended AVD before trusting device evidence as controlled — a matching serial is not enough. See `docs/evidence/wu03.3-alpha19-upgrade.md`.

## Resolved follow-up: missing text paint color (found in WU3.4, fixed in WU3.5)

`ProceduralJvmMatchDocumentCreator.create()` drew team-name/kickoff-time text via `drawTextAnchored(...)` without ever calling `getRcPaint().setColor(...)` first, unlike the diagnostic paint probe. Fixed by explicitly setting navy (`0xFF002B5C`) before the background `drawRect` and white (`0xFFFFFFFF`) before the text draws. This changed the canonical `scheduled-match.rc` fixture's bytes/SHA-256 (regenerated and reverified against the packaged APK asset). See `docs/evidence/wu03.5-compose-host-migration.md`.
