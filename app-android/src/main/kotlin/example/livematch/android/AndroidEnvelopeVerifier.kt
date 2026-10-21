package example.livematch.android

import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.subtle.Ed25519Verify
import example.livematch.contract.EnvelopePublicKey
import example.livematch.contract.EnvelopeSignatureVerifier
import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.RemoteUiEnvelope
import java.security.GeneralSecurityException
import java.util.Base64

/** Android-specific Ed25519 verifier that does not depend on platform JCA provider selection. */
internal object AndroidEnvelopeVerifier {
    fun validate(
        envelope: RemoteUiEnvelope,
        nowEpochSeconds: Long,
    ): EnvelopeValidationResult =
        EnvelopeValidator.validate(
            envelope = envelope,
            nowEpochSeconds = nowEpochSeconds,
            signatureVerifier = tinkSignatureVerifier,
        )

    /**
     * Tink's supported [PublicKeyVerify] interface normally obtains keys from a Tink keyset.
     * This envelope contract deliberately stores an SPKI public key instead, so the bounded
     * raw-key bridge uses Tink's subtle Ed25519Verify implementation. That subtle API is not
     * Tink's keyset-level stability surface; keeping it here contains that tradeoff to Android.
     */
    private val tinkSignatureVerifier: EnvelopeSignatureVerifier = { signedBytes, signatureBase64, publicKeyBase64 ->
        try {
            val verifier: PublicKeyVerify = Ed25519Verify(EnvelopePublicKey.rawEd25519PublicKey(publicKeyBase64))
            verifier.verify(Base64.getDecoder().decode(signatureBase64), signedBytes)
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: GeneralSecurityException) {
            false
        }
    }
}
