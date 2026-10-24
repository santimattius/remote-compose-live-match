# WU12 — Versioned signed fixture envelopes

## Scope and framing

| Field | Encoding |
| --- | --- |
| magic | Four literal bytes: `RCE!` |
| codec version | Big-endian signed 32-bit integer: `1` |
| numeric metadata | Big-endian signed 32-bit (`schemaVersion`, `minHostVersion`) or signed 64-bit (`revision`, timestamps) |
| text fields | Big-endian signed 32-bit length, then bounded strict UTF-8 bytes |
| signature | Big-endian signed 32-bit length, then bounded UTF-8 Base64 text |
| document | Big-endian signed 32-bit length, then bounded raw `.rc` bytes |

The decoder rejects unknown magic/version, negative or over-limit lengths before allocating,
truncation, invalid UTF-8, a total input larger than `1,041,024` bytes, and trailing bytes.

## Test evidence

- RED: `./gradlew :contract:test :creator-jvm:test --rerun-tasks` failed at
  `RemoteUiEnvelopeCodecTest` with `Unresolved reference 'RemoteUiEnvelopeCodec'` before codec
  implementation.

## Scheduled signed-fixture publication

The Scheduled generator now publishes a signed `scheduled-match.rce`, preserves the existing
`scheduled-match.rc` bytes exactly, and records envelope metadata and the `.rce` SHA-256 in the
Scheduled manifest. Its `--check` path verifies the committed raw document, decodes the envelope,
validates its embedded-key signature at the fixed fixture time, verifies metadata, and compares the
complete manifest.

Scheduled envelope metadata is stable: document ID `scheduled-match`, revision `1`, match ID
`scheduled-2027-01`, minimum host version `1`, creation time `1700000000`, and expiry
`4102444800`.

## Verification evidence

- `./gradlew :creator-jvm:test --tests example.livematch.creator.ScheduledFixtureGeneratorTest --rerun-tasks`
  passed (6 actionable tasks). The test proves deterministic `.rce` output, public-key validation,
  fixed metadata, and exact raw `.rc`-byte publication using an in-memory Ed25519 keypair.
- `./gradlew :creator-jvm:checkScheduledFixture --rerun-tasks` passed (4 actionable tasks).
- The committed raw `.rc` SHA-256 is
  `2f84de7c0163431ba53523d07371e67826864312f7ddd3e14e3df247d7406ff4`; the signed `.rce`
  SHA-256 is `603f4fc6b27ceeb3c7dee6d4da96f5631448b2657f69befb6150bad709b89719`.

## Rollback boundary

Revert this child by removing the Scheduled publisher seam, its signed artifact and manifest fields,
and returning the Scheduled generator to raw fixture publication. Preserve the pre-existing raw
`scheduled-match.rc` bytes.

## Live and Finished signed-fixture publication

The Live and Finished generators now use the existing signed-fixture publisher to publish
`live-match.rce` and `finished-match.rce`. Each keeps its established raw `.rc` bytes unchanged,
records envelope metadata and the `.rce` SHA-256 in its manifest, and verifies the committed
artifact, fixed metadata, signature, and embedded raw bytes through `--check`.

| Fixture | document ID | match ID | revision | expiry |
| --- | --- | --- | --- | --- |
| Live | `live-match` | `live-2027-01` | `1` | `4102444800` |
| Finished | `finished-match` | `finished-2027-01` | `1` | `4102444800` |

## Live and Finished verification evidence

- RED: `./gradlew :creator-jvm:test --tests example.livematch.creator.LiveFixtureGeneratorTest --tests example.livematch.creator.FinishedFixtureGeneratorTest --rerun-tasks`
  failed before implementation because both generators lacked `fixture()`.
- GREEN: the same focused test command passed after implementation. Each test uses an in-memory
  Ed25519 keypair and proves deterministic `.rce` publication, public-key validation, fixed
  metadata, and exact inner `.rc` preservation.
- Fixture checks: `./gradlew :creator-jvm:checkLiveFixture :creator-jvm:checkFinishedFixture --rerun-tasks`
  passed. Each check decodes the `.rce`, validates its embedded-key signature at the fixed fixture
  time, checks metadata and exact inner raw bytes, and compares the complete manifest.
- The raw `.rc` SHA-256 values are
  `bdf280c864792c39108dcd094e67047a689e038cdb5ef2f7711d331c7a71d6b5` (Live) and
  `92a920879765919d1933ce4adef846efc317f94c130fbc5199b288ac6ab52d03` (Finished). The signed
  `.rce` SHA-256 values are `c71a9e40412fd3f529942e5650213a3e2a5278c71de9826d4830730a0f0e21e4`
  (Live) and `1b6d099728a06268ba97ccbea2c80baabb8c194358557339a8fbf539dd177aa2` (Finished).

## Live and Finished rollback boundary

To revert this Live/Finished child, return only those two generators to raw fixture publication,
remove their `.rce` artifacts and signed-manifest fields, and preserve both raw `.rc` byte streams.
Leave the shared publisher and Scheduled signed fixture unchanged.

## Final Android/device evidence

On a fresh Pixel_9/API 35 run, `EnvelopeDocumentLoaderInstrumentationTest` passed its three
public-boundary cases: the packaged Scheduled envelope invokes the load callback once; a modified
document rejects with `HASH_MISMATCH` and zero callbacks; and a modified signature rejects with
`INVALID_SIGNATURE` and zero callbacks. The complete connected Android suite passed all eight
cases on Pixel_9/API 35. An already-connected Pixel_10_Pro/Android 16 also passed the same three
focused and eight suite cases.

The final managed JVM/Android unit run and all three signed-fixture checks passed. The retained
production screenshot `docs/evidence/wu12/valid-envelope-render.png` has SHA-256
`200842f94ea99fe266f7ab604ade04f2250477ca2bb18f7298b35f8a5690780c`; visual inspection
confirms the navy Scheduled card with Orcas, Tigers, kickoff value, and the Remind me action.

## Final rollback boundary

Revert only this final child to remove the device instrumentation test, Android/device evidence,
screenshot, and WU12 status/traceability updates. Keep the prior signed fixture artifacts,
raw `.rc` byte streams, publisher, Android validation boundary, and host integration unchanged.
