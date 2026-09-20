# WU9 — click delivery root cause: listener registered before setDocument, not a DSL gap

## Summary

WU8 hypothesized that `addClickArea`'s failure to deliver taps required a separate
component/modifier DSL (`ClickActionModifier`/`RcActionScope`) instead of the immediate-mode
`drawRect`/`addClickArea` style used since WU1. **That hypothesis was wrong.** The immediate-mode
API works correctly. The real defect was a one-line ordering bug in `MainActivity`:
`player.addIdActionListener(...)` was called **before** `RemoteComposePlayerAdapter().load(player, document)`
(which calls `RemoteComposePlayer.setDocument(bytes)`).

## Investigation

Decompiled `remote-player-view` and `remote-core` (alpha19, `javap -p -c`, no sources jar
available — same technique as WU6/WU7/WU8) to trace the exact call chain from a tap to
`IdActionCallbacks.onAction`:

1. `RemoteComposePlayer extends FrameLayout` and holds an internal
   `RemoteComposeView mInner` (also a `FrameLayout`) that does the actual document
   parsing/paint/touch work.
2. `RemoteComposePlayer.addIdActionListener(cb)` forwards directly to
   `mInner.addIdActionListener(cb)`, which does
   `this.mDocument.getDocument().addIdActionListener(...)` — i.e. it registers the callback on
   **whatever `CoreDocument` instance `mInner.mDocument` currently points to**, with no
   deferral or pending-registration mechanism.
3. `RemoteComposePlayer.setDocument(bytes)` **replaces** `mInner`'s `RemoteDocument`/
   `CoreDocument` with a brand-new instance (confirmed by reflection: `getClickAreas().size()`
   is `1` immediately after `setDocument()` returns, populated by the new document's own
   `ClickArea.apply()` during initial parse — not by anything from before).
4. On tap, `RemoteComposeView`'s touch handling ultimately reaches the `ClickAreaView`'s
   `OnClickListener`, which calls `CoreDocument.performClick(context, id, metadata)` →
   `warnClickListeners(...)` → `CoreDocument.notifyOfException(id, metadata)` (misleadingly
   named — it just iterates `mIdActionListeners` and calls `onAction(id, metadata)` on each).

If `addIdActionListener` is called **before** `setDocument`, the callback is attached to the
CoreDocument instance that exists at that moment — which `setDocument` then discards and
replaces. The new document (the one the click area, and the tap, are actually evaluated
against) has an **empty** `mIdActionListeners` set, so `notifyOfException` has nothing to call.
Calling `addIdActionListener` **after** `setDocument` attaches to the document instance that's
actually live going forward, and everything works.

### Dead ends ruled out along the way (kept for the next investigator)

- **Not a missing `ClickAreaView`.** WU8's `uiautomator dump` showing zero children was a false
  signal: `uiautomator` walks the **accessibility** node tree, and `ClickAreaView` apparently
  isn't exposed there. Reflection on the live view tree
  (`player.mInner.getChildAt(0)`) shows a real, correctly-bounded, clickable
  `ClickAreaView` with a working `OnClickListener` from the very first frame. **Do not trust
  `uiautomator dump` alone to check for `ClickAreaView` presence** — inspect the actual View
  tree (reflection, or a debug-only accessor) instead.
- **Not a click-area registration timing problem inside `CoreDocument`.** `getClickAreas()`
  already returns the registered area immediately after `setDocument()` returns, well before
  any paint frame — registration happens synchronously during document parse, not lazily during
  paint.
- **Not a coordinate/inset problem.** The `ClickAreaView`'s `getLocationOnScreen()` matched the
  tap coordinates exactly (`[48, 522]`, `552x80`, tapped at `(300, 560)`).
- **Confirmed `performClick()` alone doesn't prove wiring.** Calling `child.performClick()`
  programmatically returned `true` (a listener ran) even under the **broken** ordering, because
  `View.performClick()` only reports whether *a* click listener fired — it doesn't reveal that
  the fired listener's downstream `CoreDocument.performClick` call found the click area but
  notified an empty (orphaned) listener set. This is why the fix had to be verified by asserting
  the actual dispatched `ActionId` reached `MainActivity`, not just that some click handler ran.

## Fix

