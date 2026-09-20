package example.livematch.contract

import java.security.MessageDigest

enum class SigningBoundary { UNSIGNED, EXTERNAL_SIGNATURE_REQUIRED }

/** SHA-256 is lowercase hexadecimal over exactly [RemoteDocumentPayload.bytes], not this envelope. */
data class PayloadEnvelope(
    val sha256: String,
    val signingBoundary: SigningBoundary = SigningBoundary.UNSIGNED,
) {
    val hasSignature: Boolean
        get() = false

    fun verifies(payload: RemoteDocumentPayload): Boolean = sha256 == hash(payload.bytes)

    companion object {
        fun forPayload(
            payload: RemoteDocumentPayload,
            signingBoundary: SigningBoundary = SigningBoundary.UNSIGNED,
        ) = PayloadEnvelope(hash(payload.bytes), signingBoundary)

        private fun hash(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
