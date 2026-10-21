package example.livematch.contract

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteUiEnvelopeCodecTest {
    @Test
    fun `encodes and decodes a deterministic envelope round trip`() {
        val envelope = envelope()

        val first = RemoteUiEnvelopeCodec.encode(envelope)
        val second = RemoteUiEnvelopeCodec.encode(envelope)
        val decoded = RemoteUiEnvelopeCodec.decode(first)

        assertContentEquals(first, second)
        assertEnvelopeEquals(envelope, decoded)
    }

    @Test
    fun `rejects unknown magic and codec version`() {
        val encoded = RemoteUiEnvelopeCodec.encode(envelope())
        val unknownMagic = encoded.copyOf().also { it[0] = 'X'.code.toByte() }
        val unknownVersion = encoded.copyOf().also { it[4] = 2 }

        assertFailsWith<IllegalArgumentException> { RemoteUiEnvelopeCodec.decode(unknownMagic) }
        assertFailsWith<IllegalArgumentException> { RemoteUiEnvelopeCodec.decode(unknownVersion) }
    }

    @Test
    fun `rejects truncated input`() {
        val encoded = RemoteUiEnvelopeCodec.encode(envelope())

        assertFailsWith<IllegalArgumentException> { RemoteUiEnvelopeCodec.decode(encoded.copyOf(encoded.size - 1)) }
    }

    @Test
    fun `rejects negative and oversized lengths before allocation`() {
        assertFailsWith<IllegalArgumentException> { RemoteUiEnvelopeCodec.decode(headerWithDocumentIdLength(-1)) }
        assertFailsWith<IllegalArgumentException> {
            RemoteUiEnvelopeCodec.decode(headerWithDocumentIdLength(RemoteUiEnvelopeCodec.MAX_STRING_FIELD_BYTES + 1))
        }
    }

    @Test
    fun `rejects invalid UTF-8 in text fields`() {
        val encoded =
            ByteArrayOutputStream().use { output ->
                DataOutputStream(output).use {
                    it.write(RemoteUiEnvelopeCodec.MAGIC)
                    it.writeInt(RemoteUiEnvelopeCodec.VERSION)
                    it.writeInt(1)
                    it.writeInt(1)
                    it.writeByte(0x80)
                }
                output.toByteArray()
            }

        assertFailsWith<IllegalArgumentException> { RemoteUiEnvelopeCodec.decode(encoded) }
    }

    @Test
    fun `rejects encoded input beyond the total size bound`() {
        val oversized = ByteArray(RemoteUiEnvelopeCodec.MAX_ENCODED_BYTES + 1)

        assertFailsWith<IllegalArgumentException> { RemoteUiEnvelopeCodec.decode(oversized) }
    }

    @Test
    fun `rejects trailing bytes`() {
        val encoded = RemoteUiEnvelopeCodec.encode(envelope())

        assertFailsWith<IllegalArgumentException> { RemoteUiEnvelopeCodec.decode(encoded + byteArrayOf(0)) }
    }

    private fun headerWithDocumentIdLength(length: Int): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use {
                it.write(RemoteUiEnvelopeCodec.MAGIC)
                it.writeInt(RemoteUiEnvelopeCodec.VERSION)
                it.writeInt(1)
                it.writeInt(length)
            }
            output.toByteArray()
        }

    private fun assertEnvelopeEquals(
        expected: RemoteUiEnvelope,
        actual: RemoteUiEnvelope,
    ) {
        assertEquals(expected.schemaVersion, actual.schemaVersion)
        assertEquals(expected.documentId, actual.documentId)
        assertEquals(expected.revision, actual.revision)
        assertEquals(expected.matchId, actual.matchId)
        assertEquals(expected.minHostVersion, actual.minHostVersion)
        assertEquals(expected.createdAtEpochSeconds, actual.createdAtEpochSeconds)
        assertEquals(expected.expiresAtEpochSeconds, actual.expiresAtEpochSeconds)
        assertEquals(expected.contentSha256, actual.contentSha256)
        assertEquals(expected.signatureAlgorithm, actual.signatureAlgorithm)
        assertEquals(expected.signature, actual.signature)
        assertContentEquals(expected.bytes, actual.bytes)
    }

    private fun envelope() =
        RemoteUiEnvelope(
            schemaVersion = 1,
            documentId = "fixture|one",
            revision = 7L,
            matchId = "match-1",
            minHostVersion = 1,
            createdAtEpochSeconds = 1_700_000_000L,
            expiresAtEpochSeconds = 4_102_444_800L,
            contentSha256 = "a".repeat(64),
            signatureAlgorithm = "Ed25519",
            signature = "signature-base64",
            bytes = byteArrayOf(1, 2, 3, 4),
        )
}
