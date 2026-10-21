# WU12 — Versioned signed fixture envelopes

## Scope and framing

T2 implements the JVM publishing half of WU12 for Scheduled, Live, and Finished. Each generated
`.rce` asset is one dependency-free binary `RemoteUiEnvelope`; the existing raw `.rc` assets stay
in place temporarily because Android still consumes them until T3.

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

## Fixed demo metadata

All three fixtures use schema/host version `1`, revision `1`, creation time `1700000000`, and
expiry `4102444800` (2100-01-01 UTC). Their stable document/match IDs are:

| Fixture | documentId | matchId |
| --- | --- | --- |
| Scheduled | `scheduled-match` | `scheduled-2027-01` |
| Live | `live-match` | `live-2027-01` |
| Finished | `finished-match` | `finished-2027-01` |

This long-lived expiry is **fixture policy only**: it makes committed demonstrations reproducible.
It is not a network freshness, repository, cache, ETag, or production TTL policy.

## Test evidence

- RED: `./gradlew :contract:test :creator-jvm:test --rerun-tasks` failed at
  `RemoteUiEnvelopeCodecTest` with `Unresolved reference 'RemoteUiEnvelopeCodec'` before codec
  implementation.
- GREEN (focused): `./gradlew :contract:test :creator-jvm:test --rerun-tasks` passed after the
  codec and shared publisher implementation. The contract tests prove deterministic round-trip,
  magic/version, truncation, negative/oversized length, strict UTF-8, total-size, and trailing-byte rejection.
  Scheduled/Live/Finished tests each generate an in-memory Ed25519 keypair and prove deterministic
  publication, public-key validation, stable metadata, and exact inner `.rc` preservation.
- Publication: the ignored private key was consumed without printing key material after its derived
  public key was compared with the committed verification key. All three Gradle generation tasks
  completed successfully (6 actionable tasks: 3 executed, 3 up-to-date).
- PRESERVE: all three raw `.rc` hashes remained byte-for-byte unchanged.
- Fixture checks: `./gradlew :creator-jvm:checkScheduledFixture
  :creator-jvm:checkLiveFixture :creator-jvm:checkFinishedFixture --rerun-tasks` passed through
  the compact Gradle runner (6 actionable tasks executed). Each check decodes the `.rce`, validates
  its signature with the embedded public key, checks fixed metadata and exact inner `.rc` bytes,
  and compares the complete manifest.

## Hashes and publication status

The raw-document hashes remain the established deterministic fixture hashes:

| Fixture | raw `.rc` SHA-256 | `.rce` SHA-256 |
| --- | --- | --- |
| Scheduled | `2f84de7c0163431ba53523d07371e67826864312f7ddd3e14e3df247d7406ff4` | `603f4fc6b27ceeb3c7dee6d4da96f5631448b2657f69befb6150bad709b89719` |
| Live | `bdf280c864792c39108dcd094e67047a689e038cdb5ef2f7711d331c7a71d6b5` | `c71a9e40412fd3f529942e5650213a3e2a5278c71de9826d4830730a0f0e21e4` |
| Finished | `92a920879765919d1933ce4adef846efc317f94c130fbc5199b288ac6ab52d03` | `1b6d099728a06268ba97ccbea2c80baabb8c194358557339a8fbf539dd177aa2` |

## Rollback boundary

Rollback T2 by removing the codec, signed publisher, `.rce` assets, and expanded manifest fields;
preserve the existing raw `.rc` assets and generators. T3 is the separate boundary that changes
Android asset consumption and enforces validation before player loading.
