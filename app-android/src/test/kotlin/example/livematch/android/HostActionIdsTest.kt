package example.livematch.android

import example.livematch.contract.ActionId
import kotlin.test.Test
import kotlin.test.assertEquals

class HostActionIdsTest {
    @Test
    fun `host action ids are distinct and independent of enum order`() {
        val ids = ActionId.entries.associateWith(HostActionIds::of)

        assertEquals(ActionId.entries.size, ids.values.toSet().size)
        // These values are baked into captured documents, so they must never change.
        assertEquals(1, ids.getValue(ActionId.SET_REMINDER))
        assertEquals(2, ids.getValue(ActionId.OPEN_MATCH))
        assertEquals(3, ids.getValue(ActionId.OPEN_HIGHLIGHT))
        assertEquals(4, ids.getValue(ActionId.OPEN_SUMMARY))
        assertEquals(5, ids.getValue(ActionId.REFRESH))
    }
}
