package example.livematch.android

import example.livematch.contract.ActionAllowlist
import example.livematch.contract.ActionId
import example.livematch.contract.FinishedMatch
import example.livematch.contract.LiveMatch
import example.livematch.contract.MatchPhase
import example.livematch.contract.MatchPresentation
import example.livematch.contract.ScheduledMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchCardActionsTest {
    private val scheduled = MatchPresentation.Scheduled(ScheduledMatch("m1", "Orcas", "Tigers", 1))
    private val live = MatchPresentation.Live(LiveMatch("m1", "Orcas", "Tigers", 2, 1, "2nd Half", "67:12"))
    private val finished = MatchPresentation.Finished(FinishedMatch("m1", "Orcas", "Tigers", 2, 1, "FINAL"))

    @Test
    fun `each card offers exactly the actions it shows`() {
        assertEquals(listOf(ActionId.SET_REMINDER), MatchCardActions.of(scheduled))
        assertEquals(listOf(ActionId.OPEN_MATCH, ActionId.OPEN_HIGHLIGHT), MatchCardActions.of(live))
        assertEquals(listOf(ActionId.OPEN_SUMMARY), MatchCardActions.of(finished))
    }

    @Test
    fun `every offered action is allowed in its phase`() {
        listOf(scheduled, live, finished).forEach { presentation ->
            MatchCardActions.of(presentation).forEach { action ->
                assertTrue(ActionAllowlist.isAllowed(presentation.phase(), action), "$action in ${presentation.phase()}")
            }
        }
    }

    @Test
    fun `phases follow the presentation variant`() {
        assertEquals(MatchPhase.SCHEDULED, scheduled.phase())
        assertEquals(MatchPhase.LIVE, live.phase())
        assertEquals(MatchPhase.FINISHED, finished.phase())
    }

    @Test
    fun `labels are readable and distinct`() {
        val labels = ActionId.entries.map(MatchCardActions::label)

        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.toSet().size)
    }
}
