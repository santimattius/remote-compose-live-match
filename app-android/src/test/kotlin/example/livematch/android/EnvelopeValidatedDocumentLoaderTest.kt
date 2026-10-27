package example.livematch.android

import example.livematch.contract.EnvelopeRejectionReason
import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteUiEnvelope
import example.livematch.contract.ScheduledMatch
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EnvelopeValidatedDocumentLoaderTest {
    private val presentation = MatchPresentation.Scheduled(ScheduledMatch("match-1", "Orcas", "Tigers", 1_800_000_000_000))

    @Test
    fun `exposes the validated match payload bytes`() {
        val payload = MatchPresentationCodec.encode(presentation)
        val loader = loaderFor(payload.bytes, EnvelopeValidationResult.Valid)

        val document = loader.load(byteArrayOf(9), nowEpochSeconds = 1_500)

        assertContentEquals(payload.bytes, assertNotNull(document).bytes)
    }

    @Test
    fun `exposes nothing for rejected or mismatched envelopes`() {
        val rejected =
            loaderFor(
                MatchPresentationCodec.encode(presentation).bytes,
                EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.INVALID_SIGNATURE),
            ).load(byteArrayOf(9), nowEpochSeconds = 1_500)
        val malformed = loaderFor(byteArrayOf(1), EnvelopeValidationResult.Valid).load(byteArrayOf(9), nowEpochSeconds = 1_500)

        assertNull(rejected)
        assertNull(malformed)
    }

    private fun loaderFor(
        payloadBytes: ByteArray,
        validation: EnvelopeValidationResult,
    ) = EnvelopeValidatedDocumentLoader(
        EnvelopeMatchLoader(
            decodeEnvelope = {
                RemoteUiEnvelope(
                    schemaVersion = 1,
                    documentId = "doc-1",
                    revision = 1,
                    matchId = "match-1",
                    minHostVersion = 1,
                    createdAtEpochSeconds = 1_000,
                    expiresAtEpochSeconds = 2_000,
                    contentSha256 = "hash",
                    signatureAlgorithm = "Ed25519",
                    signature = "signature",
                    bytes = payloadBytes,
                )
            },
            validateEnvelope = { _, _ -> validation },
        ),
    )
}
