# AndroidX Remote Compose alpha learnings

This project uses the pre-1.0 `androidx.compose.remote` stack, currently pinned to
`1.0.0-alpha22`. The first sections describe the current public capture path. The later sections
that mention the JVM writer, `ClickAreaView` or `drawTextAnchored` come from the alpha19 creators,
which have been removed, and are kept as history. This document keeps only durable implementation
and diagnostic lessons that should survive the experiment. It is intentionally self-contained: generated evidence, work-unit
receipts, screenshots, and internal task records are not part of the maintained documentation.

## Capture on the device with the public API

The backend signs match data, not a Remote Compose document. Android validates the envelope,
decodes the data and captures it into a document with `captureSingleRemoteDocument(Context, ...)`
and `@RemoteComposable` content, so the creation side needs an Android `Context`. `creator-jvm`
only encodes and signs. `remote-creation-compose:1.0.0-alpha22` declares minSdk 29 in its manifest, so the app
does too; devices on API 26-28 cannot install it. The release notes say the default minSdk is 24
since alpha17, which does not match that manifest. `remote-player-view` and `remote-player-core`
declare 26, so the player alone would not require 29.

alpha22 also exposes a `captureSingleRemoteDocument` overload without a `Context`, and it was
evaluated. `CaptureApiEvidenceTest` only asserts that a `Context` overload exists.

- **It is not usable from a pure JVM module.** `remote-creation-compose` is published only as an
  Android AAR, so a Gradle JVM module cannot resolve it (`library elements 'aar'… the consumer
  needed class files`). `remote-core`, `remote-creation-core` and `remote-creation-jvm` are plain JARs (the low-level layer); the other artifacts were not checked.
- **It is reachable with public API.** The function is public. `RemoteCreationDisplayInfo`'s
  constructor and `NoOpPlatformImageProvider` are `internal`, but the factory
  `RemoteCreationDisplayInfo(width, height, dpi, fontScale, …)`, `createProfile()` and the public
  `PlatformImageProvider` interface are enough.
- **It runs in a JVM unit test, with caveats.** With `unitTests.isReturnDefaultValues = true` the
  Scheduled and Finished documents were created. The Live document needs real Android ICU: the
  library converts `toRemoteString(DecimalFormat)` to `android.icu.text.DecimalFormatSymbols`
  even when a `java.text.DecimalFormat` is passed, and the stub returns `null`. It was created
  under Robolectric.
- **The documents play unchanged.** All three, created without a `Context`, loaded in the player
  with the same text, a ticking clock, an animated pulse and working host actions.
- **A clock anchored at creation inherits clock skew.** Created on one machine and played on
  another, the clock showed 66:56 where it should have shown at least 67:12.

The project keeps capturing on the device because creating documents elsewhere is an undocumented
use that depends on a test runtime and an alpha library. The structure of each state therefore
lives in the host, and only the match data travels without an app release.

A candidate must compile before it replaces the cached match. Promoting it first let a valid
payload that failed to capture overwrite the last known good one.

## Use the id-based host action

`hostAction(name)` creates a named action. In this project a tap on it never reached
`RemoteComposePlayer.addIdActionListener`, and no host log appeared. `hostAction(id, metadata)`
creates a `HostIdAction`, which does reach the listener as `onAction(id, metadata)`. Use the
id-based overload and put the wire value in the metadata.

Assign the numeric ids explicitly (`HostActionIds`). Deriving them from enum ordinals would change
the ids baked into cached documents whenever the enum is reordered.

## Host time variables

Two public variables behave differently from what their names suggest.

- `RemoteTimeVariables.animationTime` did not make the player repaint. Two screenshots three
  seconds apart of a document driven by it were pixel-identical.
- `RemoteTimeVariables.continuousSeconds` does repaint, but it is not time elapsed since the
  document loaded. In alpha22, `RemoteClock.TimeSnapshot.getContinuousSeconds()` is
  `minute * 60 + second + millisOfSecond * 0.001`: the seconds elapsed within the current hour of the
  host clock, which returns to zero every hour. This explains a clock built from it that read 83:41 for
  a match that starts at 67:12, an excess of 989 seconds (16 min 29 s into the hour); that value was not
  measured again after reading the bytecode. Anything computed from it as elapsed time would jump back
  at the top of every hour. Its fractional part is continuous, which is why it is safe for the pulse.

The live clock therefore anchors `RemoteTimeVariables.epochSecond` to the instant the document is
captured, and `continuousSeconds` drives the pulse. The clock counts from capture, not from the
real start of the match, so a cached snapshot shows its old clock advancing from the next launch.

## Keyboard access

