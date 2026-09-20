# WU8 — action catalog, host allowlist, and a real (but unconfirmed end-to-end) click area

## Scope

Per `docs/spec-live-match-center.md` section 6.3 (action catalog) and section 9's failure
matrix ("Action ID desconocido | Ignorar, registrar y no ejecutar efectos"), this work unit
starts the deferred pipeline work: actions first, before envelope signing/cache/test-server.

## Public API investigation

Decompiled the alpha19 `remote-player-view` AAR (`classes.jar`, `javap -p -c`, no sources jar
available — same technique as WU6/WU7) and the `remote-creation-core` jar:

- `RemoteComposePlayer.addIdActionListener(IdActionCallbacks)` is public, with
  `IdActionCallbacks.onAction(int id, String metadata)`.
- `RemoteComposeWriter.addClickArea(int id, String contentDescription, float left, float top, float right, float bottom, String metadata)`
  is public on the creation side (`remote-creation-core`).
- `RemoteComposeView` (the internal view `RemoteComposePlayer` composes) has `updateClickAreas()`,
  which reads `CoreDocument.getClickAreas()` and, for each `ClickAreaRepresentation`, instantiates
  a real `ClickAreaView` child with `FrameLayout.LayoutParams` at the click area's bounds and wires
  its `OnClickListener` back to the id/metadata callback.

This is a real, wired, public action-authoring surface — not a dead API.

## What was built

- `contract/src/main/kotlin/example/livematch/contract/ActionCatalog.kt`: `MatchPhase` enum
  (SCHEDULED/LIVE/FINISHED/SUSPENDED/CANCELLED, matching the spec's state diagram),
  `ActionId` enum with wire values matching spec 6.3 exactly (`set_reminder`, `open_match`,
  `open_highlight`, `open_summary`, `refresh`) plus `fromWireValue`, and `ActionAllowlist.isAllowed(phase, actionId)`
  encoding spec 6.3's table exactly (CANCELLED allows nothing).
- `contract/src/test/kotlin/example/livematch/contract/ActionAllowlistTest.kt`: table-driven,
  covers every (phase, actionId) pair plus the unknown-wire-value and round-trip cases. Strict TDD:
  RED confirmed (compile failure, types didn't exist) before GREEN.
- `creator-jvm`'s `ProceduralJvmMatchDocumentCreator` (Scheduled only): added a real "Remind me"
  button — a `drawRect` + `drawTextAnchored` pair for the visual, plus a real
  `addClickArea(nextId(), "Set reminder", ..., ActionId.SET_REMINDER.wireValue)` call at the same
  bounds. RED confirmed first (byte-pattern test for the `"set_reminder"` ASCII literal failed
  before the production change), GREEN after.
- `app-android`'s `MainActivity`: registers `player.addIdActionListener { _, metadata -> onDocumentAction(metadata) }`
  before loading the document; `onDocumentAction` resolves the wire value via `ActionId.fromWireValue`,
  checks `ActionAllowlist.isAllowed(MatchPhase.SCHEDULED, actionId)`, and only logs acceptance —
  unknown or disallowed ids are logged-and-ignored, never reach further logic (no real "set a
  reminder" domain effect exists yet — out of scope, per spec section 6.3 "el host valida... antes
  de actuar").

## What could NOT be confirmed: end-to-end tap → onAction

Despite the button rendering correctly (see `scheduled-with-button.png` — navy card, "Remind me"
button, no visual regression from WU7's baseline) and the `set_reminder` metadata being present
in the encoded bytes (confirmed both by the JVM byte-pattern test and by APK-asset/source SHA-256
match, `2f84de7c0163431ba53523d07371e67826864312f7ddd3e14e3df247d7406ff4`), **tapping the button's
exact on-screen bounds never produced a `ClickAreaView` in the view hierarchy, and never fired
`onDocumentAction`** (confirmed by `adb shell uiautomator dump` showing zero children under the
player, and PID-scoped logcat around the tap showing no "Accepted action"/"Rejected"/"Ignoring" line).

Ruled out during investigation:
- **Not a coordinate problem**: the button's on-screen bounding box was measured directly from a
  screenshot by pixel-matching the button's exact color (`48,518`–`598,596`), and the tap was sent
  to its center (`323,557`).
- **Not an API-level/profile issue** (the WU3.2 "4-arg vs 6-arg writer" variable that explained an
  earlier rendering defect): re-authored the same document with the six-argument
  `RemoteComposeWriter(width, height, title, 6, 0, platform)` constructor (matching WU3.2's
  API-6 control exactly) — still zero click-area views, identical result.

**Working hypothesis, not yet confirmed**: `remote-creation-core` also ships a separate
component/modifier-based authoring surface — `ClickActionModifier`, `TouchActionModifier`,
`RcActionScope`/`RcRunActionScope` (all public classes seen in the same jar) — that this entire
project has never used. Every creator since WU1 has used the raw immediate-mode
`RemoteComposeWriter.apply { drawRect(...); drawTextAnchored(...) }` style with no component/layout
tree. `CoreDocument.getClickAreas()` may only populate from click areas declared through that
component/modifier tree, not from a bare `addClickArea()` call issued against an otherwise
tree-less, purely-imperative canvas sequence. This is a plausible, evidence-consistent explanation
but was not directly verified (would require authoring a document through the component/modifier
DSL as a separate experiment) — logged as the next concrete step, not asserted as fact.

## Decision made here

Keep the `ActionId`/`MatchPhase`/`ActionAllowlist` contract and the `MainActivity` listener wiring —
they are real, tested, and will work the moment click-area delivery is solved (the listener/allowlist
logic doesn't care how the tap arrived). Keep the "Remind me" button and its `addClickArea` call in
the Scheduled document too, since it doesn't regress rendering and documents current intent — but
do not claim the action-dispatch thesis (#3, "host retains control of actions") is demonstrated yet.
It is **partially** built (host-side validation is real and tested) and **not yet** proven end-to-end
against a real document-emitted tap.

## Verification run

- `./gradlew test :app-android:testDebugUnitTest` — all green (contract, creator-jvm, app-android
  unit tests), including the new `ActionAllowlistTest` (7 cases) and
  `ProceduralJvmMatchDocumentCreatorTest.scheduledDocumentAuthorsARealSetReminderClickArea`.
- Device: Pixel_9 / API 35, fingerprint-verified
  (`google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug/dev-keys`) before
  trusting evidence, per the recurring `docs/alpha-learnings.md` gotcha.
- APK asset hash matches source fixture hash exactly (see `wu08/apk-asset-hashes.txt`).
- Cold launch `TotalTime: 442ms`, `Status: ok` (`wu08/main-activity-launch.txt`).
- Screenshot confirms visible, correctly-rendered "Remind me" button with no regression
  (`wu08/scheduled-with-button.png`).
- View hierarchy dump after launch and after the tap shows no click-area child view in either case
  (`wu08/view-hierarchy.xml`).
- PID-scoped logcat around the tap shows no action-related log line
  (`wu08/tap-on-button-logcat.txt`).
- Emulator stopped after evidence capture.

No git commits, pushes, or PRs — building on top of root commit `15455ff`, leaving this work
uncommitted for review, same as every prior work unit before that one.

## Size

13 files touched (2 new: `ActionCatalog.kt`, `ActionAllowlistTest.kt`; 4 modified:
`ProceduralJvmMatchDocumentCreator.kt`, its test, `MainActivity.kt`, `scheduled-match.rc`/
`.manifest`; plus this evidence report and its supporting files). Authored source lines: ~90.
One cohesive slice, not split.
