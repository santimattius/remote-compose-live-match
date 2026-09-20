package example.livematch.creator

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchDocumentCreatorTest {
    @Test
    fun createsDeterministicNonEmptyRemoteComposeBytes() {
        val creator = MatchDocumentCreator()

        val first = creator.create("Live Match")
        val second = creator.create("Live Match")
        val differentTitle = creator.create("Final score")

        assertTrue(first.isNotEmpty())
        assertContentEquals(first, second)
        assertFalse(first.contentEquals(differentTitle))
    }
}