On the Pixel 9 emulator (API 35), TAB and the D-pad keys gave keyboard focus to nothing on the Live card, as read from
the `focused` attribute that `uiautomator` reports. The same key events do reach the app: on the local message that
appears when there is no match, TAB followed by ENTER activates Retry, and ENTER alone does nothing, which a device
test pins. So the launcher's basic interaction works without a touch screen, and the card's own actions have no
keyboard path. The host's screen reader actions are accessibility actions, not key handlers. Why the player takes no
keyboard focus, and whether it can be made to, was not investigated.

## There is no reduced-motion variable

Searching the public classes of `remote-creation-compose`, `remote-core` and `remote-player-view`
alpha22 found no reduced-motion or animation-scale variable, so the document cannot branch on it.
Because the document is captured on the device, the host resolves the preference when it captures
(`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) and keeps the LIVE indicator opaque. A setting
change applies at the next capture, not to a document that is already playing. The clock keeps
advancing because it is information, not decoration.

## Test public-API documents through accessibility nodes

Documents captured with the public API create no `ClickAreaView` children. The player exposes
virtual accessibility nodes, and instrumentation locates and taps them.

- `findAccessibilityNodeInfosByText` did not find the player's nodes, even though they were in the
  tree. Walk the tree by hand and match `node.text`.
- Wait until a node's bounds are identical on two consecutive reads before tapping. A tap computed
  while the window was still resizing landed on stale coordinates and the action was lost.
- `wm size` changes the real window size for layout tests. Restore it in `@After` and let the
  display settle, or the next test launches into a resizing window.
- To prove a visual claim, sample pixels and add a positive control. The reduced-motion test counts
  distinct frames of the LIVE indicator and also asserts that it pulses when animations are on.

## What TalkBack focuses inside the player

The accessibility tree that `UiAutomation` reads is not what TalkBack focuses, so tests that only read the tree cannot
see how a screen reader reaches a card. This section records what was measured with TalkBack running on a Pixel 9
emulator (API 35), including a correction of an earlier version of this document.

- **With the container's content description, TalkBack's first focus lands on the whole container.** The card's
  container carried a description ("Live match. Orcas 2, Tigers 1…"). TalkBack's first focus was a box the size of
  the player, and a touch exactly on "Open match" left it there.
- **Without that description, the first focus lands on a child.** Removing only the container's description made the
  first focus land on "LIVE". So the container's description is the variable that changed the first focus target.
- **`important = false` is not the cause.** Every node inside the card is published with
  `isImportantForAccessibility = false` and `isScreenReaderFocusable = false`. An earlier version of this document
  concluded that TalkBack therefore ignores them. That was inferred and not tested, and a control disproved it: a
  plain custom view with three virtual nodes from `ExploreByTouchHelper` publishes the same flags, and TalkBack's
  first focus landed on its first child.
- **`role = Role.Button` changed neither flag.** The creation API only offers content description, role, text, state
  description and enabled, plus `mergeDescendants`; it has no control over importance, focus or traversal order.

Not verified, and why it matters: whether TalkBack can move from the first focus to the later children and activate
them. Swipes sent with `adb input` moved the first focus but did not advance it afterwards, not even in the control
view, and `performAction(ACTION_ACCESSIBILITY_FOCUS)` from `UiAutomation` returned false for every node, so
navigation and native activation could not be tested. The earlier statement that the actions are unreachable was
therefore stronger than the evidence.

Because reachability could not be confirmed, the host also offers each card's actions as accessibility actions on the
player view (`exposeScreenReaderActions`), routed through the same allowlisted dispatcher as a tap, and a device test
performs them with TalkBack running. Dropping the container's description would likely make the children the first
focus target, but it would also lose the sentence that reads the score as a phrase, so it was not applied. The
"Show local match details" toggle changes state inside the document, so the host cannot offer it.

Also not verified: what TalkBack says aloud, keyboard or D-pad focus, other screen readers, and any API level other
than 35.

## Treat document distribution and trust as application-layer concerns

Remote Compose provides document encoding and on-device rendering. It does not provide a signed
distribution envelope, schema version, expiration policy, or host-version compatibility gate.
Those controls belong outside the Remote Compose document.

This project wraps the signed payload in a bounded, versioned binary envelope. Since alpha22 that
payload is match data, not document bytes; the older text below describes document bytes. Decode lengths,
UTF-8, truncation, trailing bytes, metadata, expiration, host compatibility, and the Ed25519
signature are validated before any bytes reach `RemoteComposePlayer`. Rejected input must produce
no rendering callback or document mutation.

JVM signing and verification can use standard JCA APIs. Android runtime compatibility must be
proved separately: the platform provider on Pixel 9/API 35 could not reliably import and verify
the committed arbitrary Ed25519 public key, and Conscrypt 2.5.2 exposed neither the required
`KeyFactory.Ed25519` nor `Signature.Ed25519` implementation. The Android path therefore isolates
Tink's Ed25519 verifier behind `AndroidEnvelopeVerifier`, while preserving the same key, canonical
bytes, signature, and wire format. Keep an Android known-answer test and rerun it whenever the
crypto dependency or provider configuration changes.

## Load interactive documents before attachment (legacy writer)

Loading a document after `RemoteComposePlayer` has already completed an attach/layout pass can
register click areas in the document model without materializing their `ClickAreaView` children.
For interactive documents, load synchronously before returning the player from the `AndroidView`
factory. Any future in-place document transition, such as Scheduled to Live, needs an explicit
device test rather than assuming click views will be rebuilt.

## Register action listeners after loading the document

`RemoteComposePlayer.setDocument` replaces the internal `CoreDocument`. Registering an
`IdActionListener` before loading attaches it to the document that is about to be discarded, so
taps are silently lost. Always load or set the document first, then register listeners on the live
document.

`RemoteComposeWriter.addClickArea` worked with the alpha19 immediate-mode creators, which are
removed. Documents captured with the public API use `hostAction(id, metadata)` instead.

## Accessibility inspection is not a View-tree inspection (legacy writer)

`uiautomator dump` walks the accessibility tree and can omit real `ClickAreaView` children. When
diagnosing whether click targets were created, inspect the live View hierarchy through a debug
accessor or bounded reflection. Use accessibility output only for accessibility claims.

## Procedural coordinates are physical pixels (legacy writer)

`RemoteComposePlayer` has no scale-to-fit API for documents authored with absolute procedural
coordinates. The player paints those coordinates 1:1 into its measured canvas. A `320x180`
viewport therefore remains a small `320x180` region on a larger display rather than becoming an
abstract design size.

Choose viewport dimensions for the target device class, or move to a relative layout model and
verify its behavior separately. The public-API content uses `RemoteConfiguration.windowWidth`, and
`AdaptiveLayoutTest` shows its padding scaling at three window sizes.

## `drawTextAnchored` needs an explicit horizontal anchor (legacy writer)

With `panX=0f`, `drawTextAnchored` centers text on the supplied x-coordinate, so long strings near
the left edge clip. In alpha19, `panX=-1f` produces a flush-left anchor, `0f` centers, and `+1f`
right-aligns. The creators use `panX=-1f` for left-aligned content and set paint colors explicitly
before every background or text group.

Do not rely on an implicit default paint color. Missing `setColor` calls can make otherwise valid
text appear absent and will change canonical fixture bytes once corrected.

## System chrome can hide a correctly rendered document

A default opaque `ActionBar` can cover content drawn at the player's origin even though the player
is measured and painting correctly. Hide the ActionBar or use a no-action-bar theme before blaming
the document format or player.

System-bar glyphs can also overlap edge-to-edge content. Apply
`Modifier.windowInsetsPadding(WindowInsets.systemBars)` to the `AndroidView` that hosts the player
while allowing the outer surface to remain edge-to-edge.

When a document appears blank, inspect compositing chrome and insets before changing document
encoding or library versions.

## Remote Compose still uses a regular Android View

`RemoteComposePlayer` is a `View`/`ViewGroup`, not a Compose UI composable. A Compose host must use
`AndroidView`. After that migration, tests must search the nested View tree instead of assuming the
player is the first child of `android.R.id.content`.

Dependency floors matter. `activity-compose:1.13.0` requires compileSdk 36 and AGP 8.9.1+, while
this project remains on compileSdk 35 and AGP 8.8.2. `activity-compose:1.10.1` is the compatible
choice for the current baseline. Check dependency metadata before selecting the latest stable
AndroidX release.

## Version bumps and warnings need behavioral proof

The alpha18-to-alpha19 upgrade required no source changes and produced byte-identical procedural
fixtures. Do not expect a prerelease version bump to fix unrelated rendering behavior; validate the
specific code path on a real target device.

The legacy four-argument writer path logs `System.err: error unknown Paint Type 14`. It occurs on
both alpha18 and alpha19 and does not correlate with rendering failure. The six-argument API-6 path
does not log it. Treat this warning as cosmetic unless a new reproducible symptom proves otherwise.

## Verify device identity, not only the adb serial

An adb serial such as `emulator-5554` is reusable and does not identify an AVD or API level. Before
accepting device results, verify the AVD name, SDK level, release, and build fingerprint. This
prevents an already-running emulator from silently invalidating a controlled comparison.
