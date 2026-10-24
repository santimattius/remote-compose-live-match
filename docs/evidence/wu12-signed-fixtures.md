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
