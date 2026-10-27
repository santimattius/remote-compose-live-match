package example.livematch.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import example.livematch.contract.EnvelopeRejectionReason
import example.livematch.contract.RemoteUiEnvelope
import example.livematch.contract.RemoteUiEnvelopeCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class EnvelopeDocumentLoaderInstrumentationTest {
    @Test
    fun validPackagedEnvelopeReachesTheHostLoadCallback() {
        val encoded = scheduledFixture()
        val envelope = RemoteUiEnvelopeCodec.decode(encoded)
        assertEquals(EXPECTED_CANONICAL_SIGNING_INPUT_SHA256, sha256(envelope.canonicalSigningInput()))
        var loadCalls = 0

        val result =
            envelopeDocumentLoader().load(encoded, FIXTURE_VALIDATION_EPOCH_SECONDS) {
                loadCalls += 1
            }

        assertEquals(EnvelopeLoadResult.Loaded, result)
        assertEquals(1, loadCalls)
    }

    @Test
    fun tamperedPackagedEnvelopeIsRejectedBeforeTheHostLoadCallback() {
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
            envelopeDocumentLoader().load(
                RemoteUiEnvelopeCodec.encode(tamperedEnvelope),
                FIXTURE_VALIDATION_EPOCH_SECONDS,
            ) { loadCalls += 1 }

        assertEquals(EnvelopeLoadResult.Rejected(EnvelopeRejectionReason.HASH_MISMATCH), result)
        assertEquals(0, loadCalls)
    }

    @Test
    fun modifiedSignatureIsRejectedBeforeTheHostLoadCallback() {
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
            envelopeDocumentLoader().load(
                RemoteUiEnvelopeCodec.encode(tamperedEnvelope),
                FIXTURE_VALIDATION_EPOCH_SECONDS,
            ) { loadCalls += 1 }

        assertEquals(EnvelopeLoadResult.Rejected(EnvelopeRejectionReason.INVALID_SIGNATURE), result)
        assertEquals(0, loadCalls)
    }

    private fun envelopeDocumentLoader(): EnvelopeDocumentLoader = EnvelopeDocumentLoader()

    private fun modifiedSignature(signature: String): String = signature.replaceRange(0, 1, if (signature.first() == 'A') "B" else "A")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun scheduledFixture(): ByteArray =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .assets
            .open("scheduled-match.rce")
            .use { it.readBytes() }

    private companion object {
        const val FIXTURE_VALIDATION_EPOCH_SECONDS = 1_800_000_000L
        const val EXPECTED_CANONICAL_SIGNING_INPUT_SHA256 = "8a3aadbfd322393d4dbe6a7940f53541f0b61a82db51deb0496839dfb2137a55"
    }
}
