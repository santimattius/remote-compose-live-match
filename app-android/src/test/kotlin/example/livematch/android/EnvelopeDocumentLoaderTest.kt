package example.livematch.android

import example.livematch.contract.EnvelopeRejectionReason
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.RemoteUiEnvelope
import example.livematch.contract.RemoteUiEnvelopeCodec
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EnvelopeDocumentLoaderTest {
    private val loader = EnvelopeDocumentLoader()

    @Test
    fun `accepts a valid signed envelope and exposes its document only after validation`() {
        val encoded = scheduledFixture()
        val envelope = RemoteUiEnvelopeCodec.decode(encoded)
        val expectedDocument = envelope.bytes
        assertEquals(EXPECTED_CANONICAL_SIGNING_INPUT_SHA256, sha256(envelope.canonicalSigningInput()))
        var loaded: RemoteDocumentPayload? = null

        val result =
            loader.load(encoded, FIXTURE_VALIDATION_EPOCH_SECONDS) {
                loaded = it
            }

        assertEquals(EnvelopeLoadResult.Loaded, result)
        assertContentEquals(expectedDocument, requireNotNull(loaded).bytes)
    }

    @Test
    fun `rejects a tampered envelope without invoking the document load callback`() {
        val validEnvelope = RemoteUiEnvelopeCodec.decode(scheduledFixture())
        val tamperedDocument = validEnvelope.bytes.apply { this[0] = (this[0].toInt() xor 1).toByte() }
        val tamperedEnvelope =
            RemoteUiEnvelope(
                schemaVersion = validEnvelope.schemaVersion,
                documentId = validEnvelope.documentId,
                revision = validEnvelope.revision,
                matchId = validEnvelope.matchId,
                minHostVersion = validEnvelope.minHostVersion,
                createdAtEpochSeconds = validEnvelope.createdAtEpochSeconds,
                expiresAtEpochSeconds = validEnvelope.expiresAtEpochSeconds,
                contentSha256 = validEnvelope.contentSha256,
                signatureAlgorithm = validEnvelope.signatureAlgorithm,
                signature = validEnvelope.signature,
                bytes = tamperedDocument,
            )
        var loadCalls = 0

        val result =
            loader.load(
                RemoteUiEnvelopeCodec.encode(tamperedEnvelope),
                FIXTURE_VALIDATION_EPOCH_SECONDS,
            ) { loadCalls += 1 }

        assertEquals(EnvelopeLoadResult.Rejected(EnvelopeRejectionReason.HASH_MISMATCH), result)
        assertEquals(0, loadCalls)
    }

    @Test
    fun `rejects a valid envelope with a modified signature without invoking the document load callback`() {
        val validEnvelope = RemoteUiEnvelopeCodec.decode(scheduledFixture())
        val tamperedEnvelope =
            RemoteUiEnvelope(
                schemaVersion = validEnvelope.schemaVersion,
                documentId = validEnvelope.documentId,
                revision = validEnvelope.revision,
                matchId = validEnvelope.matchId,
                minHostVersion = validEnvelope.minHostVersion,
                createdAtEpochSeconds = validEnvelope.createdAtEpochSeconds,
                expiresAtEpochSeconds = validEnvelope.expiresAtEpochSeconds,
                contentSha256 = validEnvelope.contentSha256,
                signatureAlgorithm = validEnvelope.signatureAlgorithm,
                signature = modifiedSignature(validEnvelope.signature),
                bytes = validEnvelope.bytes,
            )
        var loadCalls = 0

        val result =
            loader.load(
                RemoteUiEnvelopeCodec.encode(tamperedEnvelope),
                FIXTURE_VALIDATION_EPOCH_SECONDS,
            ) { loadCalls += 1 }

        assertEquals(EnvelopeLoadResult.Rejected(EnvelopeRejectionReason.INVALID_SIGNATURE), result)
        assertEquals(0, loadCalls)
    }

    @Test
    fun `rejects a malformed envelope without invoking the document load callback`() {
        var loadCalls = 0

        val result =
            loader.load(byteArrayOf(1, 2, 3), FIXTURE_VALIDATION_EPOCH_SECONDS) {
                loadCalls += 1
            }

        assertEquals(EnvelopeLoadResult.Malformed, result)
        assertEquals(0, loadCalls)
    }

    private fun modifiedSignature(signature: String): String = signature.replaceRange(0, 1, if (signature.first() == 'A') "B" else "A")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun scheduledFixture(): ByteArray {
        val repositoryRoot =
            generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
                .first { it.resolve("fixtures/scheduled-match.rce").isFile }
        return repositoryRoot.resolve("fixtures/scheduled-match.rce").readBytes()
    }

    private companion object {
        const val FIXTURE_VALIDATION_EPOCH_SECONDS = 1_800_000_000L
        const val EXPECTED_CANONICAL_SIGNING_INPUT_SHA256 = "8e173d3d345c05fae64f3bb41056bc39dccd870fbf23b58f08cd13fd3c1a2fd5"
    }
}
