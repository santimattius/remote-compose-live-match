# AndroidX Remote Compose alpha learnings

This project uses the pre-1.0 `androidx.compose.remote` stack, currently pinned to
`1.0.0-alpha19`. This document keeps only durable implementation and diagnostic lessons that
should survive the experiment. It is intentionally self-contained: generated evidence, work-unit
receipts, screenshots, and internal task records are not part of the maintained documentation.

## Treat document distribution and trust as application-layer concerns

Remote Compose provides document encoding and on-device rendering. It does not provide a signed
distribution envelope, schema version, expiration policy, or host-version compatibility gate.
Those controls belong outside the Remote Compose document.

This project wraps the raw document bytes in a bounded, versioned binary envelope. Decode lengths,
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

## Load interactive documents before attachment

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

`RemoteComposeWriter.addClickArea` works with the project's immediate-mode authoring style; the
component/modifier DSL is not required for click delivery.

## Accessibility inspection is not a View-tree inspection

`uiautomator dump` walks the accessibility tree and can omit real `ClickAreaView` children. When
diagnosing whether click targets were created, inspect the live View hierarchy through a debug
accessor or bounded reflection. Use accessibility output only for accessibility claims.

## Procedural coordinates are physical pixels

`RemoteComposePlayer` has no scale-to-fit API for documents authored with absolute procedural
coordinates. The player paints those coordinates 1:1 into its measured canvas. A `320x180`
viewport therefore remains a small `320x180` region on a larger display rather than becoming an
abstract design size.

Choose viewport dimensions for the target device class, or move to a relative layout model and
verify its behavior separately. The current phone-oriented fixtures use a width aligned with the
Pixel 9 evidence baseline.

## `drawTextAnchored` needs an explicit horizontal anchor

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
