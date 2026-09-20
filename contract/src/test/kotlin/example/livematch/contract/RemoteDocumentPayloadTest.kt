package example.livematch.contract

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RemoteDocumentPayloadTest {
    @Test
    fun rejectsTransportTruncationBeforeTheHostCanDecode() {
        assertFailsWith<IllegalArgumentException> {
            RemoteDocumentPayload.from(bytes = byteArrayOf(1, 2), declaredSize = 3)
        }
    }
}
