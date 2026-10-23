package example.livematch.contract

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Distribution envelope per spec section 6.2. Intentionally not a data class: [bytes] is a
 * ByteArray, and Kotlin's synthesized equals/hashCode would compare it by reference, not
 * content (same reasoning as [RemoteDocumentPayload]).
 */
class RemoteUiEnvelope(
    val schemaVersion: Int,
    val documentId: String,
    val revision: Long,
    val matchId: String,
    val minHostVersion: Int,
    val createdAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val contentSha256: String,
    val signatureAlgorithm: String,
    val signature: String,
    bytes: ByteArray,
) {
    private val contents: ByteArray = bytes.copyOf()

    val bytes: ByteArray
        get() = contents.copyOf()

    init {
        require(documentId.isNotBlank()) { "documentId must not be blank." }
        require(matchId.isNotBlank()) { "matchId must not be blank." }
        require(contentSha256.isNotBlank()) { "contentSha256 must not be blank." }
        require(signatureAlgorithm.isNotBlank()) { "signatureAlgorithm must not be blank." }
        require(signature.isNotBlank()) { "signature must not be blank." }
    }

    /** The exact bytes a signer signs and a validator verifies - excludes [bytes] itself, which is already committed via [contentSha256]. */
    fun canonicalSigningInput(): ByteArray =
        canonicalSigningInput(
            schemaVersion,
            documentId,
            revision,
            matchId,
            minHostVersion,
            createdAtEpochSeconds,
            expiresAtEpochSeconds,
            contentSha256,
            signatureAlgorithm,
        )

    companion object {
        fun canonicalSigningInput(
            schemaVersion: Int,
            documentId: String,
            revision: Long,
            matchId: String,
            minHostVersion: Int,
            createdAtEpochSeconds: Long,
            expiresAtEpochSeconds: Long,
            contentSha256: String,
            signatureAlgorithm: String,
        ): ByteArray {
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use {
                it.writeInt(schemaVersion)
                it.writeLengthPrefixedUtf8(documentId)
                it.writeLong(revision)
                it.writeLengthPrefixedUtf8(matchId)
                it.writeInt(minHostVersion)
                it.writeLong(createdAtEpochSeconds)
                it.writeLong(expiresAtEpochSeconds)
                it.writeLengthPrefixedUtf8(contentSha256)
                it.writeLengthPrefixedUtf8(signatureAlgorithm)
            }
            return output.toByteArray()
        }

        private fun DataOutputStream.writeLengthPrefixedUtf8(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }
    }
}
