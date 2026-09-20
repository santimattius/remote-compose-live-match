package example.livematch.creator

import example.livematch.contract.LiveMatch
import example.livematch.contract.LiveMatchRequest
import example.livematch.contract.Theme
import example.livematch.contract.Viewport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProceduralJvmLiveMatchDocumentCreatorTest {
    private val request =
        LiveMatchRequest(
            match = LiveMatch("match-7", "Orcas", "Tigers", 2, 1, "2nd Half", "67:12"),
            viewport = Viewport(320, 180),
            localeTag = "en-US",
            theme = Theme.DARK,
            generationEpochMillis = 1_700_000_000_000,
        )

    @Test
    fun sameExplicitInputProducesTheSameValidatedDocument() {
        val creator = ProceduralJvmLiveMatchDocumentCreator()
        val first = creator.create(request)
        val second = creator.create(request)

        assertContentEquals(first.payload.bytes, second.payload.bytes)
        assertTrue(first.envelope.verifies(first.payload))
    }

    @Test
    fun scoreAndClockMutationsProduceDistinctDocuments() {
        val creator = ProceduralJvmLiveMatchDocumentCreator()
        val original = creator.create(request).payload.bytes
        val changedScore = creator.create(request.copy(match = request.match.copy(homeScore = 3))).payload.bytes
        val changedClock = creator.create(request.copy(match = request.match.copy(clock = "70:00"))).payload.bytes

        assertFalse(original.contentEquals(changedScore))
        assertFalse(original.contentEquals(changedClock))
    }

    @Test
    fun explicitBackgroundAndTextColorsAreEncodedInTheDocument() {
        val creator = ProceduralJvmLiveMatchDocumentCreator()
        val bytes = creator.create(request).payload.bytes

        assertTrue(bytes.containsColorLiteral(BACKGROUND_COLOR), "expected background color to be explicitly set")
        assertTrue(bytes.containsColorLiteral(TEXT_COLOR), "expected text color to be explicitly set")
    }

    @Test
    fun textIsAnchoredFlushLeftNotCentered() {
        val creator = ProceduralJvmLiveMatchDocumentCreator()
        val bytes = creator.create(request).payload.bytes

        assertTrue(bytes.containsFloatLiteral(LEFT_ANCHOR_PAN_X), "expected drawTextAnchored panX to be left-anchored (-1.0), not centered (0.0)")
    }

    @Test
    fun textUsesAnExplicitLargerSizeForTheWiderViewport() {
        val creator = ProceduralJvmLiveMatchDocumentCreator()
        val bytes = creator.create(request).payload.bytes

        assertTrue(bytes.containsFloatLiteral(TEXT_SIZE), "expected an explicit larger text size for the full-width viewport")
    }

    private fun ByteArray.containsColorLiteral(color: Int): Boolean {
        val bigEndian = byteArrayOf((color shr 24).toByte(), (color shr 16).toByte(), (color shr 8).toByte(), color.toByte())
        val littleEndian = bigEndian.reversedArray()
        return indexOf(bigEndian) >= 0 || indexOf(littleEndian) >= 0
    }

    private fun ByteArray.containsFloatLiteral(value: Float): Boolean {
        val bits = value.toRawBits()
        val bigEndian = byteArrayOf((bits shr 24).toByte(), (bits shr 16).toByte(), (bits shr 8).toByte(), bits.toByte())
        val littleEndian = bigEndian.reversedArray()
        return indexOf(bigEndian) >= 0 || indexOf(littleEndian) >= 0
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        outer@ for (start in 0..size - needle.size) {
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) continue@outer
            }
            return start
        }
        return -1
    }

    private companion object {
        const val BACKGROUND_COLOR = 0xFF002B5C.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val LEFT_ANCHOR_PAN_X = -1.0f
        const val TEXT_SIZE = 48.0f
    }
}
