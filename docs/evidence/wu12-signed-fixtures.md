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
