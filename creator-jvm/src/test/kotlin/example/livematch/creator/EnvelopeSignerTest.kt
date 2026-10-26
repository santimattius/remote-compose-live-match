package example.livematch.creator

import example.livematch.contract.EnvelopePublicKey
import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.RemoteDocumentPayload
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EnvelopeSignerTest {
    private val keyPair = KeyPairGenerator.getInstance(EnvelopePublicKey.ALGORITHM).generateKeyPair()
    private val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
    private val payload = RemoteDocumentPayload.from(byteArrayOf(1, 2, 3, 4), 4)
    private val metadata =
        EnvelopeMetadataInput(
            documentId = "doc-1",
            revision = 7L,
            matchId = "match-1",
            minHostVersion = 1,
            createdAtEpochSeconds = 1_000L,
            expiresAtEpochSeconds = 2_000L,
        )

    @Test
    fun `signs a payload so the validator accepts it`() {
        val envelope = EnvelopeSigner.sign(payload, metadata, keyPair.private)

        val result = EnvelopeValidator.validate(envelope, nowEpochSeconds = 1_500L, publicKeyBase64 = publicKeyBase64)

        assertEquals(EnvelopeValidationResult.Valid, result)
    }

    @Test
    fun `signing the same payload and metadata twice is byte-for-byte deterministic`() {
        val first = EnvelopeSigner.sign(payload, metadata, keyPair.private)
        val second = EnvelopeSigner.sign(payload, metadata, keyPair.private)

        assertEquals(first.signature, second.signature)
        assertEquals(first.contentSha256, second.contentSha256)
    }

    @Test
    fun `carries the exact payload bytes through`() {
        val envelope = EnvelopeSigner.sign(payload, metadata, keyPair.private)

        assertContentEquals(payload.bytes, envelope.bytes)
    }

    @Test
    fun `EnvelopeSigningKeyLoader decodes a PKCS8 base64 private key`() {
        val base64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)

        val decoded = EnvelopeSigningKeyLoader.decode(base64)

        assertEquals(keyPair.private.algorithm, decoded.algorithm)
        // Re-derive via KeyFactory to prove it is the exact same key material, not just the same algorithm.
        val expected =
            KeyFactory.getInstance(EnvelopePublicKey.ALGORITHM)
                .generatePrivate(PKCS8EncodedKeySpec(keyPair.private.encoded))
        assertContentEquals(expected.encoded, decoded.encoded)
    }

    @Test
    fun `EnvelopeSigningKeyLoader fails clearly when the env var is missing`() {
        val error =
            runCatching { EnvelopeSigningKeyLoader.loadFromEnv("LIVE_MATCH_SIGNING_PRIVATE_KEY_TEST_MISSING") }
                .exceptionOrNull()
        requireNotNull(error) { "Expected loadFromEnv to fail for a missing env var." }
        assertEquals(true, error.message?.contains("keys/README.md"))
    }
}
