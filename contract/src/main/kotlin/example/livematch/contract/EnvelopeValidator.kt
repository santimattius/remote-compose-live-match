package example.livematch.contract

import java.security.MessageDigest

enum class EnvelopeRejectionReason {
    OVERSIZED,
    UNKNOWN_SCHEMA_VERSION,
    INCOMPATIBLE_HOST_VERSION,
    INCOHERENT_TIMESTAMPS,
    EXPIRED,
    HASH_MISMATCH,
    INVALID_SIGNATURE,
}

typealias EnvelopeSignatureVerifier =
    (signedBytes: ByteArray, signatureBase64: String, publicKeyBase64: String) -> Boolean

sealed class EnvelopeValidationResult {
    object Valid : EnvelopeValidationResult()

    data class Rejected(
        val reason: EnvelopeRejectionReason,
    ) : EnvelopeValidationResult()
}

/**
 * Implements the subset of spec section 9's checks that don't require the not-yet-built
 * repository/cache layer. Deferred to a later work unit: external-resource policy,
 * documentId/matchId/revision cross-checks against prior state, and refresh/retry rate limits.
 *
 * Checks run cheapest-and-structural first, per spec 9 ("validar antes de decodificar") -
 * size, then schema/version, then timestamps, then the cryptographic hash and signature.
 */
object EnvelopeValidator {
    const val MAX_ENVELOPE_BYTES: Int = 1_000_000
    const val CURRENT_SCHEMA_VERSION: Int = 1
    const val CURRENT_HOST_VERSION: Int = 1

    fun validate(
        envelope: RemoteUiEnvelope,
        nowEpochSeconds: Long,
        currentHostVersion: Int = CURRENT_HOST_VERSION,
        publicKeyBase64: String = EnvelopePublicKey.PRODUCTION_PUBLIC_KEY_BASE64,
        signatureVerifier: EnvelopeSignatureVerifier = EnvelopePublicKey::verify,
    ): EnvelopeValidationResult {
        if (envelope.bytes.size > MAX_ENVELOPE_BYTES) {
            return EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.OVERSIZED)
        }
        if (envelope.schemaVersion != CURRENT_SCHEMA_VERSION) {
            return EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.UNKNOWN_SCHEMA_VERSION)
        }
        if (envelope.signatureAlgorithm != EnvelopePublicKey.ALGORITHM) {
            return EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.INVALID_SIGNATURE)
        }
        if (envelope.minHostVersion > currentHostVersion) {
            return EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.INCOMPATIBLE_HOST_VERSION)
        }
        if (envelope.expiresAtEpochSeconds <= envelope.createdAtEpochSeconds) {
            return EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.INCOHERENT_TIMESTAMPS)
        }
        if (nowEpochSeconds >= envelope.expiresAtEpochSeconds) {
            return EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.EXPIRED)
        }
        if (!envelope.contentSha256.equals(sha256Hex(envelope.bytes), ignoreCase = true)) {
            return EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.HASH_MISMATCH)
        }
        if (
            !signatureVerifier(
                envelope.canonicalSigningInput(),
                envelope.signature,
                publicKeyBase64,
            )
        ) {
            return EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.INVALID_SIGNATURE)
        }
        return EnvelopeValidationResult.Valid
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
