package example.livematch.contract

import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EnvelopeValidatorTest {
    private val keyPair = KeyPairGenerator.getInstance(EnvelopePublicKey.ALGORITHM).generateKeyPair()
    private val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    @Test
    fun `accepts a well-formed, correctly signed, unexpired envelope`() {
        val envelope = signedEnvelope()

        val result = EnvelopeValidator.validate(envelope, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertEquals(EnvelopeValidationResult.Valid, result)
    }

    @Test
    fun `rejects an oversized envelope before decoding`() {
        val envelope = signedEnvelope(bytes = ByteArray(EnvelopeValidator.MAX_ENVELOPE_BYTES + 1))

        val result = EnvelopeValidator.validate(envelope, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertRejected(result, EnvelopeRejectionReason.OVERSIZED)
    }

    @Test
    fun `rejects an unknown schema version`() {
        val envelope = signedEnvelope(schemaVersion = EnvelopeValidator.CURRENT_SCHEMA_VERSION + 1)

        val result = EnvelopeValidator.validate(envelope, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertRejected(result, EnvelopeRejectionReason.UNKNOWN_SCHEMA_VERSION)
    }

    @Test
    fun `rejects an incompatible minHostVersion`() {
        val envelope = signedEnvelope(minHostVersion = EnvelopeValidator.CURRENT_HOST_VERSION + 1)

        val result =
            EnvelopeValidator.validate(
                envelope,
                nowEpochSeconds = 1_500L,
                currentHostVersion = EnvelopeValidator.CURRENT_HOST_VERSION,
                publicKeyBase64 = publicKeyBase64,
            )

        assertRejected(result, EnvelopeRejectionReason.INCOMPATIBLE_HOST_VERSION)
    }

    @Test
    fun `rejects incoherent timestamps where expiry is not after creation`() {
        val envelope = signedEnvelope(createdAtEpochSeconds = 2_000L, expiresAtEpochSeconds = 2_000L)

        val result = EnvelopeValidator.validate(envelope, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertRejected(result, EnvelopeRejectionReason.INCOHERENT_TIMESTAMPS)
    }

    @Test
    fun `rejects an expired envelope with no cache to fall back to yet`() {
        val envelope = signedEnvelope(createdAtEpochSeconds = 1_000L, expiresAtEpochSeconds = 2_000L)

        val result = EnvelopeValidator.validate(envelope, nowEpochSeconds = 2_000L, publicKeyBase64 = publicKeyBase64)

        assertRejected(result, EnvelopeRejectionReason.EXPIRED)
    }

    @Test
    fun `rejects a content hash mismatch before any signature check`() {
        val envelope = signedEnvelope(contentSha256Override = "0".repeat(64))

        val result = EnvelopeValidator.validate(envelope, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertRejected(result, EnvelopeRejectionReason.HASH_MISMATCH)
    }

    @Test
    fun `passes canonical data to an injected signature verifier`() {
        val envelope = signedEnvelope()
        var verifierCalls = 0

        val result =
            EnvelopeValidator.validate(
                envelope = envelope,
                nowEpochSeconds = 1_500L,
                publicKeyBase64 = publicKeyBase64,
            ) { signedBytes, signatureBase64, keyBase64 ->
                verifierCalls += 1
                assertEquals(envelope.canonicalSigningInput().toList(), signedBytes.toList())
                assertEquals(envelope.signature, signatureBase64)
                assertEquals(publicKeyBase64, keyBase64)
                true
            }

        assertEquals(EnvelopeValidationResult.Valid, result)
        assertEquals(1, verifierCalls)
    }

    @Test
    fun `rejects an invalid signature`() {
        val otherKeyPair = KeyPairGenerator.getInstance(EnvelopePublicKey.ALGORITHM).generateKeyPair()
        val envelope = signedEnvelope(signingKey = otherKeyPair.private)

        val result = EnvelopeValidator.validate(envelope, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertRejected(result, EnvelopeRejectionReason.INVALID_SIGNATURE)
    }

    @Test
    fun `rejects a tampered payload even if the signature field is unchanged`() {
        val valid = signedEnvelope()
        val tampered =
            RemoteUiEnvelope(
                schemaVersion = valid.schemaVersion,
                documentId = valid.documentId,
                revision = valid.revision,
                matchId = valid.matchId,
                minHostVersion = valid.minHostVersion,
                createdAtEpochSeconds = valid.createdAtEpochSeconds,
                expiresAtEpochSeconds = valid.expiresAtEpochSeconds,
                contentSha256 = valid.contentSha256,
                signatureAlgorithm = valid.signatureAlgorithm,
                signature = valid.signature,
                bytes = byteArrayOf(9, 9, 9, 9),
            )

        val result = EnvelopeValidator.validate(tampered, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertRejected(result, EnvelopeRejectionReason.HASH_MISMATCH)
    }

    @Test
    fun `rejects a signed envelope whose signature algorithm is changed to none`() {
        val valid = signedEnvelope()
        val tampered =
            RemoteUiEnvelope(
                schemaVersion = valid.schemaVersion,
                documentId = valid.documentId,
                revision = valid.revision,
                matchId = valid.matchId,
                minHostVersion = valid.minHostVersion,
                createdAtEpochSeconds = valid.createdAtEpochSeconds,
                expiresAtEpochSeconds = valid.expiresAtEpochSeconds,
                contentSha256 = valid.contentSha256,
                signatureAlgorithm = "none",
                signature = valid.signature,
                bytes = valid.bytes,
            )

        val result = EnvelopeValidator.validate(tampered, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertRejected(result, EnvelopeRejectionReason.INVALID_SIGNATURE)
    }

    private fun assertRejected(
        result: EnvelopeValidationResult,
        reason: EnvelopeRejectionReason,
    ) {
        val rejected = assertIs<EnvelopeValidationResult.Rejected>(result)
        assertEquals(reason, rejected.reason)
    }

    private fun signedEnvelope(
        schemaVersion: Int = EnvelopeValidator.CURRENT_SCHEMA_VERSION,
        documentId: String = "doc-1",
        revision: Long = 1L,
        matchId: String = "match-1",
        minHostVersion: Int = EnvelopeValidator.CURRENT_HOST_VERSION,
        createdAtEpochSeconds: Long = 1_000L,
        expiresAtEpochSeconds: Long = 2_000L,
        bytes: ByteArray = byteArrayOf(1, 2, 3, 4),
        contentSha256Override: String? = null,
        signingKey: PrivateKey = keyPair.private,
    ): RemoteUiEnvelope {
        val contentSha256 = contentSha256Override ?: sha256Hex(bytes)
        val signingInput =
            RemoteUiEnvelope.canonicalSigningInput(
                schemaVersion,
                documentId,
                revision,
                matchId,
                minHostVersion,
                createdAtEpochSeconds,
                expiresAtEpochSeconds,
                contentSha256,
                EnvelopePublicKey.ALGORITHM,
            )
        val signature =
            Signature.getInstance(EnvelopePublicKey.ALGORITHM).run {
                initSign(signingKey)
                update(signingInput)
                Base64.getEncoder().encodeToString(sign())
            }
        return RemoteUiEnvelope(
            schemaVersion = schemaVersion,
            documentId = documentId,
            revision = revision,
            matchId = matchId,
            minHostVersion = minHostVersion,
            createdAtEpochSeconds = createdAtEpochSeconds,
            expiresAtEpochSeconds = expiresAtEpochSeconds,
            contentSha256 = contentSha256,
            signatureAlgorithm = EnvelopePublicKey.ALGORITHM,
            signature = signature,
            bytes = bytes,
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
