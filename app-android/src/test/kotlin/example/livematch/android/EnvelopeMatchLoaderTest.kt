package example.livematch.android

import example.livematch.contract.EnvelopeRejectionReason
import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteUiEnvelope
import example.livematch.contract.ScheduledMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EnvelopeMatchLoaderTest {
    @Test
    fun `returns decoded match data only after envelope validation`() {
        val presentation = MatchPresentation.Scheduled(ScheduledMatch("match-1", "Orcas", "Tigers", 1_800_000_000_000))
        val envelope = envelope("match-1", MatchPresentationCodec.encode(presentation).bytes)
        var validationCalls = 0
        val loader =
            EnvelopeMatchLoader(
                decodeEnvelope = { envelope },
                validateEnvelope = { _, _ ->
                    validationCalls += 1
                    EnvelopeValidationResult.Valid
                },
            )

        val result = loader.load(byteArrayOf(9), nowEpochSeconds = 1_500)

        assertEquals(1, validationCalls)
        assertEquals(presentation, assertIs<MatchEnvelopeLoadResult.Loaded>(result).presentation)
    }

    @Test
    fun `rejects invalid envelopes without decoding payload data`() {
        var payloadDecodeCalls = 0
        val loader =
            EnvelopeMatchLoader(
                decodeEnvelope = { envelope("match-1", byteArrayOf(1)) },
                validateEnvelope = { _, _ -> EnvelopeValidationResult.Rejected(EnvelopeRejectionReason.INVALID_SIGNATURE) },
                decodePresentation = {
                    payloadDecodeCalls += 1
                    error("must not decode")
                },
            )

        val result = loader.load(byteArrayOf(9), nowEpochSeconds = 1_500)

        assertEquals(0, payloadDecodeCalls)
        assertEquals(
            MatchEnvelopeLoadResult.Rejected(EnvelopeRejectionReason.INVALID_SIGNATURE),
            result,
        )
    }

    @Test
    fun `rejects malformed match payloads and envelope match id mismatches`() {
        val malformed =
            EnvelopeMatchLoader(
                decodeEnvelope = { envelope("match-1", byteArrayOf(1)) },
                validateEnvelope = { _, _ -> EnvelopeValidationResult.Valid },
            ).load(byteArrayOf(9), nowEpochSeconds = 1_500)
        val otherPresentation = MatchPresentation.Scheduled(ScheduledMatch("other", "Orcas", "Tigers", 1_800_000_000_000))
        val mismatch =
            EnvelopeMatchLoader(
                decodeEnvelope = { envelope("match-1", MatchPresentationCodec.encode(otherPresentation).bytes) },
                validateEnvelope = { _, _ -> EnvelopeValidationResult.Valid },
            ).load(byteArrayOf(9), nowEpochSeconds = 1_500)

        assertEquals(MatchEnvelopeLoadResult.Malformed, malformed)
        assertEquals(MatchEnvelopeLoadResult.MetadataMismatch, mismatch)
    }

    private fun envelope(
        matchId: String,
        bytes: ByteArray,
    ) = RemoteUiEnvelope(
        schemaVersion = 1,
        documentId = "doc-1",
        revision = 1,
        matchId = matchId,
        minHostVersion = 1,
        createdAtEpochSeconds = 1_000,
        expiresAtEpochSeconds = 2_000,
        contentSha256 = "hash",
        signatureAlgorithm = "Ed25519",
        signature = "signature",
        bytes = bytes,
    )
}
