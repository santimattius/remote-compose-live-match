# Envelope signing keys

`RemoteUiEnvelope` documents are signed with Ed25519 (`contract/EnvelopePublicKey.kt`,
`creator-jvm/EnvelopeSigner.kt`). Per spec section 7, "Ningún secreto de firma debe formar
parte del repositorio" - the private key must never be committed.

- The **public** key is safe to commit and is embedded directly as a base64 constant in
  `contract/src/main/kotlin/example/livematch/contract/EnvelopePublicKey.kt`
  (`PRODUCTION_PUBLIC_KEY_BASE64`). The host verifies signatures against it.
- The **private** key lives only in this `keys/` directory as local, git-ignored `.pem` files
  (`.gitignore` excludes `keys/*.pem`), and is read by fixture generation from the
  `LIVE_MATCH_SIGNING_PRIVATE_KEY` environment variable (base64-encoded PKCS8 DER), never from a
  path baked into source. A real deployment would keep this in a KMS/HSM instead of a local file.

## Generating a local dev keypair

```sh
mkdir -p keys
openssl genpkey -algorithm ed25519 -out keys/envelope-signing-private-key.pem
openssl pkey -in keys/envelope-signing-private-key.pem -pubout -out keys/envelope-signing-public-key.pem
```

## Exporting the private key for fixture generation

```sh
export LIVE_MATCH_SIGNING_PRIVATE_KEY="$(openssl pkey -in keys/envelope-signing-private-key.pem -outform DER | base64 | tr -d '\n')"
```

## Getting the public key for `EnvelopePublicKey.PRODUCTION_PUBLIC_KEY_BASE64`

```sh
openssl pkey -in keys/envelope-signing-private-key.pem -pubout -outform DER | base64 | tr -d '\n'
```

Paste the output as `PRODUCTION_PUBLIC_KEY_BASE64` in `EnvelopePublicKey.kt`. Whoever generates a
new keypair must update that constant, or every existing signed fixture stops validating.
