package example.livematch.android

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SnapshotCodecTest {
    private val match = validated(payloadOf(1, 2, 3, 4), matchId = "match-7", revision = 9, createdAt = 111, expiresAt = 222)

    @Test
    fun `round trips the metadata and the payload`() {
        val decoded = assertNotNull(SnapshotCodec.decode(SnapshotCodec.encode(match)))

        assertEquals(match.metadata, decoded.metadata)
        assertContentEquals(match.document.bytes, decoded.document.bytes)
    }

    @Test
    fun `ignores bytes that have no snapshot header`() {
        assertNull(SnapshotCodec.decode(match.document.bytes))
        assertNull(SnapshotCodec.decode(byteArrayOf()))
    }

    @Test
    fun `rejects truncated, extended and inconsistent snapshots`() {
        val encoded = SnapshotCodec.encode(match)

        assertNull(SnapshotCodec.decode(encoded.copyOf(encoded.size - 1)))
        assertNull(SnapshotCodec.decode(encoded + byteArrayOf(0)))
        // The payload length is the last int before the payload; claiming more bytes than exist must fail.
        val lying = encoded.copyOf().also { it[encoded.size - match.document.bytes.size - 1] = 0x7F }
        assertNull(SnapshotCodec.decode(lying))
    }
}
