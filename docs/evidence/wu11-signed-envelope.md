# WU11 — Signed envelope, JVM-level (signer + validator)

## Design decisions

### Signature algorithm: Ed25519

Chosen because `java.security` supports it natively since JDK 15 (this project targets JDK 17)
with no external dependency, and Ed25519 signing is deterministic by construction (unlike, say,
plain ECDSA, which needs RFC 6979 to be deterministic). Verified directly:

```
$ java EdTest
deterministic=true
verifies=true
```

(ad-hoc `javac`/`java` check against a throwaway keypair, confirming `Signature.getInstance("Ed25519")`
produces byte-identical signatures across two independent sign calls on the same JVM, and that a
signature made with a private key verifies against its matching public key.) The same property is
now asserted as a real unit test — see `EnvelopeSignerTest.signing the same payload and metadata
twice is byte-for-byte deterministic`.

### What gets signed

Not the raw document bytes directly — `RemoteUiEnvelope.canonicalSigningInput()` signs a
binary, ordered encoding: fixed-width numeric values and length-prefixed UTF-8 strings for
`schemaVersion`, `documentId`, `revision`, `matchId`, `minHostVersion`,
`createdAtEpochSeconds`, `expiresAtEpochSeconds`, `contentSha256`, and
`signatureAlgorithm`. This encoding is unambiguous even when strings contain delimiter-like
characters, and authenticates the algorithm selection. `contentSha256` already commits to the
document bytes, so this transitively covers `bytes` without hashing/signing a potentially large
payload twice. `EnvelopeValidatorTest.rejects a tampered payload even if the signature field is
unchanged` proves this catches a swapped-bytes attack: the hash check fails first because
`contentSha256` no longer matches the (unsigned) `bytes`. The signature value itself is excluded
because a signature cannot sign itself.

### Key management — no secret in the repository

Per spec section 7 ("Ningún secreto de firma debe formar parte del repositorio"):

- Generated a real Ed25519 keypair: `openssl genpkey -algorithm ed25519`.
- **Public key**: embedded as a source constant,
  `EnvelopePublicKey.PRODUCTION_PUBLIC_KEY_BASE64` (X.509/SPKI DER, base64) — safe to commit,
  used by `EnvelopeValidator`'s default parameter.
- **Private key**: written only to `keys/envelope-signing-private-key.pem`, which
  `.gitignore` now excludes (`keys/*.pem`). `EnvelopeSigningKeyLoader.loadFromEnv()` reads it
  from the `LIVE_MATCH_SIGNING_PRIVATE_KEY` environment variable (base64 PKCS8 DER) at fixture-
  generation time — never from a path baked into source. `keys/README.md` documents the exact
  `openssl` commands to regenerate a dev key and export it.
- Verified the key is actually excluded: `git add -n keys/ .gitignore` stages only
  `.gitignore` and `keys/README.md`, never the `.pem` files.

### `RemoteUiEnvelope` is a plain class, not a data class

Spec 6.2 shows it as a Kotlin `data class`, but it carries a `ByteArray` field (`bytes`).
Kotlin's synthesized `equals`/`hashCode` for a data class compares `ByteArray` fields by
*reference*, not content — the same trap `RemoteDocumentPayload` in this codebase already avoids
by being a plain class with a defensive-copy getter. `RemoteUiEnvelope` follows that existing,
established precedent instead of the spec's literal syntax.
