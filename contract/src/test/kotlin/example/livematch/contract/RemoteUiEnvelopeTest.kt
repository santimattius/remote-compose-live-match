package example.livematch.contract

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RemoteUiEnvelopeTest {
    @Test
    fun `defensively copies bytes on construction and read`() {
        val source = byteArrayOf(1, 2, 3)
        val envelope = envelope(bytes = source)
        source[0] = 99

        assertContentEquals(byteArrayOf(1, 2, 3), envelope.bytes)
    }

    @Test
    fun `rejects blank documentId`() {
        assertFailsWith<IllegalArgumentException> { envelope(documentId = "") }
    }

    @Test
    fun `rejects blank matchId`() {
        assertFailsWith<IllegalArgumentException> { envelope(matchId = "") }
    }

    @Test
    fun `canonicalSigningInput is deterministic for identical fields`() {
        val a = envelope()
        val b = envelope()

        assertContentEquals(a.canonicalSigningInput(), b.canonicalSigningInput())
    }

    @Test
    fun `canonicalSigningInput changes when any signed field changes`() {
        val base = envelope().canonicalSigningInput()

        assertFalse(base.contentEquals(envelope(revision = 2).canonicalSigningInput()))
        assertFalse(base.contentEquals(envelope(contentSha256 = "deadbeef").canonicalSigningInput()))
        assertFalse(base.contentEquals(envelope(matchId = "other-match").canonicalSigningInput()))
    }

    @Test
    fun `canonicalSigningInput distinguishes field-boundary collision metadata`() {
        val first = envelope(documentId = "doc|7", revision = 8, matchId = "match")
        val second = envelope(documentId = "doc", revision = 7, matchId = "8|match")

        assertFalse(first.canonicalSigningInput().contentEquals(second.canonicalSigningInput()))
    }

    private fun envelope(
        schemaVersion: Int = 1,
        documentId: String = "doc-1",
        revision: Long = 1L,
        matchId: String = "match-1",
        minHostVersion: Int = 1,
        createdAtEpochSeconds: Long = 1_000L,
        expiresAtEpochSeconds: Long = 2_000L,
        contentSha256: String = "abc123",
        signatureAlgorithm: String = "Ed25519",
        signature: String = "c2ln",
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ) = RemoteUiEnvelope(
        schemaVersion = schemaVersion,
        documentId = documentId,
        revision = revision,
        matchId = matchId,
        minHostVersion = minHostVersion,
        createdAtEpochSeconds = createdAtEpochSeconds,
        expiresAtEpochSeconds = expiresAtEpochSeconds,
        contentSha256 = contentSha256,
        signatureAlgorithm = signatureAlgorithm,
        signature = signature,
        bytes = bytes,
    )
}