`app-android/src/main/kotlin/example/livematch/android/MainActivity.kt`:
call `RemoteComposePlayerAdapter().load(player, document)` **before**
`player.addIdActionListener { ... }`, with a comment explaining why the order matters.

`DiagnosticPlaybackActivity` doesn't register any action listener, so it wasn't affected.

## Strict-TDD evidence

| Phase | Command | Outcome |
| --- | --- | --- |
| RED | `./gradlew :app-android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=example.livematch.android.MainActivityActionDispatchTest --no-daemon`, with the listener registered **before** `setDocument` (the WU8 bug, restored temporarily) | FAILED: `expected:<SET_REMINDER> but was:<null>` — reproduces the exact defect. |
| GREEN | Same command, with the fixed order | PASSED. |
| Full suite | `./gradlew test :app-android:testDebugUnitTest --no-daemon` | PASSED (all JVM/unit tests, no regressions). |

`MainActivityActionDispatchTest` (`app-android/src/androidTest/.../MainActivityActionDispatchTest.kt`)
launches the real `MainActivity`, finds the live `ClickAreaView` in the view tree, calls
`performClick()`, and asserts `MainActivity.lastDispatchedAction == ActionId.SET_REMINDER` — a
new internal (`private set`) property added to `MainActivity` purely so tests can observe the
dispatch outcome without parsing logcat.

## Device evidence

- Device: Pixel_9 / API 35, fingerprint-verified
  (`google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug/dev-keys`) —
  `docs/evidence/wu09/device-identity.txt`.
- Cold install + launch: `docs/evidence/wu09/install.txt`, `docs/evidence/wu09/launch.txt`
  (`Status: ok`, `LaunchState: COLD`).
- **Real coordinate tap** (`adb shell input tap 300 560`, not just programmatic
  `performClick()`) produced `I/MainActivity: Accepted action: set_reminder`:
  `docs/evidence/wu09/tap-result.txt`.
- Before/after screenshots (`docs/evidence/wu09/before-tap.png`,
  `docs/evidence/wu09/after-tap.png`) are byte-identical — expected, since this button has no
  pressed-state visual styling in the document; the confirmation of dispatch is the logcat line
  above and the passing instrumentation test, not a visual diff.
- Emulator stopped after evidence capture.

## Conclusion

The action-dispatch thesis (spec section 6.3 / thesis #3, "host retains control of actions") is
now **demonstrated end-to-end** for `set_reminder` in the Scheduled phase: a real on-screen tap
on a `RemoteComposeWriter.addClickArea`-authored button reaches `MainActivity`, resolves through
`ActionId`, and is checked against `ActionAllowlist` before any (currently non-existent) domain
effect would run. WU8's action-catalog/allowlist logic (kept unchanged) was correct all along;
the gap was purely in host wiring order.

No git commits, pushes, or PRs — building on top of root commit `15455ff`, leaving this work
(and WU8's) uncommitted for review, same as every prior work unit before that one.

## Deferred (still not built)

Envelope signing beyond the existing `SigningBoundary`, `RemoteUiRepository`/cache/network
validation, test-server, `open_match`/`open_highlight`/`open_summary` wiring (only
`set_reminder` has a real click area today), and any actual "set a reminder" domain effect
(the host currently only logs/records acceptance, per spec 6.3's "host validates before
acting" — executing the effect is separate, later work).

## Incidental fix while re-running the full suite

`ScheduledFixturePlaybackTest.EXPECTED_SHA256` still hardcoded the pre-WU8 fixture hash
(`d46ee2a7...`); WU8 regenerated `scheduled-match.rc` (adding the "Remind me" button) and
updated `fixtures/scheduled-match.manifest` but missed this test constant, so
`connectedDebugAndroidTest` failed on an unrelated, pre-existing staleness the moment the full
suite was run. Updated the constant to the current, already-verified hash
(`2f84de7c0163431ba53523d07371e67826864312f7ddd3e14e3df247d7406ff4`) rather than leave a known
failure in place.

## Size

4 files touched (`MainActivity.kt`: reorder + one new internal property; 1 new test file;
1 one-line stale-hash fix; this evidence report + `docs/evidence/wu09/`). ~16 authored source
lines. One cohesive slice.
