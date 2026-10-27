package example.livematch.contract

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchPresentationCodecTest {
    @Test
    fun `encodes all match variants deterministically and decodes them exactly`() {
        val presentations =
            listOf(
                MatchPresentation.Scheduled(ScheduledMatch("scheduled-1", "Orcas", "Tigers", 1_800_000_000_000)),
                MatchPresentation.Live(LiveMatch("live-1", "Orcas", "Tigers", 2, 1, "2nd Half", "67:12")),
                MatchPresentation.Finished(FinishedMatch("finished-1", "Orcas", "Tigers", 2, 1, "FINAL")),
            )

        presentations.forEach { presentation ->
            val first = MatchPresentationCodec.encode(presentation).bytes
            val second = MatchPresentationCodec.encode(presentation).bytes

            assertContentEquals(first, second)
            assertEquals(presentation, MatchPresentationCodec.decode(first))
        }
    }

    @Test
    fun `rejects invalid magic and unsupported versions without changing caller bytes or codec state`() {
        assertRejected(byteArrayOf(0, 0, 0, 0), "Invalid match payload magic.")
        assertRejected(rawPayload(version = 2), "Unsupported match payload version: 2.")
    }

    @Test
    fun `rejects invalid declared lengths and trailing data without changing caller bytes or codec state`() {
        val valid = MatchPresentationCodec.encode(scheduled()).bytes
        val invalidLength = valid.copyOf().also { it[5] = (it[5] + 1).toByte() }
        val trailing = valid + byteArrayOf(0)
        val oversized = rawPayload(extraBody = ByteArray(MatchPresentationCodec.MAX_PAYLOAD_BYTES))

        assertRejected(invalidLength, "Invalid match payload length.")
        assertRejected(trailing, "Invalid match payload length.")
        assertRejected(oversized, "Invalid match payload length.")
    }

    @Test
    fun `rejects malformed UTF-8 and bounded field violations without changing caller bytes or codec state`() {
        assertRejected(rawPayload(homeTeamBytes = byteArrayOf(0xC3.toByte(), 0x28)), "Invalid UTF-8 match payload.")
        assertRejected(
            rawPayload(
                homeTeamBytes =
                    ByteArray(MatchPresentationCodec.MAX_STRING_BYTES + 1) {
                        'x'.code.toByte()
                    },
            ),
            "Invalid match payload string length.",
        )
        assertRejected(rawPayload(homeTeamBytes = byteArrayOf(' '.code.toByte())), "Match payload string must not be blank.")
        assertRejected(rawPayload(homeScore = MatchPresentationCodec.MAX_SCORE + 1), "Match payload score out of range.")
    }

    @Test
    fun `rejects trailing body data without changing caller bytes or codec state`() {
        assertRejected(rawPayload(extraBody = byteArrayOf(0)), "Trailing data in match payload.")
    }

    private fun assertRejected(
        bytes: ByteArray,
        expectedMessage: String,
    ) {
        val original = bytes.copyOf()
        val failure = assertFailsWith<IllegalArgumentException> { MatchPresentationCodec.decode(bytes) }

        assertEquals(expectedMessage, failure.message)
        assertContentEquals(original, bytes)
        assertEquals(scheduled(), MatchPresentationCodec.decode(MatchPresentationCodec.encode(scheduled()).bytes))
    }

    private fun scheduled() = MatchPresentation.Scheduled(ScheduledMatch("scheduled-1", "Orcas", "Tigers", 1_800_000_000_000))

    private fun rawPayload(
        version: Int = MatchPresentationCodec.VERSION,
        homeTeamBytes: ByteArray = "Orcas".toByteArray(),
        homeScore: Int = 2,
        extraBody: ByteArray = byteArrayOf(),
    ): ByteArray {
        val body =
            ByteArrayOutputStream()
                .also { bytes ->
                    DataOutputStream(bytes).use { output ->
                        output.writeByte(2) // Live
                        output.writeString("live-1".toByteArray())
                        output.writeString(homeTeamBytes)
                        output.writeString("Tigers".toByteArray())
                        output.writeInt(homeScore)
                        output.writeInt(1)
                        output.writeString("2nd Half".toByteArray())
                        output.writeString("67:12".toByteArray())
                        output.write(extraBody)
                    }
                }.toByteArray()
        return ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(MatchPresentationCodec.MAGIC)
                    output.writeByte(version)
                    output.writeInt(body.size)
                    output.write(body)
                }
            }.toByteArray()
    }

    private fun DataOutputStream.writeString(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }
}
