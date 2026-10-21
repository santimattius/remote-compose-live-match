# WU12 Android envelope validation

## Bounded Pixel_9/API 35 diagnostic

The valid committed Scheduled envelope still returns `Rejected(INVALID_SIGNATURE)`; both
message- and signature-tampered envelopes still reject before the load callback.

A focused instrumentation diagnostic on `Pixel_9(AVD) - 15` (the requested running
Pixel_9/API 35) isolated the explicit-provider failure. The deliberately failing,
test-only observation was:

```text
keyFactoryProvider=Conscrypt; keyImport=not-run; keyFactoryException=NoSuchAlgorithmException:no such algorithm: Ed25519 for provider Conscrypt; signatureProvider=Conscrypt; signatureException=NoSuchAlgorithmException:no such algorithm: Ed25519 for provider Conscrypt; initVerify=not-run; verify=not-run; canonicalSha256MatchesDesktop=true; publicKeyBytes=44; signatureBytes=64
```

No key, signature, or document bytes were emitted. The committed fixture's Android
canonical-signing-input SHA-256 matches the desktop known answer
`8e173d3d345c05fae64f3bb41056bc39dccd870fbf23b58f08cd13fd3c1a2fd5`.

The exact focused invocation, run through the required compact wrapper, was:

```text
./gradlew :app-android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=example.livematch.android.EnvelopeDocumentLoaderInstrumentationTest --no-daemon
```

It compiled and executed four tests. The two tampered-envelope tests passed. The valid
envelope test failed with `expected:<Loaded> but was:<Rejected(reason=INVALID_SIGNATURE)>`;
the intentional diagnostic test failed with the observation above.

## Conclusion and bounded correction

`Conscrypt.newProvider()` 2.5.2 is not an Ed25519 provider on this Android runtime: neither
`KeyFactory.Ed25519` nor `Signature.Ed25519` can be constructed, so public-key import,
`initVerify`, and `verify` never run. The mismatch is provider capability, not fixture
canonicalization, key/signature encoding lengths, or a false signature-verification result.

The correction removes Conscrypt and selects Google `tink-android` 1.18.0 without global
provider registration. Android injects a Tink verifier through the contract's explicit
signature-verifier lambda; JVM callers retain the default JCA verification path and JVM signing
is unchanged. The committed SPKI key is converted to Tink's raw 32-byte Ed25519 form only after
exact DER prefix and length validation.

Tink's supported `PublicKeyVerify` interface normally receives a key from a Tink keyset. The
wire contract intentionally carries a committed SPKI public key rather than a Tink keyset, so
`AndroidEnvelopeVerifier` alone bridges it with `com.google.crypto.tink.subtle.Ed25519Verify`
as a `PublicKeyVerify`. `subtle` is not Tink's keyset-level API-stability surface; isolating
that small raw-key bridge keeps its upgrade risk out of the contract and all other Android code.
The Ed25519 envelope wire format, key, signatures, canonical bytes, and JVM signing stay
unchanged.

## Device GREEN and visual evidence

The final focused run on `Pixel_9(AVD)` / Android 15 / API 35 executed three public-boundary
instrumentation tests: the exact packaged Scheduled envelope loaded once, a modified document
was rejected with `HASH_MISMATCH` and zero callbacks, and a modified signature was rejected with
`INVALID_SIGNATURE` and zero callbacks. Result: `BUILD SUCCESSFUL`, 3 tests finished.

The complete Android instrumentation suite then passed on the same device: 8 tests finished,
`BUILD SUCCESSFUL`. A production `MainActivity` launch loaded `scheduled-match.rce` through the
same validation boundary and visibly rendered the navy match card, Orcas/Tigers text, kickoff
time, and Remind me action. Screenshot:
`docs/evidence/wu12/valid-envelope-render.png`, SHA-256
`200842f94ea99fe266f7ab604ade04f2250477ca2bb18f7298b35f8a5690780c`.

The final JVM/Android unit regression plus all three signed-fixture checks passed with 58 tasks
executed and no warnings. The compact Gradle workflow removed only its wrapper-owned logs.

## Durable coverage

The instrumentation test retains a known-answer assertion for the Scheduled envelope's
canonical-signing-input SHA-256. The provider diagnostics were removed after the bounded run.
The production correction is isolated to the explicit Android Tink verifier; the contract's
JVM verifier remains unchanged by default.
