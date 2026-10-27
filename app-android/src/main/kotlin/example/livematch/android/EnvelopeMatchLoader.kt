package example.livematch.android

import example.livematch.contract.EnvelopeRejectionReason
import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteUiEnvelope
import example.livematch.contract.RemoteUiEnvelopeCodec

sealed class MatchEnvelopeLoadResult {
    data class Loaded(
        val presentation: MatchPresentation,
    ) : MatchEnvelopeLoadResult()

    data object Malformed : MatchEnvelopeLoadResult()

    /** The signed envelope metadata disagrees with the match data it carries. */
    data object MetadataMismatch : MatchEnvelopeLoadResult()

    data class Rejected(
        val reason: EnvelopeRejectionReason,
    ) : MatchEnvelopeLoadResult()
}

/**
 * Trust boundary for signed match data. The envelope is decoded and validated before its payload
 * is decoded, so untrusted bytes never reach the match codec.
 */
class EnvelopeMatchLoader(
    private val decodeEnvelope: (ByteArray) -> RemoteUiEnvelope = RemoteUiEnvelopeCodec::decode,
    private val validateEnvelope: (RemoteUiEnvelope, Long) -> EnvelopeValidationResult =
        AndroidEnvelopeVerifier::validate,
    private val decodePresentation: (ByteArray) -> MatchPresentation = { MatchPresentationCodec.decode(it) },
) {
    fun load(
        encodedEnvelope: ByteArray,
        nowEpochSeconds: Long,
    ): MatchEnvelopeLoadResult {
        val envelope =
            try {
                decodeEnvelope(encodedEnvelope)
            } catch (_: IllegalArgumentException) {
                return MatchEnvelopeLoadResult.Malformed
            }
        val validation = validateEnvelope(envelope, nowEpochSeconds)
        if (validation is EnvelopeValidationResult.Rejected) {
            return MatchEnvelopeLoadResult.Rejected(validation.reason)
        }
        val presentation =
            try {
                decodePresentation(envelope.bytes)
            } catch (_: IllegalArgumentException) {
                return MatchEnvelopeLoadResult.Malformed
            }
        return if (presentation.matchId == envelope.matchId) {
            MatchEnvelopeLoadResult.Loaded(presentation)
        } else {
            MatchEnvelopeLoadResult.MetadataMismatch
        }
    }
}
