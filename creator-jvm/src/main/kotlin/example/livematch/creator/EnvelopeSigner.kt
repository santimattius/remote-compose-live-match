package example.livematch.creator

import example.livematch.contract.EnvelopePublicKey
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.RemoteUiEnvelope
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

data class EnvelopeMetadataInput(
    val documentId: String,
    val revision: Long,
    val matchId: String,
    val minHostVersion: Int,
    val createdAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val schemaVersion: Int = EnvelopeValidator.CURRENT_SCHEMA_VERSION,
)

/** Runs entirely on the JVM creation pipeline - the private key never reaches the Android host. */
object EnvelopeSigner {
    fun sign(
        payload: RemoteDocumentPayload,
        metadata: EnvelopeMetadataInput,
        privateKey: PrivateKey,
    ): RemoteUiEnvelope {
        val bytes = payload.bytes
        val contentSha256 = sha256Hex(bytes)
        val signatureAlgorithm = EnvelopePublicKey.ALGORITHM
        val signingInput =
            RemoteUiEnvelope.canonicalSigningInput(
                metadata.schemaVersion,
                metadata.documentId,
                metadata.revision,
                metadata.matchId,
                metadata.minHostVersion,
                metadata.createdAtEpochSeconds,
                metadata.expiresAtEpochSeconds,
                contentSha256,
                signatureAlgorithm,
            )
        val signatureBase64 =
            Signature.getInstance(signatureAlgorithm).run {
                initSign(privateKey)
                update(signingInput)
                Base64.getEncoder().encodeToString(sign())
            }
        return RemoteUiEnvelope(
            schemaVersion = metadata.schemaVersion,
            documentId = metadata.documentId,
            revision = metadata.revision,
            matchId = metadata.matchId,
            minHostVersion = metadata.minHostVersion,
            createdAtEpochSeconds = metadata.createdAtEpochSeconds,
            expiresAtEpochSeconds = metadata.expiresAtEpochSeconds,
            contentSha256 = contentSha256,
            signatureAlgorithm = signatureAlgorithm,
            signature = signatureBase64,
            bytes = bytes,
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
