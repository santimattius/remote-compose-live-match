package example.livematch.android

import example.livematch.contract.EnvelopeRejectionReason
import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.RemoteUiEnvelope
import example.livematch.contract.RemoteUiEnvelopeCodec

sealed class EnvelopeLoadResult {
    data object Loaded : EnvelopeLoadResult()

    data object Malformed : EnvelopeLoadResult()

    data class Rejected(
        val reason: EnvelopeRejectionReason,
    ) : EnvelopeLoadResult()
}

/**
 * Trust boundary for Remote Compose envelopes. It decodes and validates an envelope before
 * creating or exposing the untrusted inner document to a player-loading callback.
 */
class EnvelopeDocumentLoader(
    private val decode: (ByteArray) -> RemoteUiEnvelope = RemoteUiEnvelopeCodec::decode,
    private val validate: (RemoteUiEnvelope, Long) -> EnvelopeValidationResult =
        AndroidEnvelopeVerifier::validate,
) {
    fun load(
        encodedEnvelope: ByteArray,
        nowEpochSeconds: Long,
        onDocumentLoad: (RemoteDocumentPayload) -> Unit,
    ): EnvelopeLoadResult {
        val envelope =
            try {
                decode(encodedEnvelope)
            } catch (_: IllegalArgumentException) {
                return EnvelopeLoadResult.Malformed
            }
        return when (val validation = validate(envelope, nowEpochSeconds)) {
            EnvelopeValidationResult.Valid -> {
                onDocumentLoad(RemoteDocumentPayload.from(envelope.bytes, envelope.bytes.size))
                EnvelopeLoadResult.Loaded
            }

            is EnvelopeValidationResult.Rejected -> {
                EnvelopeLoadResult.Rejected(validation.reason)
            }
        }
    }
}
