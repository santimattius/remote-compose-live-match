package example.livematch.contract

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ScheduledMatchDocumentTest {
    @Test
    fun payloadDefensivelyCopiesCallerAndReaderBytes() {
        val source = byteArrayOf(1, 2, 3)
        val payload = RemoteDocumentPayload.from(source, source.size)
        source[0] = 9
        val read = payload.bytes
        read[1] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), payload.bytes)
    }

    @Test
    fun envelopeHashesOnlyCanonicalPayloadBytes() {
        val first = RemoteDocumentPayload.from(byteArrayOf(1, 2), 2)
        val second = RemoteDocumentPayload.from(byteArrayOf(1, 3), 2)

        assertFalse(PayloadEnvelope.forPayload(first).sha256 == PayloadEnvelope.forPayload(second).sha256)
        assertFalse(PayloadEnvelope.forPayload(first).verifies(second))
    }

    @Test
    fun signingRemainsAnExternalBoundary() {
        val payload = RemoteDocumentPayload.from(byteArrayOf(1), 1)
        val envelope = PayloadEnvelope.forPayload(payload, SigningBoundary.EXTERNAL_SIGNATURE_REQUIRED)

        assertEquals(SigningBoundary.EXTERNAL_SIGNATURE_REQUIRED, envelope.signingBoundary)
        assertFalse(envelope.hasSignature)
    }
}
